package com.xunwu.agent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 执行引擎
 *
 * <p>基于 Spring AI 实现 <b>Think-Execute</b> 执行循环：</p>
 * <pre>
 * 用户输入 → [THINKING] 模型推理
 *              ↓
 *        有 tool_calls?
 *          ├─ 是 → [TOOL_CALLING] 执行工具 → 结果回传 → 继续推理
 *          └─ 否 → [COMPLETED] 输出最终回答
 *              ↓
 *        步数 ≥ maxSteps → [MAX_STEPS_REACHED] 终止
 * </pre>
 *
 * <p>关闭框架内部自动工具执行（{@code internalToolExecutionEnabled(false)}），
 * 由业务层显式管理模型响应、工具调用及执行结果。</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class AgentExecutor {

    private final AgentToolService toolService;

    public AgentExecutor(AgentToolService toolService) {
        this.toolService = toolService;
    }

    /**
     * 执行一次完整任务（非流式）
     */
    public AgentEvent.ExecutionResult execute(AgentContext context) {
        List<Message> messages = buildMessages(context);
        AtomicInteger step = new AtomicInteger(0);
        int maxSteps = context.getMaxSteps();

        while (step.incrementAndGet() <= maxSteps) {
            log.debug("Agent 第 {} 步推理", step.get());

            var response = callModel(context, messages);
            AssistantMessage assistantMessage = response.getResult().getOutput();
            messages.add(assistantMessage);

            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                return new AgentEvent.ExecutionResult(
                        assistantMessage.getText(),
                        AgentState.COMPLETED.name(),
                        step.get());
            }

            // 工具调用
            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall call : toolCalls) {
                String result = toolService.execute(call.name(), call.arguments());
                toolResponses.add(new ToolResponseMessage.ToolResponse(
                        call.id(), call.name(), result));
            }
            messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
        }

        log.warn("Agent 达到最大执行步数 {}，任务终止", maxSteps);
        return new AgentEvent.ExecutionResult(
                null, AgentState.MAX_STEPS_REACHED.name(), step.get());
    }

    /**
     * 流式执行：通过 Sinks 推送 token 与工具事件
     */
    public Flux<AgentEvent> executeStream(AgentContext context) {
        Sinks.Many<AgentEvent> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 在独立线程中执行，避免阻塞响应线程
        new Thread(() -> {
            try {
                runStream(context, sink);
                sink.tryEmitComplete();
            } catch (Exception e) {
                log.error("Agent 流式执行异常", e);
                sink.tryEmitError(e);
            }
        }, "xunwu-agent-stream").start();

        return sink.asFlux();
    }

    private void runStream(AgentContext context, Sinks.Many<AgentEvent> sink) {
        List<Message> messages = buildMessages(context);
        AtomicInteger step = new AtomicInteger(0);
        int maxSteps = context.getMaxSteps();

        while (step.incrementAndGet() <= maxSteps) {
            sink.tryEmitNext(AgentEvent.thinking(step.get()));

            // 调用模型（流式）
            Sinks.Many<String> tokenSink = Sinks.many().unicast().onBackpressureBuffer();
            StringBuilder contentBuilder = new StringBuilder();

            var responseFlux = streamModel(context, messages);
            responseFlux.subscribe(
                    token -> {
                        contentBuilder.append(token);
                        tokenSink.tryEmitNext(token);
                    },
                    error -> tokenSink.tryEmitError(error),
                    tokenSink::tryEmitComplete);

            // 等待流完成（简单同步等待）
            List<String> tokens = tokenSink.asFlux().collectList().block();
            String content = tokens == null ? "" : String.join("", tokens);

            // 重新调用一次获取完整响应（含 tool_calls）
            // 注意：流式调用无法直接拿到 tool_calls，这里用非流式获取工具调用
            var response = callModel(context, messages);
            AssistantMessage assistantMessage = response.getResult().getOutput();
            messages.add(assistantMessage);

            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                if (!content.isBlank()) {
                    sink.tryEmitNext(AgentEvent.token(content, step.get()));
                }
                sink.tryEmitNext(AgentEvent.done(content, step.get()));
                return;
            }

            // 工具调用
            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall call : toolCalls) {
                sink.tryEmitNext(AgentEvent.toolCalling(call.name(), call.arguments(), step.get()));
                String result = toolService.execute(call.name(), call.arguments());
                sink.tryEmitNext(AgentEvent.toolResult(call.name(), result, step.get()));
                toolResponses.add(new ToolResponseMessage.ToolResponse(
                        call.id(), call.name(), result));
            }
            messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
        }

        sink.tryEmitNext(AgentEvent.maxSteps(step.get()));
    }

    private List<Message> buildMessages(AgentContext context) {
        List<Message> messages = new ArrayList<>();
        if (context.getSystemPrompt() != null && !context.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(context.getSystemPrompt()));
        }
        if (context.getKnowledgeContext() != null && !context.getKnowledgeContext().isBlank()) {
            messages.add(new SystemMessage("以下是相关知识库内容，请结合回答：\n"
                    + context.getKnowledgeContext()));
        }
        messages.addAll(context.getHistory());
        messages.add(new UserMessage(context.getUserMessage()));
        return messages;
    }

    private org.springframework.ai.chat.model.ChatResponse callModel(AgentContext context,
                                                                    List<Message> messages) {
        ChatClient client = context.getChatClient();
        List<ToolCallback> tools = context.getTools();

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .toolNames(new java.util.HashSet<>(context.getToolNames()))
                .internalToolExecutionEnabled(false)
                .build();

        ChatClient.ChatClientRequestSpec spec = client.prompt()
                .messages(messages)
                .options(chatOptions);

        return spec.call().chatResponse();
    }

    private Flux<String> streamModel(AgentContext context, List<Message> messages) {
        ChatClient client = context.getChatClient();
        List<ToolCallback> tools = context.getTools();

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .toolNames(new java.util.HashSet<>(context.getToolNames()))
                .internalToolExecutionEnabled(false)
                .build();

        return client.prompt()
                .messages(messages)
                .options(chatOptions)
                .stream()
                .content();
    }
}
