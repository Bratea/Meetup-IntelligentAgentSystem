package com.xunwu.agent.rag;

import com.xunwu.agent.entity.KnowledgeSearchResult;
import com.xunwu.agent.entity.KnowledgeSegment;
import com.xunwu.agent.mapper.KnowledgeSegmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库检索服务
 *
 * <p>基于 PostgreSQL + pgvector 的相似度检索，HNSW 索引加速，Top-K 限制结果数量。</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class KnowledgeSearchService {

    private final KnowledgeSegmentMapper knowledgeSegmentMapper;
    private final EmbeddingService embeddingService;

    public KnowledgeSearchService(KnowledgeSegmentMapper knowledgeSegmentMapper,
                                  EmbeddingService embeddingService) {
        this.knowledgeSegmentMapper = knowledgeSegmentMapper;
        this.embeddingService = embeddingService;
    }

    /**
     * 检索相关知识片段
     *
     * @param query 查询文本
     * @param topK  返回数量
     * @return 检索结果（按相似度降序）
     */
    public List<KnowledgeSearchResult> search(String query, int topK) {
        long start = System.currentTimeMillis();
        float[] vector = embeddingService.embed(query);
        String vectorString = EmbeddingService.toVectorString(vector);

        List<KnowledgeSearchResult> results = knowledgeSegmentMapper.searchSimilar(vectorString, topK);
        log.info("知识库检索完成: topK={}, cost={}ms, hits={}",
                topK, System.currentTimeMillis() - start, results.size());
        return results;
    }

    /**
     * 将检索结果组装为上下文文本
     */
    public String buildContext(List<KnowledgeSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            KnowledgeSearchResult r = results.get(i);
            sb.append("【片段 ").append(i + 1).append("】");
            if (r.getDocumentName() != null) {
                sb.append("来源: ").append(r.getDocumentName()).append('\n');
            }
            sb.append(r.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
