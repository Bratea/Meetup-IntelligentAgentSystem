package com.xunwu.agent.mapper;

import com.xunwu.agent.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息 Mapper
 *
 * @author xunwu
 */
@Mapper
public interface MessageMapper {

    /** 新增消息，返回自增主键 */
    int insert(Message message);

    /** 根据会话查询消息（按 id 升序，供对话执行时回溯历史） */
    List<Message> selectByConversationId(@Param("conversationId") Long conversationId);

    /** 统计会话消息数（分页用） */
    long countByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 分页查询会话消息（按 id 倒序，即最新一页）。
     *
     * <p>会话消息按「最新一页」翻页：page=1 取最新的 size 条，page=2 取更早的。
     * 调用方需自行翻转为时间升序 —— 打开会话时用户关心的是最近的消息。</p>
     */
    List<Message> selectPageByConversationId(@Param("conversationId") Long conversationId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    /** 根据 ID 查询 */
    Message selectById(@Param("id") Long id);

    /** 根据 ID 删除 */
    int deleteById(@Param("id") Long id);

    /**
     * 删除指定消息之后的全部消息（同会话内）。
     *
     * <p>用于「重新生成」：保留触发重新生成的用户消息，清掉它之后的旧助手回复。</p>
     */
    int deleteAfterId(@Param("conversationId") Long conversationId,
                      @Param("messageId") Long messageId);
}
