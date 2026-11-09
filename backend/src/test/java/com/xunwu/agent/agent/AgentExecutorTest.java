package com.xunwu.agent.agent;

import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.rag.KnowledgeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link AgentExecutor} 单元测试
 *
 * <p>用 Mock 的 {@link ChatClient} 模拟模型响应，验证 Think-Execute 循环：
 * 普通补全、工具调用续跑、达到最大步数、空回复兜底。</p>
 *
 * @author xunwu
 */
class AgentExecutorTest {

    private AgentToolService toolService;
    private KnowledgeSearchService knowledgeSearchService;
    private AgentExecutor executor;

    @BeforeEach
    void setUp() {
        toolService = mock(AgentToolService.class);
        knowledgeSearchService = mock(KnowledgeSearchService.class);
        XunwuProperties properties = new XunwuProperties();
        // 同步执行，保证测试确定性
        Executor sameThread = command -> command.run();
        executor = new AgentExecutor(toolService, knowledgeSearchService, properties, sameThread);
    }

    // ---------- helpers ----------

    private ChatResponse textResponse(String text) {
        AssistantMessage message = AssistantMessage.builder().content(text).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private ChatResponse toolResponse(String name, String arguments) {
        AssistantMessage.ToolCall call =
                new AssistantMessage.ToolCall("call-1", "function", name, arguments);
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(call))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private AgentContext context(ChatClient chatClient, int maxSteps) {
        AgentContext context = new AgentContext();
        context.setChatClient(chatClient);
        context.setSystemPrompt("sys");
        context.setUserMessage("hi");
        context.setTools(new ArrayList<>());
        context.setToolNames(new ArrayList<>());
        context.setMaxSteps(maxSteps);
        context.setModelId("qwen2.5:7b");
        context.setHistory(new ArrayList<>());
        return context;
    }

    private ChatClient mockChatClient(ChatResponse... responses) {
        ChatClient chatClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        // 每次调用返回下一个响应（最后一步重复最后一个），模拟多轮对话
        int[] call = {0};
        when(chatClient.prompt()
                .messages(anyList())
                .options(any(ChatOptions.class))
                .stream()
                .chatResponse())
                .thenAnswer(invocation -> {
                    int index = Math.min(call[0], responses.length - 1);
                    call[0]++;
                    return Flux.just(responses[index]);
                });
        return chatClient;
    }

    private List<String> eventTypes(List<AgentEvent> events) {
        return events.stream().map(AgentEvent::getType).toList();
    }

    // ---------- tests ----------

    @Test
    void simpleCompletion_emitsThinkingTokenDone() {
        ChatClient chatClient = mockChatClient(textResponse("hello"));
        List<AgentEvent> events = executor.executeStream(context(chatClient, 3))
                .collectList().block();

        assertNotNull(events);
        assertEquals(List.of("thinking", "token", "done"), eventTypes(events));
        assertEquals("hello", events.get(2).getData());
    }

    @Test
    void toolCall_executesToolAndContinues() {
        when(toolService.execute("calculator", "{\"expression\":\"1+1\"}"))
                .thenReturn("{\"result\":2.0}");
        ChatClient chatClient = mockChatClient(
                toolResponse("calculator", "{\"expression\":\"1+1\"}"),
                textResponse("结果是 2"));

        List<AgentEvent> events = executor.executeStream(context(chatClient, 3))
                .collectList().block();

        assertNotNull(events);
        assertEquals(List.of("thinking", "tool_calling", "tool_result",
                "thinking", "token", "done"), eventTypes(events));
        verify(toolService).execute("calculator", "{\"expression\":\"1+1\"}");
    }

    @Test
    void maxStepsReachedWithoutContent_emitsMaxSteps() {
        when(toolService.execute(anyString(), any())).thenReturn("{}");
        ChatClient chatClient = mockChatClient(
                toolResponse("calculator", "{}"),
                toolResponse("calculator", "{}"));

        List<AgentEvent> events = executor.executeStream(context(chatClient, 2))
                .collectList().block();

        assertNotNull(events);
        assertEquals("max_steps", events.get(events.size() - 1).getType());
    }

    @Test
    void emptyContent_emitsError() {
        ChatClient chatClient = mockChatClient(textResponse(""));
        List<AgentEvent> events = executor.executeStream(context(chatClient, 3))
                .collectList().block();

        assertNotNull(events);
        assertEquals("error", events.get(events.size() - 1).getType());
    }
}
