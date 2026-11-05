package com.xunwu.agent.chat;

import com.xunwu.agent.agent.AgentEvent;
import com.xunwu.agent.agent.AgentRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 对话接口（SSE 流式）
 *
 * @author xunwu
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * SSE 流式对话
     *
     * <p>使用 GET + query 参数，客户端通过 {@code fetch} 读取流（EventSource 不支持自定义
     * header，且需要传递智能体/会话/模型等参数，故统一用 fetch stream）：</p>
     * <pre>
     *   const res = await fetch('/api/chat/stream?agentId=1&message=你好');
     *   const reader = res.body.getReader();
     *   // 逐块解析 "data: {...}" 事件
     * </pre>
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> stream(@ModelAttribute AgentRequest request) {
        return chatService.stream(request);
    }
}
