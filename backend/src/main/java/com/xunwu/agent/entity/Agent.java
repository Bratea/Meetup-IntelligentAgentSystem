package com.xunwu.agent.entity;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.OffsetDateTime;

/**
 * 智能体实体
 *
 * @author xunwu
 */
@Data
@Alias("Agent")
public class Agent {

    /** 主键 */
    private Long id;

    /** 智能体名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    /** 头像 */
    private String avatar;

    /** 默认模型标识 */
    private String modelId;

    /** 启用的工具名称列表（JSON 数组） */
    private String toolIds;

    /** 最大执行步数 */
    private Integer maxSteps;

    /** 状态：ACTIVE / INACTIVE */
    private String status;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
