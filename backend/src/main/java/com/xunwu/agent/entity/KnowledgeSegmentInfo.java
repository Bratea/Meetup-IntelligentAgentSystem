package com.xunwu.agent.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 知识库分段信息（对外展示用，不含完整向量）
 *
 * <p>向量维度为 1024，完整数组字符串体积大且对前端无意义，
 * 因此这里只暴露 {@link #hasEmbedding} 标记，不返回原始向量。</p>
 *
 * @author xunwu
 */
@Data
public class KnowledgeSegmentInfo {

    /** 主键 */
    private Long id;

    /** 所属文档 */
    private Long documentId;

    /** 分段序号 */
    private Integer chunkIndex;

    /** 分段内容 */
    private String content;

    /** 是否已有向量（false 表示该分段尚未向量化或向量化失败） */
    private Boolean hasEmbedding;

    /** 创建时间 */
    private OffsetDateTime createdAt;
}
