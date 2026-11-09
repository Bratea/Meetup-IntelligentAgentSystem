package com.xunwu.agent.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EmbeddingService} 单元测试（聚焦向量与 PG 数组字符串互转）
 *
 * @author xunwu
 */
class EmbeddingServiceTest {

    @Test
    void toVectorString_empty() {
        assertEquals("[]", EmbeddingService.toVectorString(new float[0]));
    }

    @Test
    void toVectorString_values() {
        assertEquals("[1.0,2.0,3.5]",
                EmbeddingService.toVectorString(new float[]{1f, 2f, 3.5f}));
    }

    @Test
    void toVectorString_noSpaces() {
        // pgvector 数组字符串不应包含空格，避免解析歧义
        String result = EmbeddingService.toVectorString(new float[]{0.1f, 0.2f});
        assertFalse(result.contains(" "));
    }
}
