package com.xunwu.agent.chat;

import com.xunwu.agent.agent.AgentContext;
import com.xunwu.agent.agent.AgentEvent;
import com.xunwu.agent.agent.AgentExecutor;
import com.xunwu.agent.agent.AgentRequest;
import com.xunwu.agent.agent.AgentState;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.entity.Agent;
import com.xunwu.agent.entity.Conversation;
import com.xunwu.agent.mapper.AgentMapper;
import com.xunwu.agent.mapper.ConversationMapper;
import com.xunwu.agent.mapper.MessageMapper;
import com.xunwu.agent.model.ChatClientRegistry;
import com.xunwu.agent.tool.ToolRegistry;
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

    private final AgentMapper agentMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ChatClientRegistry chatClientRegistry;
    private final ToolRegistry toolRegistry;
    private final AgentExecutor agentExecutor;

    public ChatService(AgentMapper agentMapper,
                       ConversationMapper conversationMapper,
                       MessageMapper messageMapper,
                       ChatClientRegistry chatClientRegistry,
                       ToolRegistry toolRegistry,
                       AgentExecutor agentExecutor) {
        this.agentMapper = agentMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.chatClientRegistry = chatClientRegistry;
        this.toolRegistry = toolRegistry;
        this.agentExecutor = agentExecutor;
    }

    /**
     * 流式对话
     */
    public Flux<AgentEvent> stream(AgentRequest request) {
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

        // 4. 组装上下文
        AgentContext context = buildContext(request, agent);

        // 5. 执行并推送事件
        return agentExecutor.executeStream(context)
                .doOnNext(event -> {
                    if ("done".equals(event.getType())) {
                        saveMessage(finalConversationId, "assistant",
                                (String) event.getData(), context.getModelId(), null);
                    }
                })
                .doOnComplete(() -> log.info("对话完成: conversationId={}", finalConversationId))
                .doOnError(e -> log.error("对话异常: conversationId={}", finalConversationId, e));
    }

    /**
     * 组装执行上下文
     */
    private AgentContext buildContext(AgentRequest request, Agent agent) {
        String modelId = request.getModelId() != null ? request.getModelId() : agent.getModelId();
        ChatClient chatClient = chatClientRegistry.getChatClient(modelId);

        List<org.springframework.ai.chat.messages.Message> history = loadHistory(request.getConversationId());

        // 工具
        List<String> toolNames = parseToolIds(agent.getToolIds());
        List<ToolCallback> tools = toolRegistry.getTools(toolNames);
        // 固定工具始终可用
        tools.addAll(toolRegistry.getFixedTools());

        int maxSteps = request.getMaxSteps() != null ? request.getMaxSteps() : agent.getMaxSteps();

        AgentContext context = new AgentContext();
        context.setChatClient(chatClient);
        context.setSystemPrompt(agent.getSystemPrompt());
        context.setHistory(history);
        context.setUserMessage(request.getMessage());
        context.setTools(tools);
        context.setToolNames(toolNames);
        context.setMaxSteps(maxSteps);
        context.setModelId(modelId);
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

    private List<org.springframework.ai.chat.messages.Message> loadHistory(Long conversationId) {
        if (conversationId == null) {
            return new ArrayList<>();
        }
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messageMapper.selectByConversationId(conversationId).forEach(m -> {
            switch (m.getRole()) {
                case "user" -> messages.add(new UserMessage(m.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(m.getContent()));
                case "system" -> messages.add(new SystemMessage(m.getContent()));
                default -> {
                    // 忽略 tool 消息（避免历史工具调用上下文污染）
                }
            }
        });
        return messages;
    }

    private void saveMessage(Long conversationId, String role, String content,
                             String model, String toolCalls) {
        com.xunwu.agent.entity.Message message = new com.xunwu.agent.entity.Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setModel(model);
        message.setToolCalls(toolCalls);
        messageMapper.insert(message);
    }

    private List<String> parseToolIds(String toolIdsJson) {
        List<String> names = new ArrayList<>();
        if (toolIdsJson == null || toolIdsJson.isBlank()) {
            return names;
        }
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(toolIdsJson);
            if (node.isArray()) {
                node.forEach(n -> names.add(n.asText()));
            }
        } catch (Exception e) {
            log.warn("解析工具列表失败: {}", toolIdsJson, e);
        }
        return names;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
