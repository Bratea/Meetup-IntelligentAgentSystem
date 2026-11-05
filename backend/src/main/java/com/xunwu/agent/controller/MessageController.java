package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.entity.Message;
import com.xunwu.agent.mapper.MessageMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息接口
 *
 * @author xunwu
 */
@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageMapper messageMapper;

    public MessageController(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    /** 会话消息列表 */
    @GetMapping
    public ApiResponse<List<Message>> list(@PathVariable Long conversationId) {
        return ApiResponse.ok(messageMapper.selectByConversationId(conversationId));
    }

    /** 删除消息 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long conversationId, @PathVariable Long id) {
        Message message = messageMapper.selectById(id);
        if (message == null || !conversationId.equals(message.getConversationId())) {
            throw BizException.notFound("消息不存在: " + id);
        }
        messageMapper.deleteById(id);
        return ApiResponse.ok();
    }
}
