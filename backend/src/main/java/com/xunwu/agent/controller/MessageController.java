package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.common.PageQuery;
import com.xunwu.agent.common.PageResult;
import com.xunwu.agent.entity.Message;
import com.xunwu.agent.mapper.MessageMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * 会话消息列表（分页）。
     *
     * <p>按「最新一页」翻页：page=1 是最新的 size 条，page=2 是更早的。
     * 返回的记录仍按时间升序排列，前端可直接渲染。</p>
     */
    @GetMapping
    public ApiResponse<PageResult<Message>> list(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageQuery query = PageQuery.of(page, size, 200, 500);
        long total = messageMapper.countByConversationId(conversationId);
        List<Message> records = new ArrayList<>(messageMapper.selectPageByConversationId(
                conversationId, query.offset(), query.getSize()));
        // SQL 倒序取最新一页，翻转为时间升序返回
        Collections.reverse(records);
        return ApiResponse.ok(PageResult.of(
                records, total, query.getPage(), query.getSize()));
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
