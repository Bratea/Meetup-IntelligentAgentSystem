package com.xunwu.agent.entity;

import lombok.Data;

/**
 * 知识库检索结果（含相似度）
 *
 * @author xunwu
 */
@Data
public class KnowledgeSearchResult {

    /** 分段 ID */
    private Long id;

    /** 文档 ID */
    private Long documentId;

    /** 文档名称 */
    private String documentName;

    /** 分段内容 */
    private String content;

    /** 相似度（0~1，越大越相似） */
    private Double similarity;

    public KnowledgeSearchResult() {
    }

    public KnowledgeSearchResult(Long id, Long documentId, String documentName,
                                 String content, Double similarity) {
        this.id = id;
        this.documentId = documentId;
        this.documentName = documentName;
        this.content = content;
        this.similarity = similarity;
    }
}
