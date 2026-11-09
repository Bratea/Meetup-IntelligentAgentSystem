package com.xunwu.agent.chat;

import com.xunwu.agent.agent.AgentContext;
import com.xunwu.agent.agent.AgentEvent;
import com.xunwu.agent.agent.AgentExecutor;
import com.xunwu.agent.agent.AgentRequest;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.Agent;
import com.xunwu.agent.entity.Conversation;
import com.xunwu.agent.mapper.AgentMapper;
import com.xunwu.agent.mapper.ConversationMapper;
import com.xunwu.agent.mapper.MessageMapper;
import com.xunwu.agent.model.ChatClientRegistry;
import com.xunwu.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话服务：组装 AgentContext、调用执行引擎、SSE 流式输出
 *
 * @author xunwu
 */
@Slf4j
@Service
public class ChatService {

    /** 单次请求携带的最大历史消息条数（超出则丢弃最早的，避免上下文溢出） */
    private static final int MAX_HISTORY_MESSAGES = 20;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentMapper agentMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ChatClientRegistry chatClientRegistry;
    private final ToolRegistry toolRegistry;
    private final AgentExecutor agentExecutor;
    private final XunwuProperties properties;

    public ChatService(AgentMapper agentMapper,
                       ConversationMapper conversationMapper,
                       MessageMapper messageMapper,
                       ChatClientRegistry chatClientRegistry,
                       ToolRegistry toolRegistry,
                       AgentExecutor agentExecutor,
                       XunwuProperties properties) {
        this.agentMapper = agentMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.chatClientRegistry = chatClientRegistry;
        this.toolRegistry = toolRegistry;
        this.agentExecutor = agentExecutor;
        this.properties = properties;
    }

    /**
     * 流式对话
     */
    public Flux<AgentEvent> stream(AgentRequest request) {
        try {
            return doStream(request);
        } catch (BizException e) {
            // SSE 端点的 produces 固定为 text/event-stream，
            // 此时再抛异常会因"无 JSON 转换器"导致响应写不出去。
            // 因此初始化阶段的错误也一律作为 SSE 事件下发。
            log.warn("对话初始化被拒绝: {}", e.getMessage());
            return Flux.just(AgentEvent.error(e.getMessage()));
        } catch (Exception e) {
            log.error("对话初始化失败", e);
            return Flux.just(AgentEvent.error("对话初始化失败，请稍后重试"));
        }
    }

    /**
     * 重新生成：以会话里最后一条用户消息为输入，清掉它之后的旧助手回复，再跑一次。
     *
     * <p>与「新对话」复用同一套执行与落库逻辑，区别只是不重复保存用户消息
     * （该消息已存在于库中，且刚被用作重新生成的输入）。</p>
     */
    public Flux<AgentEvent> regenerate(Long conversationId, String modelId, boolean enableRag) {
        try {
            return doRegenerate(conversationId, modelId, enableRag);
        } catch (BizException e) {
            log.warn("重新生成被拒绝: {}", e.getMessage());
            return Flux.just(AgentEvent.error(e.getMessage()));
        } catch (Exception e) {
            log.error("重新生成失败: conversationId={}", conversationId, e);
            return Flux.just(AgentEvent.error("重新生成失败，请稍后重试"));
        }
    }

    private Flux<AgentEvent> doStream(AgentRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw BizException.badRequest("消息内容不能为空");
        }

        // 1. 加载智能体
        Agent agent = loadAgent(request.getAgentId());

        // 2. 会话处理
        Long conversationId = request.getConversationId();
        if (conversationId == null) {
            Conversation conversation = new Conversation();
            conversation.setAgentId(agent.getId());
            conversation.setTitle(truncate(request.getMessage(), 30));
            conversation.setStatus("ACTIVE");
            conversationMapper.insert(conversation);
            conversationId = conversation.getId();
        }

        // 3. 保存用户消息
        final Long finalConversationId = conversationId;
        saveMessage(finalConversationId, "user", request.getMessage(), null, null);

        // 4. 组装上下文并执行
        return executeAndPersist(agent, finalConversationId, request);
    }

    private Flux<AgentEvent> doRegenerate(Long conversationId, String modelId, boolean enableRag) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw BizException.notFound("会话不存在: " + conversationId);
        }

        // 找最后一条用户消息作为重新生成的输入
        List<com.xunwu.agent.entity.Message> history =
                messageMapper.selectByConversationId(conversationId);
        com.xunwu.agent.entity.Message lastUser = null;
        for (com.xunwu.agent.entity.Message m : history) {
            if ("user".equals(m.getRole())) {
                lastUser = m;
            }
        }
        if (lastUser == null) {
            throw BizException.badRequest("该会话没有可重新生成的用户消息");
        }

        // 清掉这条用户消息之后的全部消息（旧助手回复），避免历史里出现重复回复
        int removed = messageMapper.deleteAfterId(conversationId, lastUser.getId());
        log.info("重新生成: conversationId={}, 移除旧消息 {} 条", conversationId, removed);

        Agent agent = loadAgent(conversation.getAgentId());
        AgentRequest request = new AgentRequest();
        request.setAgentId(conversation.getAgentId());
        request.setConversationId(conversationId);
        request.setMessage(lastUser.getContent());
        request.setModelId(modelId);
        request.setEnableRag(enableRag);
        return executeAndPersist(agent, conversationId, request);
    }

    /**
     * 组装上下文、执行引擎并把助手回复落库。
     * 普通对话与重新生成共用。
     */
    private Flux<AgentEvent> executeAndPersist(Agent agent, Long conversationId, AgentRequest request) {
        AgentContext context = buildContext(request, agent);
        return agentExecutor.executeStream(context)
                .doOnNext(event -> {
                    if ("done".equals(event.getType())) {
                        Object data = event.getData();
                        String content = data == null ? "" : String.valueOf(data);
                        // 空内容不入库：避免历史里出现空气泡，污染后续上下文
                        if (content.isBlank()) {
                            log.warn("对话结束但内容为空，跳过落库: conversationId={}", conversationId);
                            return;
                        }
                        saveMessage(conversationId, "assistant",
                                content, context.getModelId(), null);
                    }
                })
                .doOnComplete(() -> log.info("对话完成: conversationId={}", conversationId))
                .doOnError(e -> log.error("对话异常: conversationId={}", conversationId, e))
                // 理论上 AgentExecutor 已把异常转成 error 事件，这里是最后一道兜底
                .onErrorResume(e -> Flux.just(AgentEvent.error(toUserMessage(e))));
    }

    /**
     * 组装执行上下文
     */
    private AgentContext buildContext(AgentRequest request, Agent agent) {
        String modelId = request.getModelId() != null ? request.getModelId() : agent.getModelId();
        ChatClient chatClient;
        try {
            chatClient = chatClientRegistry.getChatClient(modelId);
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest(e.getMessage());
        }

        List<String> configuredTools = parseToolIds(agent.getToolIds());
        // 可选工具 + 固定工具，统一用可变列表承载
        List<ToolCallback> tools = new ArrayList<>(toolRegistry.getTools(configuredTools));
        tools.addAll(toolRegistry.getFixedTools());

        // toolNames 必须是"真正注册且启用"的名字。
        // Spring AI 的 DefaultToolCallingManager.resolveToolDefinitions 会对 toolNames 中
        // 无法解析的名字抛 IllegalStateException，未过滤会导致整个对话 500。
        List<String> toolNames = new ArrayList<>(toolRegistry.resolveToolNames(configuredTools));
        toolNames.addAll(toolRegistry.getFixedToolNames());

        Integer maxSteps = request.getMaxSteps() != null
                ? request.getMaxSteps()
                : (agent.getMaxSteps() != null && agent.getMaxSteps() > 0
                    ? agent.getMaxSteps()
                    : properties.getAgent().getMaxSteps());

        AgentContext context = new AgentContext();
        context.setChatClient(chatClient);
        context.setSystemPrompt(agent.getSystemPrompt());
        context.setHistory(loadHistory(request.getConversationId()));
        context.setUserMessage(request.getMessage());
        context.setTools(tools);
        context.setToolNames(toolNames);
        context.setMaxSteps(maxSteps);
        context.setModelId(modelId);
        // RAG：把查询交给执行引擎，在异步线程里检索，不阻塞 HTTP 线程
        if (request.isEnableRag()) {
            context.setKnowledgeQuery(request.getMessage());
        }
        return context;
    }

    private Agent loadAgent(Long agentId) {
        if (agentId == null) {
            throw BizException.badRequest("请先创建并指定智能体");
        }
        Agent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw BizException.notFound("智能体不存在: " + agentId);
        }
        return agent;
    }

    /**
     * 加载会话历史，过滤掉 tool 消息（避免历史工具调用上下文污染），并限制条数。
     */
    private List<org.springframework.ai.chat.messages.Message> loadHistory(Long conversationId) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        if (conversationId == null) {
            return messages;
        }
        List<com.xunwu.agent.entity.Message> history =
                messageMapper.selectByConversationId(conversationId);
        if (history.isEmpty()) {
            return messages;
        }
        // 只保留最近 MAX_HISTORY_MESSAGES 条
        int fromIndex = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (com.xunwu.agent.entity.Message m : history.subList(fromIndex, history.size())) {
            switch (m.getRole()) {
                case "user" -> messages.add(new UserMessage(m.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(m.getContent()));
                case "system" -> messages.add(new SystemMessage(m.getContent()));
                default -> {
                    // 忽略 tool 消息
                }
            }
        }
        return messages;
    }

    private void saveMessage(Long conversationId, String role, String content,
                             String model, String toolCalls) {
        com.xunwu.agent.entity.Message message = new com.xunwu.agent.entity.Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setModel(model);
        message.setToolCalls(toolCalls);
        messageMapper.insert(message);
    }

    /**
     * 解析智能体配置的工具名列表（JSON 数组）。
     * 解析失败时返回空列表而不是抛异常 —— 工具配置损坏不应导致整个智能体不可用。
     */
    private List<String> parseToolIds(String toolIdsJson) {
        List<String> names = new ArrayList<>();
        if (toolIdsJson == null || toolIdsJson.isBlank()) {
            return names;
        }
        try {
            var node = OBJECT_MAPPER.readTree(toolIdsJson);
            if (node != null && node.isArray()) {
                node.forEach(n -> {
                    String text = n.asText().trim();
                    if (!text.isEmpty()) {
                        names.add(text);
                    }
                });
            } else {
                log.warn("toolIds 不是合法 JSON 数组，已忽略: {}", toolIdsJson);
            }
        } catch (Exception e) {
            log.warn("解析工具列表失败，已忽略: {}", toolIdsJson, e);
        }
        return names;
    }

    /** 兜底：把异常翻译成可读提示，不泄露内部细节。 */
    private String toUserMessage(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof IllegalArgumentException) {
            return cause.getMessage();
        }
        return "对话处理失败，请稍后重试";
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
