package com.xunwu.agent.agent;

import com.xunwu.agent.common.ThrowableUtils;
import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.KnowledgeSearchResult;
import com.xunwu.agent.rag.KnowledgeSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
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
 * <p>流式路径使用 {@code ChatClient.stream().chatResponse()} <b>一次调用</b>同时拿到
 * 增量文本与聚合后的 tool_calls，既保证真流式输出，也避免"两次生成结果不一致"的问题。</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class AgentExecutor {

    /** 单次模型调用的最长等待时间 */
    private static final Duration MODEL_TIMEOUT = Duration.ofMinutes(10);

    private final AgentToolService toolService;
    private final KnowledgeSearchService knowledgeSearchService;
    private final XunwuProperties properties;
    private final Executor agentTaskExecutor;

    public AgentExecutor(AgentToolService toolService,
                         KnowledgeSearchService knowledgeSearchService,
                         XunwuProperties properties,
                         @Qualifier("taskExecutor") Executor agentTaskExecutor) {
        this.toolService = toolService;
        this.knowledgeSearchService = knowledgeSearchService;
        this.properties = properties;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    /**
     * 流式执行：通过 FluxSink 推送 token 与工具事件
     */
    public Flux<AgentEvent> executeStream(AgentContext context) {
        // Flux.create + BUFFER：sink 在订阅时创建，且缓冲无上限，
        // 不会出现"订阅前事件被丢弃"或"客户端慢导致溢出"的问题。
        return Flux.create(emitter -> {
            // 在托管线程池中执行，避免阻塞响应线程，也避免裸线程无法回收
            agentTaskExecutor.execute(() -> {
                try {
                    runStream(context, emitter);
                    emitter.complete();
                } catch (Exception e) {
                    log.error("Agent 流式执行异常", e);
                    if (emitter.isCancelled()) {
                        return;
                    }
                    // SSE 连接已建立，HTTP 状态码无法再改变：
                    // 必须把错误作为事件推给客户端，否则前端只会看到流被截断。
                    emitter.next(AgentEvent.error(toUserMessage(e)));
                    emitter.complete();
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private void runStream(AgentContext context, FluxSink<AgentEvent> sink) {
        loadKnowledge(context);

        List<Message> messages = buildMessages(context);
        AtomicInteger step = new AtomicInteger(0);
        int maxSteps = context.getMaxSteps();

        while (step.incrementAndGet() <= maxSteps) {
            // 客户端断连后立即停止，避免继续消耗模型算力
            if (sink.isCancelled()) {
                log.info("客户端已断连，Agent 提前终止");
                return;
            }
            sink.next(AgentEvent.thinking(step.get()));

            // 一次调用：边收 token 边推送，同时聚合 tool_calls
            StreamingOutcome outcome = callModelStreaming(context, messages, sink, step.get());
            String content = outcome.content();
            List<AssistantMessage.ToolCall> toolCalls = outcome.toolCalls();

            if (toolCalls.isEmpty()) {
                sink.next(AgentEvent.done(content, step.get()));
                return;
            }

            // 工具调用
            if (sink.isCancelled()) {
                log.info("客户端已断连，跳过工具执行");
                return;
            }
            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall call : toolCalls) {
                sink.next(AgentEvent.toolCalling(call.name(), call.arguments(), step.get()));
                String result = toolService.execute(call.name(), call.arguments());
                sink.next(AgentEvent.toolResult(call.name(), result, step.get()));
                toolResponses.add(new ToolResponseMessage.ToolResponse(
                        call.id(), call.name(), result));
            }
            messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
        }

        sink.next(AgentEvent.maxSteps(step.get()));
    }

    /**
     * 流式调用模型：逐个 token 推送到 sink，并聚合工具调用。
     *
     * <p>Spring AI 的流式响应会把 tool_calls 按 index 累积到 {@link ConcurrentHashMap}，
     * 每个 chunk 携带的是"截至目前的完整快照"，因此这里按 index 合并、
     * 优先取信息更完整的一份即可。</p>
     */
    private StreamingOutcome callModelStreaming(AgentContext context, List<Message> messages,
                                                FluxSink<AgentEvent> sink, int step) {
        ChatOptions chatOptions = buildChatOptions(context);

        StringBuilder content = new StringBuilder();
        Map<Integer, AssistantMessage.ToolCall> mergedToolCalls = new LinkedHashMap<>();

        Flux<ChatResponse> responseFlux = context.getChatClient().prompt()
                .messages(messages)
                .options(chatOptions)
                .stream()
                .chatResponse();

        responseFlux
                .doOnNext(chunk -> {
                    if (sink.isCancelled()) {
                        return;
                    }
                    if (chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }
                    AssistantMessage output = chunk.getResult().getOutput();

                    String delta = output.getText();
                    if (delta != null && !delta.isEmpty()) {
                        content.append(delta);
                        sink.next(AgentEvent.token(delta, step));
                    }

                    List<AssistantMessage.ToolCall> calls = output.getToolCalls();
                    if (calls != null && !calls.isEmpty()) {
                        mergeToolCalls(mergedToolCalls, calls);
                    }
                })
                .blockLast(MODEL_TIMEOUT);

        // 用同一次生成的内容构造 assistant 消息，保证历史与用户所见一致
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(content.toString())
                .toolCalls(new ArrayList<>(mergedToolCalls.values()))
                .build();
        messages.add(assistantMessage);

        return new StreamingOutcome(content.toString(), new ArrayList<>(mergedToolCalls.values()));
    }

    /**
     * 合并流式 tool_calls：按 index 对齐，优先保留非空的 id/type/name 与更完整的 arguments。
     */
    private void mergeToolCalls(Map<Integer, AssistantMessage.ToolCall> merged,
                                List<AssistantMessage.ToolCall> calls) {
        for (int i = 0; i < calls.size(); i++) {
            AssistantMessage.ToolCall call = calls.get(i);
            AssistantMessage.ToolCall existing = merged.get(i);
            if (existing == null) {
                merged.put(i, call);
                continue;
            }
            merged.put(i, new AssistantMessage.ToolCall(
                    firstNonBlank(call.id(), existing.id()),
                    firstNonBlank(call.type(), existing.type()),
                    firstNonBlank(call.name(), existing.name()),
                    longer(call.arguments(), existing.arguments())));
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private String longer(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.length() >= b.length() ? a : b;
    }

    /**
     * 知识库检索：把命中的片段写入上下文，供 {@link #buildMessages} 注入 SystemMessage。
     * 在异步线程执行，不阻塞 HTTP 请求线程；检索失败时降级为"无知识库"而不是中断对话。
     */
    private void loadKnowledge(AgentContext context) {
        String query = context.getKnowledgeQuery();
        if (query == null || query.isBlank() || context.isKnowledgeLoaded()) {
            return;
        }
        try {
            List<KnowledgeSearchResult> results = knowledgeSearchService.search(
                    query, properties.getRag().getTopK());
            String knowledgeContext = knowledgeSearchService.buildContext(results);
            if (!knowledgeContext.isBlank()) {
                context.setKnowledgeContext(knowledgeContext);
            }
            log.info("知识库检索完成: hits={}, contextLength={}",
                    results.size(), knowledgeContext.length());
        } catch (Exception e) {
            log.warn("知识库检索失败，本次对话不使用知识库: {}", e.getMessage());
        } finally {
            context.setKnowledgeLoaded(true);
        }
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

    private ChatOptions buildChatOptions(AgentContext context) {
        List<String> toolNames = context.getToolNames() == null ? List.of() : context.getToolNames();
        return ToolCallingChatOptions.builder()
                .toolCallbacks(context.getTools() == null ? List.of() : context.getTools())
                .toolNames(new java.util.HashSet<>(toolNames))
                .internalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 单次流式调用的结果
     */
    private record StreamingOutcome(String content, List<AssistantMessage.ToolCall> toolCalls) {
    }

    /**
     * 把底层异常翻译成用户可读的提示。
     * 只暴露"该做什么"，不泄露堆栈、连接串等内部信息。
     */
    private String toUserMessage(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof WebClientResponseException wce) {
            int status = wce.getStatusCode().value();
            if (status == 404) {
                return "模型不可用：模型服务返回 404。本地模型请先执行 "
                        + "ollama pull <模型名> 拉取，远端模型请检查模型名称。";
            }
            if (status == 401 || status == 403) {
                return "模型服务鉴权失败：请检查 API Key 是否正确配置。";
            }
            if (status >= 500) {
                return "模型服务暂时不可用（HTTP " + status + "），请稍后重试。";
            }
            return "模型调用失败（HTTP " + status + "），请稍后重试。";
        }
        if (ThrowableUtils.isConnectionFailure(cause)) {
            return "无法连接模型服务，请确认 Ollama 已启动、网络可达。";
        }
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return "模型响应超时，请稍后重试或减小输入长度。";
        }
        return "模型调用失败：" + cause.getMessage();
    }
}
