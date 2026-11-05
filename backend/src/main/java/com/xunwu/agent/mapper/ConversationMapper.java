package com.xunwu.agent.mapper;

import com.xunwu.agent.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话 Mapper
 *
 * @author xunwu
 */
@Mapper
public interface ConversationMapper {

    /** 新增会话，返回自增主键 */
    int insert(Conversation conversation);

    /** 更新会话 */
    int update(Conversation conversation);

    /** 根据 ID 查询 */
    Conversation selectById(@Param("id") Long id);

    /** 按智能体查询会话列表 */
    List<Conversation> selectByAgentId(@Param("agentId") Long agentId);

    /** 查询全部会话 */
    List<Conversation> selectAll();

    /** 根据 ID 删除 */
    int deleteById(@Param("id") Long id);
}
