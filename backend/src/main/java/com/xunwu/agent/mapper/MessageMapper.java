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

    /** 根据会话查询消息（按 id 升序） */
    List<Message> selectByConversationId(@Param("conversationId") Long conversationId);

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
