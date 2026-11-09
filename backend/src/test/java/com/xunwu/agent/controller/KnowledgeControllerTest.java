package com.xunwu.agent.controller;

import com.xunwu.agent.common.BizException;
import com.xunwu.agent.common.GlobalExceptionHandler;
import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.KnowledgeDocument;
import com.xunwu.agent.entity.KnowledgeSearchResult;
import com.xunwu.agent.entity.KnowledgeSegmentInfo;
import com.xunwu.agent.mapper.KnowledgeDocumentMapper;
import com.xunwu.agent.rag.KnowledgeSearchService;
import com.xunwu.agent.rag.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link KnowledgeController} 单元测试。
 *
 * <p>覆盖 P14 新增的分段明细 / 重新向量化 / 分段编辑 / 分段删除四个端点，
 * 以及入参校验与异步任务提交。</p>
 *
 * @author xunwu
 */
class KnowledgeControllerTest {

    private KnowledgeService knowledgeService;
    private KnowledgeSearchService searchService;
    private KnowledgeDocumentMapper documentMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        knowledgeService = mock(KnowledgeService.class);
        searchService = mock(KnowledgeSearchService.class);
        documentMapper = mock(KnowledgeDocumentMapper.class);
        // 同步执行异步任务，便于断言提交行为
        Executor directExecutor = Runnable::run;

        mockMvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeController(
                        knowledgeService, searchService, documentMapper,
                        new XunwuProperties(), directExecutor))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 文档 ====================

    @Test
    void upload_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"正文\"}"))
                .andExpect(status().isBadRequest());

        verify(knowledgeService, never()).createDocument(any(), any());
    }

    @Test
    void upload_oversizedName_returns400() throws Exception {
        mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(101) + "\",\"content\":\"正文\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_blankContent_returns400() throws Exception {
        mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"文档\",\"content\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_oversizedContent_returns400() throws Exception {
        mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"文档\",\"content\":\"" + "x".repeat(200_001) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(knowledgeService);
    }

    @Test
    void upload_valid_createsDocumentAndSubmitsAsyncTask() throws Exception {
        KnowledgeDocument created = new KnowledgeDocument();
        created.setId(1L);
        created.setName("文档");
        created.setStatus("PROCESSING");
        when(knowledgeService.createDocument("文档", "正文")).thenReturn(created);

        mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"文档\",\"content\":\"正文\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        verify(knowledgeService).createDocument("文档", "正文");
        // 异步任务最终会调用处理逻辑
        verify(knowledgeService).processDocument(1L);
    }

    @Test
    void getDocument_unknown_returns404() throws Exception {
        when(documentMapper.selectById(9L)).thenReturn(null);

        mockMvc.perform(get("/api/knowledge/documents/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void deleteDocument_delegatesToService() throws Exception {
        mockMvc.perform(delete("/api/knowledge/documents/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(knowledgeService).deleteDocument(3L);
    }

    // ==================== 分段 ====================

    @Test
    void listSegments_returnsServiceResult() throws Exception {
        KnowledgeSegmentInfo info = new KnowledgeSegmentInfo();
        info.setId(10L);
        info.setDocumentId(1L);
        info.setChunkIndex(0);
        info.setContent("分段内容");
        info.setHasEmbedding(true);
        when(knowledgeService.listSegments(1L)).thenReturn(List.of(info));

        mockMvc.perform(get("/api/knowledge/documents/1/segments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].content").value("分段内容"))
                .andExpect(jsonPath("$.data[0].hasEmbedding").value(true));
    }

    @Test
    void listSegments_unknownDocument_returns404() throws Exception {
        when(knowledgeService.listSegments(9L))
                .thenThrow(BizException.notFound("文档不存在: 9"));

        mockMvc.perform(get("/api/knowledge/documents/9/segments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("文档不存在: 9"));
    }

    @Test
    void reprocess_resetsThenSubmitsAsyncTask() throws Exception {
        mockMvc.perform(post("/api/knowledge/documents/1/reprocess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        var inOrder = inOrder(knowledgeService);
        inOrder.verify(knowledgeService).reprocessDocument(1L);
        inOrder.verify(knowledgeService).processDocument(1L);
    }

    @Test
    void reprocess_unknownDocument_returns404() throws Exception {
        doThrow(BizException.notFound("文档不存在: 9"))
                .when(knowledgeService).reprocessDocument(9L);

        mockMvc.perform(post("/api/knowledge/documents/9/reprocess"))
                .andExpect(status().isNotFound());

        verify(knowledgeService, never()).processDocument(any());
    }

    @Test
    void updateSegment_passesContentToService() throws Exception {
        mockMvc.perform(put("/api/knowledge/segments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"新内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(knowledgeService).updateSegment(10L, "新内容");
    }

    @Test
    void updateSegment_serviceRejectsBlank_returns400() throws Exception {
        doThrow(BizException.badRequest("分段内容不能为空"))
                .when(knowledgeService).updateSegment(10L, "  ");

        mockMvc.perform(put("/api/knowledge/segments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("分段内容不能为空"));
    }

    @Test
    void updateSegment_unknownSegment_returns404() throws Exception {
        doThrow(BizException.notFound("分段不存在: 10"))
                .when(knowledgeService).updateSegment(10L, "内容");

        mockMvc.perform(put("/api/knowledge/segments/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"内容\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSegment_delegatesToService() throws Exception {
        mockMvc.perform(delete("/api/knowledge/segments/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(knowledgeService).deleteSegment(10L);
    }

    // ==================== 检索 ====================

    @Test
    void search_clampsTopKToBounds() throws Exception {
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of());

        // 0 → 收敛到 1
        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"测试\",\"topK\":0}"))
                .andExpect(status().isOk());
        verify(searchService).search("测试", 1);

        // 999 → 收敛到 20
        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"测试\",\"topK\":999}"))
                .andExpect(status().isOk());
        verify(searchService).search("测试", 20);
    }

    @Test
    void search_nullTopK_usesConfiguredDefault() throws Exception {
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"测试\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(searchService).search(eq("测试"), captor.capture());
        assertEquals(new XunwuProperties().getRag().getTopK(), captor.getValue());
    }

    @Test
    void searchContext_returnsBuiltContext() throws Exception {
        KnowledgeSearchResult result = new KnowledgeSearchResult();
        result.setId(1L);
        result.setDocumentId(1L);
        result.setDocumentName("文档");
        result.setContent("内容");
        result.setSimilarity(0.9);
        when(searchService.search("测试", 4)).thenReturn(List.of(result));
        when(searchService.buildContext(anyList())).thenReturn("组装后的上下文");

        mockMvc.perform(post("/api/knowledge/search/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("组装后的上下文"));
    }
}
