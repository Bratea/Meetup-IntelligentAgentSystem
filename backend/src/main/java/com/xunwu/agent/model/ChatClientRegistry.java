package com.xunwu.agent.model;

import com.xunwu.agent.config.XunwuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
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
 * 统一管理 DeepSeek / 智谱 / Ollama 的调用入口。</p>
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
        embedding.setDefaultModel(true);
        modelInfos.put("bge-m3", embedding);
        log.info("已注册 Ollama 本地模型，baseUrl={}", ollamaBaseUrl);
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
            chat.setAvailable(names.contains("qwen2.5:7b"));
        }
        ModelInfo embedding = modelInfos.get("bge-m3");
        if (embedding != null) {
            embedding.setAvailable(names.contains("bge-m3"));
        }
        log.info("Ollama 已安装模型: {}", names);
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
     * 判断模型是否已注册
     */
    public boolean contains(String modelId) {
        return chatClients.containsKey(resolveModelId(modelId));
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
     * 解析模型标识：空值/未知值回退到默认模型
     */
    public String resolveModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return properties.getAgent().getDefaultModel();
        }
        // 兼容 "qwen2.5" -> "qwen2.5:7b" 等简写
        return chatClients.containsKey(modelId) ? modelId : properties.getAgent().getDefaultModel();
    }

    /**
     * 获取默认模型标识
     */
    public String defaultModelId() {
        return properties.getAgent().getDefaultModel();
    }

}
