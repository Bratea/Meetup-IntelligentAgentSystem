package com.xunwu.agent.entity;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.OffsetDateTime;

/**
 * 知识库分段实体（含向量）
 *
 * @author xunwu
 */
@Data
@Alias("KnowledgeSegment")
public class KnowledgeSegment {

    /** 主键 */
    private Long id;

    /** 所属文档 */
    private Long documentId;

    /** 分段序号 */
    private Integer chunkIndex;

    /** 分段内容 */
    private String content;

    /** 向量数据（数组字符串，如 [0.1, 0.2, ...]） */
    private String embedding;

    /** 元数据（JSON） */
    private String metadata;

    /** 创建时间 */
    private OffsetDateTime createdAt;
}
