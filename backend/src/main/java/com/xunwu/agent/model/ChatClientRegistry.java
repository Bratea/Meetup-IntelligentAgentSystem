package com.xunwu.agent.model;

import com.xunwu.agent.config.XunwuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private final Map<String, ChatClient> chatClients = new LinkedHashMap<>();
    private final Map<String, ModelInfo> modelInfos = new LinkedHashMap<>();

    private final ObjectProvider<OpenAiChatModel> openAiChatModelProvider;
    private final ObjectProvider<ZhiPuAiChatModel> zhiPuAiChatModelProvider;
    private final ObjectProvider<OllamaChatModel> ollamaChatModelProvider;
    private final XunwuProperties properties;

    @Value("${spring.ai.openai.api-key:}")
    private String deepSeekApiKey;

    @Value("${spring.ai.zhipuai.api-key:}")
    private String zhiPuApiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String deepSeekBaseUrl;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

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
        String modelId = properties.getAgent().getDefaultModel();
        // 优先复用 Spring AI 自动配置的 OpenAiChatModel
        ChatModel chatModel = openAiChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("未获取到 OpenAiChatModel，DeepSeek 模型不可用");
            modelInfos.put("deepseek-chat",
                    ModelInfo.of("deepseek-chat", "DeepSeek V3", "deepseek", "chat",
                            "DeepSeek 对话模型（OpenAI 兼容协议）"));
            modelInfos.get("deepseek-chat").setAvailable(false);
            return;
        }
        ChatClient client = ChatClient.builder(chatModel).build();
        chatClients.put("deepseek-chat", client);
        modelInfos.put("deepseek-chat",
                ModelInfo.of("deepseek-chat", "DeepSeek V3", "deepseek", "chat",
                        "DeepSeek 对话模型（OpenAI 兼容协议）"));
        log.info("已注册 DeepSeek 模型，baseUrl={}", deepSeekBaseUrl);
    }

    /**
     * 注册智谱 GLM
     */
    private void registerZhiPu() {
        ChatModel chatModel = zhiPuAiChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("未获取到 ZhiPuAiChatModel，智谱模型不可用");
            ModelInfo info = ModelInfo.of("glm-4-plus", "智谱 GLM-4-Plus", "zhipu", "chat",
                    "智谱 GLM 对话模型");
            info.setAvailable(false);
            modelInfos.put("glm-4-plus", info);
            return;
        }
        ChatClient client = ChatClient.builder(chatModel).build();
        chatClients.put("glm-4-plus", client);
        modelInfos.put("glm-4-plus",
                ModelInfo.of("glm-4-plus", "智谱 GLM-4-Plus", "zhipu", "chat",
                        "智谱 GLM 对话模型"));
        log.info("已注册智谱 GLM 模型");
    }

    /**
     * 注册 Ollama 本地模型
     */
    private void registerOllama() {
        ChatModel chatModel = ollamaChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("未获取到 OllamaChatModel，本地模型不可用");
            ModelInfo info = ModelInfo.of("qwen2.5:7b", "Qwen2.5 7B", "ollama", "chat",
                    "Ollama 本地对话模型");
            info.setAvailable(false);
            modelInfos.put("qwen2.5:7b", info);
            return;
        }
        ChatClient client = ChatClient.builder(chatModel).build();
        chatClients.put("qwen2.5:7b", client);
        ModelInfo info = ModelInfo.of("qwen2.5:7b", "Qwen2.5 7B", "ollama", "chat",
                "Ollama 本地对话模型");
        info.setDefaultModel(true);
        modelInfos.put("qwen2.5:7b", info);
        log.info("已注册 Ollama 本地模型，baseUrl={}", ollamaBaseUrl);
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
     * 获取模型标识对应的 ChatModel
     */
    public ChatModel getChatModel(String modelId) {
        return resolveChatModel(modelId);
    }

    /**
     * 判断模型是否已注册
     */
    public boolean contains(String modelId) {
        return chatClients.containsKey(resolveModelId(modelId));
    }

    /**
     * 所有可用模型标识
     */
    public List<String> availableModelIds() {
        return new ArrayList<>(chatClients.keySet());
    }

    /**
     * 模型元信息列表
     */
    public List<ModelInfo> listModels() {
        return new ArrayList<>(modelInfos.values());
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

    private ChatModel resolveChatModel(String modelId) {
        String id = resolveModelId(modelId);
        ChatModel chatModel = switch (id) {
            case "deepseek-chat" -> openAiChatModelProvider.getIfAvailable();
            case "glm-4-plus" -> zhiPuAiChatModelProvider.getIfAvailable();
            case "qwen2.5:7b" -> ollamaChatModelProvider.getIfAvailable();
            default -> null;
        };
        if (chatModel == null) {
            throw new IllegalArgumentException("模型不可用: " + id);
        }
        return chatModel;
    }

    /**
     * 获取默认模型标识
     */
    public String defaultModelId() {
        return properties.getAgent().getDefaultModel();
    }

    /**
     * 构建指定模型的 ChatOptions（用于工具调用等场景）
     */
    @SuppressWarnings("unchecked")
    public <T extends org.springframework.ai.chat.prompt.ChatOptions> Optional<T> buildOptions(
            String modelId, Map<String, Object> runtimeProps) {
        String id = resolveModelId(modelId);
        return switch (id) {
            case "deepseek-chat" -> {
                OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
                runtimeProps.forEach((k, v) -> applyOpenAiOption(builder, k, v));
                yield Optional.of((T) builder.build());
            }
            case "glm-4-plus" -> {
                ZhiPuAiChatOptions.Builder builder = ZhiPuAiChatOptions.builder();
                runtimeProps.forEach((k, v) -> applyZhiPuOption(builder, k, v));
                yield Optional.of((T) builder.build());
            }
            case "qwen2.5:7b" -> {
                OllamaChatOptions.Builder builder = OllamaChatOptions.builder();
                runtimeProps.forEach((k, v) -> applyOllamaOption(builder, k, v));
                yield Optional.of((T) builder.build());
            }
            default -> Optional.empty();
        };
    }

    private void applyOpenAiOption(OpenAiChatOptions.Builder builder, String key, Object value) {
        switch (key) {
            case "temperature" -> builder.temperature(((Number) value).doubleValue());
            case "maxTokens" -> builder.maxTokens((Integer) value);
            case "topP" -> builder.topP(((Number) value).doubleValue());
            default -> {
            }
        }
    }

    private void applyZhiPuOption(ZhiPuAiChatOptions.Builder builder, String key, Object value) {
        switch (key) {
            case "temperature" -> builder.temperature(((Number) value).doubleValue());
            case "maxTokens" -> builder.maxTokens((Integer) value);
            case "topP" -> builder.topP(((Number) value).doubleValue());
            default -> {
            }
        }
    }

    private void applyOllamaOption(OllamaChatOptions.Builder builder, String key, Object value) {
        switch (key) {
            case "temperature" -> builder.temperature(((Number) value).doubleValue());
            case "maxTokens" -> builder.numPredict((Integer) value);
            default -> {
            }
        }
    }
}
