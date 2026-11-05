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
}
