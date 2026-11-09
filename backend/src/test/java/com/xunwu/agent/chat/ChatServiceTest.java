package com.xunwu.agent.chat;

import com.xunwu.agent.agent.AgentContext;
import com.xunwu.agent.agent.AgentEvent;
import com.xunwu.agent.agent.AgentExecutor;
import com.xunwu.agent.agent.AgentRequest;
import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.Agent;
import com.xunwu.agent.entity.Conversation;
import com.xunwu.agent.entity.Message;
import com.xunwu.agent.mapper.AgentMapper;
import com.xunwu.agent.mapper.ConversationMapper;
import com.xunwu.agent.mapper.MessageMapper;
import com.xunwu.agent.model.ChatClientRegistry;
import com.xunwu.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * {@link ChatService} 单元测试。
 *
 * <p>重点覆盖 P14 新增的「重新生成」链路：旧回复清理、以最后一条用户消息为输入、
 * 空内容不落库、异常统一转成 error 事件。</p>
 *
 * @author xunwu
 */
class ChatServiceTest {

    private AgentMapper agentMapper;
    private ConversationMapper conversationMapper;
    private MessageMapper messageMapper;
    private ChatClientRegistry chatClientRegistry;
    private ToolRegistry toolRegistry;
    private AgentExecutor agentExecutor;
    private ChatService service;

    @BeforeEach
    void setUp() {
        agentMapper = mock(AgentMapper.class);
        conversationMapper = mock(ConversationMapper.class);
        messageMapper = mock(MessageMapper.class);
        chatClientRegistry = mock(ChatClientRegistry.class);
        toolRegistry = mock(ToolRegistry.class);
        agentExecutor = mock(AgentExecutor.class);

        when(toolRegistry.getTools(anyList())).thenReturn(new ArrayList<>());
        when(toolRegistry.getFixedTools()).thenReturn(new ArrayList<>());
        when(toolRegistry.getFixedToolNames()).thenReturn(new ArrayList<>());
        when(chatClientRegistry.getChatClient(any())).thenReturn(mock(
                org.springframework.ai.chat.client.ChatClient.class));

        service = new ChatService(agentMapper, conversationMapper, messageMapper,
                chatClientRegistry, toolRegistry, agentExecutor, new XunwuProperties());
    }

    // ==================== 辅助 ====================

    private Agent agent(Long id, String modelId) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName("测试智能体");
        agent.setModelId(modelId);
        agent.setMaxSteps(8);
        agent.setToolIds("[]");
        agent.setSystemPrompt("你是测试助手");
        return agent;
    }

    private Conversation conversation(Long id, Long agentId) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setAgentId(agentId);
        c.setTitle("会话");
        c.setStatus("ACTIVE");
        return c;
    }

    private Message message(Long id, String role, String content) {
        Message m = new Message();
        m.setId(id);
        m.setConversationId(1L);
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    /** 让执行引擎返回一个 done 事件，内容可指定 */
    private void givenExecution(AgentEvent... events) {
        when(agentExecutor.executeStream(any(AgentContext.class)))
                .thenReturn(Flux.fromArray(events));
    }

    private List<AgentEvent> collect(Flux<AgentEvent> flux) {
        return flux.collectList().block();
    }

    // ==================== 普通对话 ====================

    @Test
    void stream_blankMessage_emitsErrorEvent() {
        List<AgentEvent> events = collect(service.stream(request(null, "   ")));

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
        verifyNoInteractions(agentMapper, conversationMapper, messageMapper, agentExecutor);
    }

    @Test
    void stream_agentNotFound_emitsErrorEvent() {
        when(agentMapper.selectById(1L)).thenReturn(null);

        List<AgentEvent> events = collect(service.stream(request(1L, "你好")));

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
        assertTrue(String.valueOf(events.get(0).getData()).contains("智能体不存在"));
        verifyNoInteractions(agentExecutor);
    }

    @Test
    void stream_newConversation_createsConversationAndSavesUserMessage() {
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(conversationMapper.insert(any(Conversation.class))).thenAnswer(inv -> {
            inv.getArgument(0, Conversation.class).setId(77L);
            return 1;
        });
        givenExecution(AgentEvent.done("你好，我是助手", 1));

        AgentRequest request = request(1L, "你好");
        request.setConversationId(null);

        List<AgentEvent> events = collect(service.stream(request));

        // 会话已创建并回填自增主键
        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).insert(conversationCaptor.capture());
        assertEquals("你好", conversationCaptor.getValue().getTitle());
        assertEquals("ACTIVE", conversationCaptor.getValue().getStatus());

        // 用户消息 + 助手消息都落库
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper, times(2)).insert(messageCaptor.capture());
        assertEquals("user", messageCaptor.getAllValues().get(0).getRole());
        assertEquals("你好", messageCaptor.getAllValues().get(0).getContent());
        assertEquals("assistant", messageCaptor.getAllValues().get(1).getRole());
        assertEquals("你好，我是助手", messageCaptor.getAllValues().get(1).getContent());

        assertEquals("done", events.get(0).getType());
    }

    @Test
    void stream_existingConversation_reusesItWithoutCreating() {
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(conversationMapper.selectById(55L)).thenReturn(conversation(55L, 1L));
        givenExecution(AgentEvent.done("收到", 1));

        AgentRequest request = request(1L, "继续");
        request.setConversationId(55L);

        collect(service.stream(request));

        verify(conversationMapper, never()).insert(any());
        verify(messageMapper, times(2)).insert(any(Message.class));
    }

    @Test
    void stream_blankAssistantContent_notPersisted() {
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        givenExecution(AgentEvent.done("   ", 1));

        collect(service.stream(request(1L, "你好")));

        // 只有用户消息落库，空气泡被跳过
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper, times(1)).insert(captor.capture());
        assertEquals("user", captor.getValue().getRole());
    }

    @Test
    void stream_modelOverride_passedToContext() {
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        givenExecution(AgentEvent.done("ok", 1));

        AgentRequest request = request(1L, "你好");
        request.setModelId("deepseek-chat");

        collect(service.stream(request));

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(agentExecutor).executeStream(captor.capture());
        assertEquals("deepseek-chat", captor.getValue().getModelId());
    }

    @Test
    void stream_ragDisabled_contextHasNoKnowledgeQuery() {
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        givenExecution(AgentEvent.done("ok", 1));

        AgentRequest request = request(1L, "你好");
        request.setEnableRag(false);

        collect(service.stream(request));

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(agentExecutor).executeStream(captor.capture());
        assertNull(captor.getValue().getKnowledgeQuery());
    }

    @Test
    void stream_executorThrows_emitsErrorEvent() {
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(agentExecutor.executeStream(any(AgentContext.class)))
                .thenThrow(new IllegalStateException("boom"));

        List<AgentEvent> events = collect(service.stream(request(1L, "你好")));

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
    }

    // ==================== 重新生成 ====================

    @Test
    void regenerate_conversationNotFound_emitsErrorEvent() {
        when(conversationMapper.selectById(9L)).thenReturn(null);

        List<AgentEvent> events = collect(service.regenerate(9L, null, true));

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
        assertTrue(String.valueOf(events.get(0).getData()).contains("会话不存在"));
        verifyNoInteractions(agentExecutor);
    }

    @Test
    void regenerate_noUserMessage_emitsErrorEvent() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(messageMapper.selectByConversationId(1L))
                .thenReturn(List.of(message(1L, "assistant", "只有回复")));

        List<AgentEvent> events = collect(service.regenerate(1L, null, true));

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
        assertTrue(String.valueOf(events.get(0).getData()).contains("没有可重新生成"));
        verify(messageMapper, never()).deleteAfterId(any(), any());
        verifyNoInteractions(agentExecutor);
    }

    @Test
    void regenerate_deletesRepliesAfterLastUserMessage() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(messageMapper.selectByConversationId(1L)).thenReturn(List.of(
                message(1L, "user", "第一问"),
                message(2L, "assistant", "第一答"),
                message(3L, "user", "第二问"),
                message(4L, "assistant", "第二答")));
        when(messageMapper.deleteAfterId(1L, 3L)).thenReturn(1);
        givenExecution(AgentEvent.done("重新生成的答案", 1));

        List<AgentEvent> events = collect(service.regenerate(1L, null, true));

        // 只保留最后一条用户消息之后的旧回复被清掉
        verify(messageMapper).deleteAfterId(1L, 3L);

        // 输入是最后一条用户消息，且不再重复保存用户消息
        ArgumentCaptor<AgentContext> contextCaptor = ArgumentCaptor.forClass(AgentContext.class);
        verify(agentExecutor).executeStream(contextCaptor.capture());
        assertEquals("第二问", contextCaptor.getValue().getUserMessage());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper, times(1)).insert(messageCaptor.capture());
        assertEquals("assistant", messageCaptor.getValue().getRole());
        assertEquals("重新生成的答案", messageCaptor.getValue().getContent());

        assertEquals("done", events.get(0).getType());
    }

    @Test
    void regenerate_doesNotReinsertUserMessage() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(messageMapper.selectByConversationId(1L)).thenReturn(List.of(
                message(1L, "user", "问题"),
                message(2L, "assistant", "旧答案")));
        givenExecution(AgentEvent.done("新答案", 1));

        collect(service.regenerate(1L, null, true));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper, times(1)).insert(captor.capture());
        assertEquals("assistant", captor.getValue().getRole());
    }

    @Test
    void regenerate_modelOverride_passedToContext() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(messageMapper.selectByConversationId(1L)).thenReturn(List.of(
                message(1L, "user", "问题")));
        givenExecution(AgentEvent.done("答案", 1));

        collect(service.regenerate(1L, "deepseek-chat", false));

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(agentExecutor).executeStream(captor.capture());
        assertEquals("deepseek-chat", captor.getValue().getModelId());
        assertNull(captor.getValue().getKnowledgeQuery());
    }

    @Test
    void regenerate_blankContent_notPersisted() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(messageMapper.selectByConversationId(1L)).thenReturn(List.of(
                message(1L, "user", "问题")));
        givenExecution(AgentEvent.done("", 1));

        collect(service.regenerate(1L, null, true));

        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    void regenerate_executorThrows_emitsErrorEvent() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(messageMapper.selectByConversationId(1L)).thenReturn(List.of(
                message(1L, "user", "问题")));
        when(agentExecutor.executeStream(any(AgentContext.class)))
                .thenThrow(new IllegalStateException("boom"));

        List<AgentEvent> events = collect(service.regenerate(1L, null, true));

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
    }

    @Test
    void regenerate_executorStreamErrors_emitsErrorEvent() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(messageMapper.selectByConversationId(1L)).thenReturn(List.of(
                message(1L, "user", "问题")));
        when(agentExecutor.executeStream(any(AgentContext.class)))
                .thenReturn(Flux.error(new RuntimeException("boom")));

        List<AgentEvent> events = collect(service.regenerate(1L, null, true));

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
        // 不泄露内部堆栈，只给可读提示
        assertEquals("对话处理失败，请稍后重试", events.get(0).getData());
    }

    @Test
    void regenerate_illegalArgument_translatedToMessage() {
        when(conversationMapper.selectById(1L)).thenReturn(conversation(1L, 1L));
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(messageMapper.selectByConversationId(1L)).thenReturn(List.of(
                message(1L, "user", "问题")));
        when(chatClientRegistry.getChatClient(any()))
                .thenThrow(new IllegalArgumentException("模型不存在: xxx"));

        List<AgentEvent> events = collect(service.regenerate(1L, "xxx", true));

        assertEquals("error", events.get(0).getType());
        assertEquals("模型不存在: xxx", events.get(0).getData());
    }

    // ==================== 工具与提示词 ====================

    @Test
    void stream_contextCarriesPromptToolsAndHistory() {
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 1L));
        when(messageMapper.selectByConversationId(5L)).thenReturn(List.of(
                message(1L, "user", "旧问题"),
                message(2L, "assistant", "旧回答"),
                message(3L, "tool", "工具噪声")));
        givenExecution(AgentEvent.done("ok", 1));

        AgentRequest request = request(1L, "新问题");
        request.setConversationId(5L);
        request.setEnableRag(false);

        collect(service.stream(request));

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(agentExecutor).executeStream(captor.capture());
        AgentContext context = captor.getValue();
        assertEquals("你是测试助手", context.getSystemPrompt());
        // tool 角色消息必须被过滤，避免污染模型上下文
        assertEquals(2, context.getHistory().size());
        assertEquals("新问题", context.getUserMessage());
    }

    @Test
    void stream_conversationNotFound_emitsErrorEvent() {
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(conversationMapper.selectById(55L)).thenReturn(null);

        AgentRequest request = request(1L, "你好");
        request.setConversationId(55L);

        List<AgentEvent> events = collect(service.stream(request));

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
        assertTrue(String.valueOf(events.get(0).getData()).contains("会话不存在"));
        // 不能把消息写进不存在的会话（否则触发外键约束异常）
        verify(messageMapper, never()).insert(any());
        verifyNoInteractions(agentExecutor);
    }

    @Test
    void stream_conversationBelongsToAnotherAgent_emitsErrorEvent() {
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "qwen2.5:7b"));
        when(conversationMapper.selectById(55L)).thenReturn(conversation(55L, 99L));

        AgentRequest request = request(1L, "你好");
        request.setConversationId(55L);

        List<AgentEvent> events = collect(service.stream(request));

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
        assertTrue(String.valueOf(events.get(0).getData()).contains("不属于当前智能体"));
        verify(messageMapper, never()).insert(any());
        verifyNoInteractions(agentExecutor);
    }

    private AgentRequest request(Long agentId, String message) {
        AgentRequest request = new AgentRequest();
        request.setAgentId(agentId);
        request.setMessage(message);
        return request;
    }
}
