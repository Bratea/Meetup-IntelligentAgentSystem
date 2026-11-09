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

    /** 总记录数（分页用） */
    long countAll();

    /** 按智能体统计会话数（分页用） */
    long countByAgentId(@Param("agentId") Long agentId);

    /** 分页查询全部会话（按更新时间倒序） */
    List<Conversation> selectPageAll(@Param("offset") int offset,
                                    @Param("limit") int limit);

    /** 分页查询指定智能体的会话（按更新时间倒序） */
    List<Conversation> selectPageByAgentId(@Param("agentId") Long agentId,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 根据 ID 删除 */
    int deleteById(@Param("id") Long id);
}
