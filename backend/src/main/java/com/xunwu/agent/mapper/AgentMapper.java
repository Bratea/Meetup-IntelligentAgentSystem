package com.xunwu.agent.mapper;

import com.xunwu.agent.entity.Agent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 智能体 Mapper
 *
 * @author xunwu
 */
@Mapper
public interface AgentMapper {

    /** 新增智能体，返回自增主键 */
    int insert(Agent agent);

    /** 更新智能体 */
    int update(Agent agent);

    /** 根据 ID 查询 */
    Agent selectById(@Param("id") Long id);

    /** 总记录数（分页用） */
    long countAll();

    /**
     * 分页查询（按更新时间倒序）。
     *
     * <p>用 LIMIT / OFFSET 而不是全量取回再内存截断：数据量上去后，
     * 后者会把整表读进内存，且无法利用索引。</p>
     */
    List<Agent> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    /** 根据 ID 删除 */
    int deleteById(@Param("id") Long id);
}
