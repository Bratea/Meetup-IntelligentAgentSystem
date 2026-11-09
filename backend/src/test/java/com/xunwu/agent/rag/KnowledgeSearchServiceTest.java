package com.xunwu.agent.rag;

import com.xunwu.agent.entity.KnowledgeSearchResult;
import com.xunwu.agent.mapper.KnowledgeSegmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KnowledgeSearchService} 单元测试（聚焦上下文组装逻辑）
 *
 * @author xunwu
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSearchServiceTest {

    @Mock
    private KnowledgeSegmentMapper knowledgeSegmentMapper;

    @Mock
    private EmbeddingService embeddingService;

    private KnowledgeSearchService service() {
        return new KnowledgeSearchService(knowledgeSegmentMapper, embeddingService);
    }

    @Test
    void buildContext_emptyOrNull_returnsEmpty() {
        assertEquals("", service().buildContext(List.of()));
        assertEquals("", service().buildContext(null));
    }

    @Test
    void buildContext_containsMarkersAndContent() {
        List<KnowledgeSearchResult> results = List.of(
                new KnowledgeSearchResult(1L, 10L, "文档A", "片段一", 0.9),
                new KnowledgeSearchResult(2L, 11L, "文档B", "片段二", 0.8));
        String context = service().buildContext(results);
        assertTrue(context.contains("【片段 1】"));
        assertTrue(context.contains("来源: 文档A"));
        assertTrue(context.contains("片段一"));
        assertTrue(context.contains("【片段 2】"));
        assertTrue(context.contains("片段二"));
    }

    @Test
    void buildContext_nullDocumentName_skipsSourceLine() {
        List<KnowledgeSearchResult> results = List.of(
                new KnowledgeSearchResult(1L, 10L, null, "内容", 0.5));
        String context = service().buildContext(results);
        assertFalse(context.contains("来源:"));
        assertTrue(context.contains("内容"));
    }
}
