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

    /** 查询全部（按更新时间倒序） */
    List<Agent> selectAll();

    /** 根据 ID 删除 */
    int deleteById(@Param("id") Long id);
}
