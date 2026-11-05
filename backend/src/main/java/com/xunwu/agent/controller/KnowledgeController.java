package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.KnowledgeDocument;
import com.xunwu.agent.entity.KnowledgeSearchResult;
import com.xunwu.agent.mapper.KnowledgeDocumentMapper;
import com.xunwu.agent.rag.KnowledgeSearchService;
import com.xunwu.agent.rag.KnowledgeService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库接口
 *
 * @author xunwu
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeSearchService knowledgeSearchService;
    private final KnowledgeDocumentMapper documentMapper;
    private final XunwuProperties properties;

    public KnowledgeController(KnowledgeService knowledgeService,
                               KnowledgeSearchService knowledgeSearchService,
                               KnowledgeDocumentMapper documentMapper,
                               XunwuProperties properties) {
        this.knowledgeService = knowledgeService;
        this.knowledgeSearchService = knowledgeSearchService;
        this.documentMapper = documentMapper;
        this.properties = properties;
    }

    /** 文档列表 */
    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocument>> listDocuments() {
        return ApiResponse.ok(documentMapper.selectAll());
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
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw BizException.badRequest("文档内容不能为空");
        }
        KnowledgeDocument document = knowledgeService.createDocument(
                request.getName(), request.getContent());
        // 异步向量化，避免阻塞请求
        new Thread(() -> knowledgeService.processDocument(document.getId()),
                "xunwu-rag-" + document.getId()).start();
        return ApiResponse.ok(document);
    }

    /** 删除文档 */
    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return ApiResponse.ok();
    }

    /** 知识库检索测试 */
    @PostMapping("/search")
    public ApiResponse<List<KnowledgeSearchResult>> search(@RequestBody SearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw BizException.badRequest("查询内容不能为空");
        }
        int topK = request.getTopK() != null ? request.getTopK() : properties.getRag().getTopK();
        return ApiResponse.ok(knowledgeSearchService.search(request.getQuery(), topK));
    }

    /** 将检索结果组装为上下文 */
    @PostMapping("/search/context")
    public ApiResponse<String> searchContext(@RequestBody SearchRequest request) {
        int topK = request.getTopK() != null ? request.getTopK() : properties.getRag().getTopK();
        List<KnowledgeSearchResult> results = knowledgeSearchService.search(request.getQuery(), topK);
        return ApiResponse.ok(knowledgeSearchService.buildContext(results));
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
}
