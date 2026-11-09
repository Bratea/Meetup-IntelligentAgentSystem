package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.KnowledgeDocument;
import com.xunwu.agent.entity.KnowledgeSearchResult;
import com.xunwu.agent.entity.KnowledgeSegmentInfo;
import com.xunwu.agent.mapper.KnowledgeDocumentMapper;
import com.xunwu.agent.rag.KnowledgeSearchService;
import com.xunwu.agent.rag.KnowledgeService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 知识库接口
 *
 * @author xunwu
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    /** 单个文档最大字符数，避免超长文档把内存和 Ollama 拖垮 */
    private static final int MAX_CONTENT_LENGTH = 200_000;

    private final KnowledgeService knowledgeService;
    private final KnowledgeSearchService knowledgeSearchService;
    private final KnowledgeDocumentMapper documentMapper;
    private final XunwuProperties properties;
    private final Executor taskExecutor;

    public KnowledgeController(KnowledgeService knowledgeService,
                               KnowledgeSearchService knowledgeSearchService,
                               KnowledgeDocumentMapper documentMapper,
                               XunwuProperties properties,
                               @Qualifier("taskExecutor") Executor taskExecutor) {
        this.knowledgeService = knowledgeService;
        this.knowledgeSearchService = knowledgeSearchService;
        this.documentMapper = documentMapper;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
    }

    /** 文档列表 */
    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocument>> listDocuments() {
        return ApiResponse.ok(documentMapper.selectSummaryList());
    }

    /** 文档详情 */
    @GetMapping("/documents/{id}")
    public ApiResponse<KnowledgeDocument> getDocument(@PathVariable Long id) {
        KnowledgeDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw BizException.notFound("文档不存在: " + id);
        }
        return ApiResponse.ok(document);
    }

    /** 上传文档（异步向量化） */
    @PostMapping("/documents")
    public ApiResponse<KnowledgeDocument> upload(@RequestBody DocumentRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw BizException.badRequest("文档名称不能为空");
        }
        if (request.getName().length() > 100) {
            throw BizException.badRequest("文档名称不能超过 100 个字符");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw BizException.badRequest("文档内容不能为空");
        }
        if (request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw BizException.badRequest("文档内容过长，单次最多 " + MAX_CONTENT_LENGTH + " 字符");
        }

        KnowledgeDocument document = knowledgeService.createDocument(
                request.getName(), request.getContent());
        Long documentId = document.getId();
        // 复用托管线程池，异步向量化，避免阻塞 HTTP 请求
        taskExecutor.execute(() -> {
            try {
                knowledgeService.processDocument(documentId);
            } catch (Exception e) {
                log.error("文档向量化任务异常: documentId={}", documentId, e);
            }
        });
        return ApiResponse.ok(document);
    }

    /** 删除文档 */
    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return ApiResponse.ok();
    }

    /** 文档分段明细 */
    @GetMapping("/documents/{id}/segments")
    public ApiResponse<List<KnowledgeSegmentInfo>> listSegments(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.listSegments(id));
    }

    /** 重新向量化整个文档（异步） */
    @PostMapping("/documents/{id}/reprocess")
    public ApiResponse<Void> reprocess(@PathVariable Long id) {
        knowledgeService.reprocessDocument(id);
        taskExecutor.execute(() -> {
            try {
                knowledgeService.processDocument(id);
            } catch (Exception e) {
                log.error("文档重新向量化任务异常: documentId={}", id, e);
            }
        });
        return ApiResponse.ok();
    }

    /** 编辑分段内容并重新向量化 */
    @PutMapping("/segments/{id}")
    public ApiResponse<Void> updateSegment(@PathVariable Long id,
                                           @RequestBody SegmentRequest request) {
        knowledgeService.updateSegment(id, request.getContent());
        return ApiResponse.ok();
    }

    /** 删除单个分段 */
    @DeleteMapping("/segments/{id}")
    public ApiResponse<Void> deleteSegment(@PathVariable Long id) {
        knowledgeService.deleteSegment(id);
        return ApiResponse.ok();
    }

    /** 知识库检索测试 */
    @PostMapping("/search")
    public ApiResponse<List<KnowledgeSearchResult>> search(@RequestBody SearchRequest request) {
        validateQuery(request.getQuery());
        int topK = normalizeTopK(request.getTopK());
        return ApiResponse.ok(knowledgeSearchService.search(request.getQuery(), topK));
    }

    /** 将检索结果组装为上下文 */
    @PostMapping("/search/context")
    public ApiResponse<String> searchContext(@RequestBody SearchRequest request) {
        validateQuery(request.getQuery());
        int topK = normalizeTopK(request.getTopK());
        List<KnowledgeSearchResult> results =
                knowledgeSearchService.search(request.getQuery(), topK);
        return ApiResponse.ok(knowledgeSearchService.buildContext(results));
    }

    /**
     * 检索词不能为空：否则 {@code embeddingService.embed(null)} 会直接抛异常，
     * 由全局异常处理器兜底成 500，错误信息也不可读。
     */
    private void validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw BizException.badRequest("查询内容不能为空");
        }
    }

    private int normalizeTopK(Integer topK) {
        int configured = topK != null ? topK : properties.getRag().getTopK();
        return Math.max(1, Math.min(configured, 20));
    }

    // ---------- 请求体 ----------

    @Data
    public static class DocumentRequest {
        private String name;
        private String content;
    }

    @Data
    public static class SearchRequest {
        private String query;
        private Integer topK;
    }

    @Data
    public static class SegmentRequest {
        private String content;
    }
}
