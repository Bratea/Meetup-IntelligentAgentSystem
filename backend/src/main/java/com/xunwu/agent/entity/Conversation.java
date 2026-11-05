package com.xunwu.agent.entity;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.OffsetDateTime;

/**
 * 会话实体
 *
 * @author xunwu
 */
@Data
@Alias("Conversation")
public class Conversation {

    /** 主键 */
    private Long id;

    /** 所属智能体 */
    private Long agentId;

    /** 会话标题 */
    private String title;

    /** 状态：ACTIVE / ARCHIVED */
    private String status;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
