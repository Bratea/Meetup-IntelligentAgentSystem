package com.xunwu.agent.model;

import com.xunwu.agent.config.XunwuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多模型管理组件
 *
 * <p>按模型标识动态获取对应的 {@link ChatClient}，将模型配置与 Agent 执行逻辑解耦。
 * 统一管理 DeepSeek / 智谱 / Ollama / LongCat 的调用入口。</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class ChatClientRegistry {

    /**
     * 未配置模型 API Key 时使用的占位值。
     *
     * <p>仅用于通过 Spring AI 对 api-key 的 {@code Assert.hasText} 非空断言，
     * 不代表任何真实凭据。{@link #hasText(String)} 会把它识别为"未配置"。</p>
     */
    public static final String PLACEHOLDER_API_KEY = "sk-not-configured";

    /** 探测 Ollama 的超时时间，避免模型服务不可达时拖垮启动 */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    /** 已安装模型列表的缓存时长，避免每次请求都打 Ollama */
    private static final long OLLAMA_PROBE_TTL_MS = 30_000L;

    /** /api/tags 返回的模型名，形如 {"name":"qwen2.5:7b",...} */
    private static final Pattern OLLAMA_MODEL_NAME =
            Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final Map<String, ChatClient> chatClients = new LinkedHashMap<>();
    private final Map<String, ModelInfo> modelInfos = new LinkedHashMap<>();

    private final ObjectProvider<OpenAiChatModel> openAiChatModelProvider;
    private final ObjectProvider<ZhiPuAiChatModel> zhiPuAiChatModelProvider;
    private final ObjectProvider<OllamaChatModel> ollamaChatModelProvider;
    private final XunwuProperties properties;

    /** 直接读环境变量，用于判断用户是否真的配置了 Key（区别于 yml 里的占位值） */
    @Value("${DEEPSEEK_API_KEY:}")
    private String deepSeekApiKey;

    @Value("${ZHIPU_API_KEY:}")
    private String zhiPuApiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String deepSeekBaseUrl;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    /** LongCat（OpenAI 兼容协议），配置见 application.yml 的 longcat 段 */
    @Value("${longcat.api-key:}")
    private String longcatApiKey;

    @Value("${longcat.base-url:https://api.longcat.chat/openai/v1}")
    private String longcatBaseUrl;

    @Value("${longcat.model:LongCat-2.5}")
    private String longcatModel;

    private final HttpClient ollamaProbeClient = HttpClient.newBuilder()
            .connectTimeout(PROBE_TIMEOUT)
            .build();

    /** 已安装的 Ollama 模型名缓存（含 TTL，见 {@link #refreshOllamaAvailability()}） */
    private volatile Set<String> installedOllamaModels = Set.of();
    private volatile long ollamaProbeAt = 0L;

    public ChatClientRegistry(ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
                              ObjectProvider<ZhiPuAiChatModel> zhiPuAiChatModelProvider,
                              ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
                              XunwuProperties properties) {
        this.openAiChatModelProvider = openAiChatModelProvider;
        this.zhiPuAiChatModelProvider = zhiPuAiChatModelProvider;
        this.ollamaChatModelProvider = ollamaChatModelProvider;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        registerDeepSeek();
        registerZhiPu();
        registerOllama();
        registerLongcat();
        log.info("ChatClientRegistry 初始化完成，已注册 {} 个模型: {}", chatClients.size(), chatClients.keySet());
    }

    /**
     * 注册 DeepSeek（OpenAI 兼容协议）
     */
    private void registerDeepSeek() {
        // 优先复用 Spring AI 自动配置的 OpenAiChatModel
        ChatModel chatModel = openAiChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("未获取到 OpenAiChatModel，DeepSeek 模型不可用");
            setUnavailable("deepseek-chat", "DeepSeek V3", "deepseek", "chat",
                    "DeepSeek 对话模型（OpenAI 兼容协议）");
            return;
        }
        ChatClient client = ChatClient.builder(chatModel).build();
        chatClients.put("deepseek-chat", client);
        ModelInfo info = ModelInfo.of("deepseek-chat", "DeepSeek V3", "deepseek", "chat",
                "DeepSeek 对话模型（OpenAI 兼容协议）");
        info.setAvailable(hasText(deepSeekApiKey));
        modelInfos.put("deepseek-chat", info);
        log.info("已注册 DeepSeek 模型，baseUrl={}，available={}",
                deepSeekBaseUrl, info.isAvailable());
    }

    /**
     * 注册智谱 GLM
     */
    private void registerZhiPu() {
        ChatModel chatModel = zhiPuAiChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("未获取到 ZhiPuAiChatModel，智谱模型不可用");
            setUnavailable("glm-4-plus", "智谱 GLM-4-Plus", "zhipu", "chat",
                    "智谱 GLM 对话模型");
            return;
        }
        ChatClient client = ChatClient.builder(chatModel).build();
        chatClients.put("glm-4-plus", client);
        ModelInfo info = ModelInfo.of("glm-4-plus", "智谱 GLM-4-Plus", "zhipu", "chat",
                "智谱 GLM 对话模型");
        info.setAvailable(hasText(zhiPuApiKey));
        modelInfos.put("glm-4-plus", info);
        log.info("已注册智谱 GLM 模型，available={}", info.isAvailable());
    }

    /**
     * 注册 Ollama 本地模型
     */
    private void registerOllama() {
        ChatModel chatModel = ollamaChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("未获取到 OllamaChatModel，本地模型不可用");
            setUnavailable("qwen2.5:7b", "Qwen2.5 7B", "ollama", "chat",
                    "Ollama 本地对话模型");
            return;
        }
        ChatClient client = ChatClient.builder(chatModel).build();
        chatClients.put("qwen2.5:7b", client);
        ModelInfo info = ModelInfo.of("qwen2.5:7b", "Qwen2.5 7B", "ollama", "chat",
                "Ollama 本地对话模型");
        info.setDefaultModel(true);
        modelInfos.put("qwen2.5:7b", info);

        // 向量化模型不是 ChatModel，单独登记，供模型页展示与知识库状态判断。
        // 注意：available 只是"乐观值"，真实结果由 listModels() 探测后刷新。
        ModelInfo embedding = ModelInfo.of("bge-m3", "BGE-M3 向量模型", "ollama", "embedding",
                "Ollama 本地向量化模型（1024 维）");
        modelInfos.put("bge-m3", embedding);
        log.info("已注册 Ollama 本地模型，baseUrl={}", ollamaBaseUrl);
    }

    /**
     * 注册 LongCat（OpenAI 兼容协议）。
     *
     * <p>LongCat 与 DeepSeek 同为 OpenAI 兼容协议，但 Spring AI 自动配置只创建一个
     * {@link OpenAiChatModel}（已被 DeepSeek 占用），因此这里手工构造一个独立实例。
     * 未配置 {@code LONGCAT_API_KEY} 时只登记元信息并标记不可用，不影响其他模型。</p>
     */
    private void registerLongcat() {
        if (!hasText(longcatApiKey)) {
            log.info("未配置 LONGCAT_API_KEY，LongCat 模型不可用");
            setUnavailable(longcatModel, "LongCat 2.5", "longcat", "chat",
                    "LongCat 对话模型（OpenAI 兼容协议）");
            return;
        }
        try {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(longcatBaseUrl)
                    .apiKey(longcatApiKey)
                    .completionsPath("/chat/completions")
                    .build();
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(longcatModel)
                            .temperature(0.7)
                            .build())
                    .build();
            chatClients.put(longcatModel, ChatClient.builder(chatModel).build());
            ModelInfo info = ModelInfo.of(longcatModel, "LongCat 2.5", "longcat", "chat",
                    "LongCat 对话模型（OpenAI 兼容协议）");
            info.setAvailable(true);
            modelInfos.put(longcatModel, info);
            log.info("已注册 LongCat 模型，baseUrl={}，model={}", longcatBaseUrl, longcatModel);
        } catch (Exception e) {
            log.warn("LongCat 模型注册失败: {}", e.getMessage());
            setUnavailable(longcatModel, "LongCat 2.5", "longcat", "chat",
                    "LongCat 对话模型（OpenAI 兼容协议）");
        }
    }

    /**
     * 探测 Ollama 实际已安装的模型，刷新本地模型的 available 标记。
     *
     * <p>带 30 秒缓存：Ollama 刚启动 / 刚拉完模型时不会立刻显示为可用，
     * 但也不会每次请求都打一次 Ollama。</p>
     */
    private void refreshOllamaAvailability() {
        long now = System.currentTimeMillis();
        if (now - ollamaProbeAt < OLLAMA_PROBE_TTL_MS) {
            return;
        }
        ollamaProbeAt = now;
        Set<String> names = probeInstalledModels();
        installedOllamaModels = names;
        ModelInfo chat = modelInfos.get("qwen2.5:7b");
        if (chat != null) {
            chat.setAvailable(matchesInstalled(names, "qwen2.5:7b"));
        }
        ModelInfo embedding = modelInfos.get("bge-m3");
        if (embedding != null) {
            embedding.setAvailable(matchesInstalled(names, "bge-m3"));
        }
        log.info("Ollama 已安装模型: {}", names);
    }

    /**
     * 判断已安装列表里是否包含指定模型。
     *
     * <p>Ollama 的 {@code /api/tags} 返回的 name 带 tag（如 {@code bge-m3:latest}），
     * 而注册名可能是裸名（{@code bge-m3}），也可能自带 tag（{@code qwen2.5:7b}），
     * 所以：完全相等直接命中；注册名不带 tag 时，任意 tag 都算命中。</p>
     */
    private static boolean matchesInstalled(Set<String> installed, String modelId) {
        if (installed.contains(modelId)) {
            return true;
        }
        if (modelId.indexOf(':') >= 0) {
            return false;
        }
        for (String name : installed) {
            int colon = name.lastIndexOf(':');
            if (colon > 0 && name.substring(0, colon).equals(modelId)) {
                return true;
            }
        }
        return false;
    }

    /** 调用 Ollama /api/tags 获取已安装模型名，任何异常都降级为"未安装" */
    private Set<String> probeInstalledModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    ollamaProbeClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) {
                log.warn("探测 Ollama 模型列表返回 HTTP {}", response.statusCode());
                return Set.of();
            }
            Set<String> names = new HashSet<>();
            Matcher matcher = OLLAMA_MODEL_NAME.matcher(response.body());
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            return names;
        } catch (Exception e) {
            log.warn("探测 Ollama 已安装模型失败: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * 根据模型标识获取 ChatClient
     *
     * @param modelId 模型标识，为空时使用默认模型
     * @return ChatClient
     */
    public ChatClient getChatClient(String modelId) {
        String id = resolveModelId(modelId);
        ChatClient client = chatClients.get(id);
        if (client == null) {
            throw new IllegalArgumentException("未注册的模型: " + id
                    + "，可用模型: " + chatClients.keySet());
        }
        return client;
    }

    /**
     * 判断模型是否已注册（纯查询：未知模型返回 false，不抛异常）
     */
    public boolean contains(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        return chatClients.containsKey(modelId);
    }

    /**
     * 模型元信息列表。
     *
     * <p>每次调用前刷新一次 Ollama 已安装模型（带缓存），
     * 保证 available 反映"模型是否真的拉下来了"，而不只是"服务是否配置了"。</p>
     */
    public List<ModelInfo> listModels() {
        refreshOllamaAvailability();
        return new ArrayList<>(modelInfos.values());
    }

    private void setUnavailable(String id, String name, String provider,
                                String type, String description) {
        ModelInfo info = ModelInfo.of(id, name, provider, type, description);
        info.setAvailable(false);
        modelInfos.put(id, info);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !PLACEHOLDER_API_KEY.equals(value);
    }

    /**
     * 解析模型标识。
     *
     * <p>空值回退到 {@code xunwu.agent.default-model}；显式传入但未注册的模型直接抛异常 ——
     * 静默替换会让调用方以为在用 A 模型，实际却走了默认模型，出问题时极难排查。</p>
     */
    public String resolveModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return properties.getAgent().getDefaultModel();
        }
        if (chatClients.containsKey(modelId)) {
            return modelId;
        }
        throw new IllegalArgumentException(
                "未注册的模型: " + modelId + "，可用模型: " + chatClients.keySet());
    }

    /**
     * 获取默认模型标识
     */
    public String defaultModelId() {
        return properties.getAgent().getDefaultModel();
    }

}
