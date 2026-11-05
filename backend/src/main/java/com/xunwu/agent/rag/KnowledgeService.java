package com.xunwu.agent.rag;

import com.xunwu.agent.common.BizException;
import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.KnowledgeDocument;
import com.xunwu.agent.entity.KnowledgeSegment;
import com.xunwu.agent.mapper.KnowledgeDocumentMapper;
import com.xunwu.agent.mapper.KnowledgeSegmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务：文档入库、分段、向量化
 *
 * @author xunwu
 */
@Slf4j
@Service
public class KnowledgeService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeSegmentMapper segmentMapper;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingService embeddingService;
    private final XunwuProperties properties;

    public KnowledgeService(KnowledgeDocumentMapper documentMapper,
                            KnowledgeSegmentMapper segmentMapper,
                            DocumentSplitter documentSplitter,
                            EmbeddingService embeddingService,
                            XunwuProperties properties) {
        this.documentMapper = documentMapper;
        this.segmentMapper = segmentMapper;
        this.documentSplitter = documentSplitter;
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    /**
     * 新增文档并异步向量化
     */
    @Transactional
    public KnowledgeDocument createDocument(String name, String content) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setName(name);
        document.setContent(content);
        document.setStatus("PROCESSING");
        document.setSegmentCount(0);
        documentMapper.insert(document);
        return document;
    }

    /**
     * 文档分段并向量化入库
     */
    @Transactional
    public void processDocument(Long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw BizException.notFound("文档不存在: " + documentId);
        }

        // 1. 分段
        List<String> chunks = documentSplitter.split(document.getContent());
        if (chunks.isEmpty()) {
            documentMapper.updateSegmentCount(documentId, 0);
            updateStatus(documentId, "FAILED");
            return;
        }

        // 2. 向量化
        List<float[]> vectors;
        try {
            vectors = embeddingService.embed(chunks);
        } catch (Exception e) {
            log.error("文档向量化失败: documentId={}", documentId, e);
            updateStatus(documentId, "FAILED");
            throw new BizException(500, "文档向量化失败: " + e.getMessage());
        }

        // 3. 入库
        List<KnowledgeSegment> segments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeSegment segment = new KnowledgeSegment();
            segment.setDocumentId(documentId);
            segment.setChunkIndex(i);
            segment.setContent(chunks.get(i));
            segment.setEmbedding(EmbeddingService.toVectorString(vectors.get(i)));
            segment.setMetadata(null);
            segments.add(segment);
        }
        segmentMapper.batchInsert(segments);
        documentMapper.updateSegmentCount(documentId, segments.size());
        updateStatus(documentId, "READY");
        log.info("文档处理完成: documentId={}, chunks={}", documentId, segments.size());
    }

    /**
     * 删除文档（级联删除分段）
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        segmentMapper.deleteByDocumentId(documentId);
        documentMapper.deleteById(documentId);
    }

    private void updateStatus(Long documentId, String status) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus(status);
        documentMapper.update(update);
    }
}
