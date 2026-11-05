package com.xunwu.agent.tool;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具调度与执行管理器
 *
 * <p>统一管理工具调度与执行，降低工具实现与 Agent 核心流程之间的耦合。
 * 业务层可显式调用工具，而不依赖框架的自动工具执行。</p>
 *
 * @author xunwu
 */
@Component
public class ToolCallingManagerImpl implements ToolCallingManager {

    private final ToolCallingManager delegate;

    public ToolCallingManagerImpl(ToolCallingManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<org.springframework.ai.tool.definition.ToolDefinition> resolveToolDefinitions(
            org.springframework.ai.model.tool.ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        return delegate.executeToolCalls(prompt, chatResponse);
    }

    /**
     * 从工具执行结果中取出工具响应消息，追加到消息列表
     */
    public static void appendToolResponse(List<Message> messages,
                                          ToolExecutionResult result) {
        if (result == null) {
            return;
        }
        result.conversationHistory().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .forEach(messages::add);
    }

    /**
     * 从 ChatResponse 中解析工具调用
     */
    public static List<AssistantMessage.ToolCall> extractToolCalls(ChatResponse chatResponse) {
        if (chatResponse == null || !chatResponse.hasToolCalls()) {
            return List.of();
        }
        return chatResponse.getResult().getOutput().getToolCalls();
    }

    /**
     * 构建工具上下文
     */
    public static ToolContext buildContext(Map<String, Object> ctx) {
        return new ToolContext(ctx == null ? Map.of() : ctx);
    }

    /**
     * 手动构建工具响应消息（当不使用框架自动执行时）
     */
    public static ToolResponseMessage buildToolResponse(List<AssistantMessage.ToolCall> toolCalls,
                                                        Map<String, String> callResults) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall call : toolCalls) {
            responses.add(new ToolResponseMessage.ToolResponse(
                    call.id(),
                    call.name(),
                    callResults.getOrDefault(call.name(), "")));
        }
        return ToolResponseMessage.builder().responses(responses).build();
    }
}
