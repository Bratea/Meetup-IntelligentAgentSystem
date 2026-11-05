package com.xunwu.agent.entity;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.OffsetDateTime;

/**
 * 知识库文档实体
 *
 * @author xunwu
 */
@Data
@Alias("KnowledgeDocument")
public class KnowledgeDocument {

    /** 主键 */
    private Long id;

    /** 文档名称 */
    private String name;

    /** 文档内容 */
    private String content;

    /** 状态：PROCESSING / READY / FAILED */
    private String status;

    /** 分段数量 */
    private Integer segmentCount;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
