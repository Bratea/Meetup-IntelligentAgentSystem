package com.xunwu.agent.entity;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.OffsetDateTime;

/**
 * 消息实体
 *
 * @author xunwu
 */
@Data
@Alias("Message")
public class Message {

    /** 主键 */
    private Long id;

    /** 所属会话 */
    private Long conversationId;

    /** 角色：user / assistant / system / tool */
    private String role;

    /** 消息内容 */
    private String content;

    /** 模型标识 */
    private String model;

    /** 工具调用记录（JSON 数组） */
    private String toolCalls;

    /** 创建时间 */
    private OffsetDateTime createdAt;
}
