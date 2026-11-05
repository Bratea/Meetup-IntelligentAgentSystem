package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.entity.Conversation;
import com.xunwu.agent.mapper.ConversationMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理接口
 *
 * @author xunwu
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationMapper conversationMapper;

    public ConversationController(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    /** 会话列表 */
    @GetMapping
    public ApiResponse<List<Conversation>> list(@RequestParam(required = false) Long agentId) {
        if (agentId != null) {
            return ApiResponse.ok(conversationMapper.selectByAgentId(agentId));
        }
        return ApiResponse.ok(conversationMapper.selectAll());
    }

    /** 会话详情 */
    @GetMapping("/{id}")
    public ApiResponse<Conversation> detail(@PathVariable Long id) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw BizException.notFound("会话不存在: " + id);
        }
        return ApiResponse.ok(conversation);
    }

    /** 创建会话 */
    @PostMapping
    public ApiResponse<Conversation> create(@RequestBody Conversation conversation) {
        if (conversation.getAgentId() == null) {
            throw BizException.badRequest("agentId 不能为空");
        }
        if (conversation.getTitle() == null) {
            conversation.setTitle("新会话");
        }
        if (conversation.getStatus() == null) {
            conversation.setStatus("ACTIVE");
        }
        conversationMapper.insert(conversation);
        return ApiResponse.ok(conversation);
    }

    /** 更新会话 */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody Conversation conversation) {
        conversation.setId(id);
        conversationMapper.update(conversation);
        return ApiResponse.ok();
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        conversationMapper.deleteById(id);
        return ApiResponse.ok();
    }
}
