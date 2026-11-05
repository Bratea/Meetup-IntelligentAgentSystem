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
     * <p>客户端使用 EventSource 或 fetch stream 接收：</p>
     * <pre>
     *   const es = new EventSource('/api/chat/stream?agentId=1&message=你好');
     *   es.onmessage = e => console.log(JSON.parse(e.data));
     * </pre>
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> stream(@ModelAttribute AgentRequest request) {
        return chatService.stream(request);
    }
}
