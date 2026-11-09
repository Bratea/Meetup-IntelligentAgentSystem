package com.xunwu.agent.rag;

import com.xunwu.agent.common.BizException;
import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.KnowledgeDocument;
import com.xunwu.agent.entity.KnowledgeSegment;
import com.xunwu.agent.entity.KnowledgeSegmentInfo;
import com.xunwu.agent.mapper.KnowledgeDocumentMapper;
import com.xunwu.agent.mapper.KnowledgeSegmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link KnowledgeService} 单元测试。
 *
 * <p>覆盖 P14 新增的分段明细 / 重新向量化 / 分段编辑 / 分段删除，
 * 以及向量化失败时状态落库的原子性。</p>
 *
 * @author xunwu
 */
class KnowledgeServiceTest {

    private KnowledgeDocumentMapper documentMapper;
    private KnowledgeSegmentMapper segmentMapper;
    private DocumentSplitter documentSplitter;
    private EmbeddingService embeddingService;
    private TransactionTemplate transactionTemplate;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        documentMapper = mock(KnowledgeDocumentMapper.class);
        segmentMapper = mock(KnowledgeSegmentMapper.class);
        documentSplitter = mock(DocumentSplitter.class);
        embeddingService = mock(EmbeddingService.class);
        transactionTemplate = mock(TransactionTemplate.class);

        // executeWithoutResult 直接执行回调，等价于"没有事务"的本地行为
        doAnswer(invocation -> {
            Consumer<org.springframework.transaction.TransactionStatus> action =
                    invocation.getArgument(0);
            action.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new KnowledgeService(documentMapper, segmentMapper, documentSplitter,
                embeddingService, new XunwuProperties(), transactionTemplate);
    }

    private KnowledgeDocument document(Long id, String status) {
        KnowledgeDocument d = new KnowledgeDocument();
        d.setId(id);
        d.setName("文档");
        d.setContent("正文");
        d.setStatus(status);
        d.setSegmentCount(2);
        return d;
    }

    private KnowledgeSegment segment(Long id, Long documentId, int index,
                                     String content, String embedding) {
        KnowledgeSegment s = new KnowledgeSegment();
        s.setId(id);
        s.setDocumentId(documentId);
        s.setChunkIndex(index);
        s.setContent(content);
        s.setEmbedding(embedding);
        return s;
    }

    // ==================== createDocument ====================

    @Test
    void createDocument_marksProcessingWithZeroSegments() {
        KnowledgeDocument created = service.createDocument("新文档", "正文");

        ArgumentCaptor<KnowledgeDocument> captor =
                ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentMapper).insert(captor.capture());
        assertEquals("新文档", captor.getValue().getName());
        assertEquals("正文", captor.getValue().getContent());
        assertEquals("PROCESSING", captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getSegmentCount());
        assertEquals("PROCESSING", created.getStatus());
    }

    // ==================== processDocument ====================

    @Test
    void processDocument_splitsEmbedsAndPersists() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, "PROCESSING"));
        when(documentSplitter.split("正文")).thenReturn(List.of("段一", "段二"));
        when(embeddingService.embed(List.of("段一", "段二")))
                .thenReturn(List.of(new float[]{0.1f}, new float[]{0.2f}));

        service.processDocument(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(segmentMapper).batchInsert(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(0, captor.getValue().get(0).getChunkIndex());
        assertEquals(1, captor.getValue().get(1).getChunkIndex());
        // 向量已转成 PostgreSQL 数组字符串
        assertEquals("[0.1]", captor.getValue().get(0).getEmbedding());
        assertEquals("[0.2]", captor.getValue().get(1).getEmbedding());

        verify(documentMapper).updateSegmentCount(1L, 2);
        verify(documentMapper).update(argThat(d ->
                "READY".equals(d.getStatus()) && d.getId().equals(1L)));
    }

    @Test
    void processDocument_emptyChunks_marksFailedWithoutInsert() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, "PROCESSING"));
        when(documentSplitter.split("正文")).thenReturn(List.of());

        service.processDocument(1L);

        verify(segmentMapper, never()).batchInsert(any());
        verify(documentMapper).update(argThat(d ->
                "FAILED".equals(d.getStatus())
                        && d.getErrorMessage() != null
                        && d.getErrorMessage().contains("无可分段内容")));
    }

    @Test
    void processDocument_embeddingFailure_marksFailedAndKeepsReason() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, "PROCESSING"));
        when(documentSplitter.split("正文")).thenReturn(List.of("段一"));
        when(embeddingService.embed(anyList()))
                .thenThrow(BizException.serviceUnavailable("向量化模型 bge-m3 未安装"));

        service.processDocument(1L);

        // 失败原因必须落库，且不受后续事务影响
        verify(documentMapper).update(argThat(d ->
                "FAILED".equals(d.getStatus())
                        && d.getErrorMessage().contains("bge-m3")));
        verify(segmentMapper, never()).batchInsert(any());
    }

    @Test
    void processDocument_missingDocument_doesNothing() {
        when(documentMapper.selectById(1L)).thenReturn(null);

        service.processDocument(1L);

        verifyNoInteractions(documentSplitter, embeddingService, segmentMapper);
    }

    // ==================== listSegments ====================

    @Test
    void listSegments_mapsToInfoWithoutRawVector() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, "READY"));
        when(segmentMapper.selectByDocumentId(1L)).thenReturn(List.of(
                segment(10L, 1L, 0, "有向量", "[0.1,0.2]"),
                segment(11L, 1L, 1, "无向量", null),
                segment(12L, 1L, 2, "空向量", "   ")));

        List<KnowledgeSegmentInfo> infos = service.listSegments(1L);

        assertEquals(3, infos.size());
        assertEquals(10L, infos.get(0).getId());
        assertEquals(0, infos.get(0).getChunkIndex());
        assertTrue(infos.get(0).getHasEmbedding());
        assertFalse(infos.get(1).getHasEmbedding());
        assertFalse(infos.get(2).getHasEmbedding());
    }

    @Test
    void listSegments_unknownDocument_throwsNotFound() {
        when(documentMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.listSegments(9L));
        assertEquals(404, ex.getCode());
        verify(segmentMapper, never()).selectByDocumentId(any());
    }

    // ==================== reprocessDocument ====================

    @Test
    void reprocessDocument_clearsSegmentsAndResetsStatus() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, "READY"));

        service.reprocessDocument(1L);

        verify(segmentMapper).deleteByDocumentId(1L);
        verify(documentMapper).updateSegmentCount(1L, 0);
        verify(documentMapper).update(argThat(d ->
                "PROCESSING".equals(d.getStatus()) && d.getId().equals(1L)));
    }

    @Test
    void reprocessDocument_unknown_throwsNotFound() {
        when(documentMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.reprocessDocument(9L));
        assertEquals(404, ex.getCode());
    }

    // ==================== deleteSegment ====================

    @Test
    void deleteSegment_removesAndRecounts() {
        when(segmentMapper.selectById(10L)).thenReturn(segment(10L, 1L, 0, "段一", "[0.1]"));
        when(segmentMapper.countByDocumentId(1L)).thenReturn(3L);

        service.deleteSegment(10L);

        verify(segmentMapper).deleteById(10L);
        verify(documentMapper).updateSegmentCount(1L, 3);
    }

    @Test
    void deleteSegment_unknown_throwsNotFound() {
        when(segmentMapper.selectById(10L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.deleteSegment(10L));
        assertEquals(404, ex.getCode());
        verify(segmentMapper, never()).deleteById(any());
    }

    // ==================== updateSegment ====================

    @Test
    void updateSegment_persistsContentAndNewVector() {
        when(segmentMapper.selectById(10L)).thenReturn(segment(10L, 1L, 0, "旧内容", "[0.1]"));
        when(embeddingService.embed("新内容")).thenReturn(new float[]{0.9f});

        service.updateSegment(10L, "新内容");

        verify(segmentMapper).updateContent(10L, "新内容", "[0.9]");
    }

    @Test
    void updateSegment_blankContent_throwsBadRequest() {
        BizException ex = assertThrows(BizException.class,
                () -> service.updateSegment(10L, "   "));
        assertEquals(400, ex.getCode());
        verify(segmentMapper, never()).updateContent(any(), any(), any());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void updateSegment_unknown_throwsNotFound() {
        when(segmentMapper.selectById(10L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.updateSegment(10L, "新内容"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void updateSegment_embeddingFailure_doesNotUpdateContent() {
        when(segmentMapper.selectById(10L)).thenReturn(segment(10L, 1L, 0, "旧内容", "[0.1]"));
        when(embeddingService.embed("新内容"))
                .thenThrow(BizException.serviceUnavailable("向量化服务暂时不可用"));

        assertThrows(BizException.class, () -> service.updateSegment(10L, "新内容"));

        // 向量化在事务外先失败，内容不会被改成"没有向量的新内容"
        verify(segmentMapper, never()).updateContent(any(), any(), any());
    }

    // ==================== deleteDocument ====================

    @Test
    void deleteDocument_cascadesSegmentsFirst() {
        service.deleteDocument(1L);

        var inOrder = inOrder(segmentMapper, documentMapper);
        inOrder.verify(segmentMapper).deleteByDocumentId(1L);
        inOrder.verify(documentMapper).deleteById(1L);
    }

    @Test
    void listSegments_infoTypeHasNoEmbeddingField() {
        List<String> fields = new ArrayList<>();
        for (java.lang.reflect.Field f : KnowledgeSegmentInfo.class.getDeclaredFields()) {
            fields.add(f.getName());
        }
        assertFalse(fields.contains("embedding"),
                "对外 DTO 不应暴露原始向量字段，实际字段: " + fields);
    }
}
