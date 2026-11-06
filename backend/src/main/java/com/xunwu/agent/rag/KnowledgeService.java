package com.xunwu.agent.rag;

import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.KnowledgeDocument;
import com.xunwu.agent.entity.KnowledgeSegment;
import com.xunwu.agent.mapper.KnowledgeDocumentMapper;
import com.xunwu.agent.mapper.KnowledgeSegmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务：文档入库、分段、向量化
 *
 * <p><b>事务边界说明：</b>向量化需要调用 Ollama（远程 HTTP，秒级耗时），
 * 因此 {@link #processDocument} 故意<b>不</b>加 {@code @Transactional}：
 * 长事务会占满数据库连接，且失败回滚会把已写入的状态一并丢弃。
 * 真正需要原子的"批量插入分段 + 更新文档状态"由 {@link TransactionTemplate} 显式包裹。</p>
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
    private final TransactionTemplate transactionTemplate;

    public KnowledgeService(KnowledgeDocumentMapper documentMapper,
                            KnowledgeSegmentMapper segmentMapper,
                            DocumentSplitter documentSplitter,
                            EmbeddingService embeddingService,
                            XunwuProperties properties,
                            TransactionTemplate transactionTemplate) {
        this.documentMapper = documentMapper;
        this.segmentMapper = segmentMapper;
        this.documentSplitter = documentSplitter;
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 新增文档（状态 PROCESSING），由上层异步触发向量化
     */
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
     *
     * <p>失败时不抛异常，而是把文档置为 FAILED 并保留原因日志，
     * 由前端轮询接口展示真实状态。</p>
     */
    public void processDocument(Long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            log.warn("文档不存在，跳过处理: documentId={}", documentId);
            return;
        }

        try {
            // 1. 分段（纯本地计算）
            List<String> chunks = documentSplitter.split(document.getContent());
            if (chunks.isEmpty()) {
                markFailed(documentId, "文档内容为空，无可分段内容");
                return;
            }

            // 2. 向量化（远程 HTTP，不放在事务里）
            List<float[]> vectors = embeddingService.embed(chunks);

            // 3. 组装分段
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

            // 4. 入库（原子操作）
            transactionTemplate.executeWithoutResult(status -> {
                segmentMapper.batchInsert(segments);
                documentMapper.updateSegmentCount(documentId, segments.size());
                updateStatus(documentId, "READY");
            });

            log.info("文档处理完成: documentId={}, chunks={}", documentId, segments.size());
        } catch (Exception e) {
            markFailed(documentId, "处理失败: " + rootMessage(e));
        }
    }

    /**
     * 删除文档（级联删除分段）
     */
    public void deleteDocument(Long documentId) {
        transactionTemplate.executeWithoutResult(status -> {
            segmentMapper.deleteByDocumentId(documentId);
            documentMapper.deleteById(documentId);
        });
    }

    /**
     * 把文档标记为 FAILED。
     * 单独执行，不参与调用方的事务，确保失败原因不会被回滚掉。
     */
    private void markFailed(Long documentId, String reason) {
        log.warn("文档处理失败: documentId={}, reason={}", documentId, reason);
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus("FAILED");
        // 失败原因落库，前端直接展示，避免用户只看到一个没有下文的 FAILED
        update.setErrorMessage(reason.length() > 500 ? reason.substring(0, 500) : reason);
        transactionTemplate.executeWithoutResult(status -> documentMapper.update(update));
    }

    private void updateStatus(Long documentId, String status) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(documentId);
        update.setStatus(status);
        documentMapper.update(update);
    }

    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : e.getClass().getSimpleName();
    }
}
