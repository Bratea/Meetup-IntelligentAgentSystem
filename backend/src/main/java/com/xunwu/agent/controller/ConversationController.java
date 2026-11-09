package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.entity.Conversation;
import com.xunwu.agent.mapper.ConversationMapper;
import lombok.Data;
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

    private static final int MAX_TITLE_LENGTH = 255;

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
    public ApiResponse<Conversation> create(@RequestBody ConversationRequest request) {
        if (request.getAgentId() == null) {
            throw BizException.badRequest("agentId 不能为空");
        }
        Conversation conversation = new Conversation();
        conversation.setAgentId(request.getAgentId());
        conversation.setTitle(normalizeTitle(request.getTitle()));
        conversation.setStatus(normalizeStatus(request.getStatus()));
        conversationMapper.insert(conversation);
        return ApiResponse.ok(conversation);
    }

    /** 更新会话（支持改标题 / 状态） */
    @PutMapping("/{id}")
    public ApiResponse<Conversation> update(@PathVariable Long id,
                                            @RequestBody ConversationRequest request) {
        Conversation existing = conversationMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("会话不存在: " + id);
        }
        Conversation conversation = new Conversation();
        conversation.setId(id);
        if (request.getTitle() != null) {
            conversation.setTitle(normalizeTitle(request.getTitle()));
        }
        if (request.getStatus() != null) {
            conversation.setStatus(normalizeStatus(request.getStatus()));
        }
        conversationMapper.update(conversation);
        return ApiResponse.ok(conversationMapper.selectById(id));
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (conversationMapper.selectById(id) == null) {
            throw BizException.notFound("会话不存在: " + id);
        }
        conversationMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新会话";
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw BizException.badRequest("会话标题不能超过 " + MAX_TITLE_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"ARCHIVED".equals(normalized)) {
            throw BizException.badRequest("status 只能是 ACTIVE 或 ARCHIVED");
        }
        return normalized;
    }

    // ---------- 请求体 ----------

    /**
     * 会话请求体。
     *
     * <p>使用专用 DTO 而非实体，避免客户端注入 id / createdAt / updatedAt 等内部字段。</p>
     */
    @Data
    public static class ConversationRequest {
        private Long agentId;
        private String title;
        private String status;
    }
}
