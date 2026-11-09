package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.common.PageQuery;
import com.xunwu.agent.common.PageResult;
import com.xunwu.agent.entity.Conversation;
import com.xunwu.agent.mapper.AgentMapper;
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
    private final AgentMapper agentMapper;

    public ConversationController(ConversationMapper conversationMapper,
                                  AgentMapper agentMapper) {
        this.conversationMapper = conversationMapper;
        this.agentMapper = agentMapper;
    }

    /** 会话列表（分页，可按智能体过滤） */
    @GetMapping
    public ApiResponse<PageResult<Conversation>> list(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageQuery query = PageQuery.of(page, size);
        if (agentId != null) {
            List<Conversation> records = conversationMapper.selectPageByAgentId(
                    agentId, query.offset(), query.getSize());
            return ApiResponse.ok(PageResult.of(
                    records, conversationMapper.countByAgentId(agentId),
                    query.getPage(), query.getSize()));
        }
        List<Conversation> records = conversationMapper.selectPageAll(
                query.offset(), query.getSize());
        return ApiResponse.ok(PageResult.of(
                records, conversationMapper.countAll(), query.getPage(), query.getSize()));
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
        // conversation.agent_id 是 NOT NULL 外键：不校验会直接触发约束异常，
        // 由全局异常处理器兑底成 400，客户端无法知道是自己的 agentId 写错了
        if (agentMapper.selectById(request.getAgentId()) == null) {
            throw BizException.notFound("智能体不存在: " + request.getAgentId());
        }
        Conversation conversation = new Conversation();
        conversation.setAgentId(request.getAgentId());
        conversation.setTitle(normalizeTitle(request.getTitle()));
        conversation.setStatus(normalizeStatus(request.getStatus()));
        conversationMapper.insert(conversation);
        return ApiResponse.ok(conversation);
    }

    /**
     * 更新会话（支持改所属智能体 / 标题 / 状态）。
     *
     * <p>agentId 与 create 走同一套校验：不校验会直接触发外键约束异常，
     * 由全局异常处理器兑底成 400，客户端无法知道是自己的 agentId 写错了。</p>
     */
    @PutMapping("/{id}")
    public ApiResponse<Conversation> update(@PathVariable Long id,
                                            @RequestBody ConversationRequest request) {
        Conversation existing = conversationMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("会话不存在: " + id);
        }
        if (request.getAgentId() != null
                && agentMapper.selectById(request.getAgentId()) == null) {
            throw BizException.notFound("智能体不存在: " + request.getAgentId());
        }
        Conversation conversation = new Conversation();
        conversation.setId(id);
        if (request.getAgentId() != null) {
            conversation.setAgentId(request.getAgentId());
        }
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
