package com.xunwu.agent.rag;

import com.xunwu.agent.config.XunwuProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DocumentSplitter} 单元测试
 *
 * <p>重点覆盖：终止性（不死循环）、重叠语义、硬切分、空输入。</p>
 *
 * @author xunwu
 */
class DocumentSplitterTest {

    private DocumentSplitter splitter(int chunkSize, int overlap) {
        XunwuProperties properties = new XunwuProperties();
        properties.getRag().setChunkSize(chunkSize);
        properties.getRag().setChunkOverlap(overlap);
        return new DocumentSplitter(properties);
    }

    @Test
    void shortText_returnsSingleChunk() {
        assertEquals(List.of("你好世界"), splitter(500, 50).split("你好世界"));
    }

    @Test
    void blankOrNull_returnsEmpty() {
        assertTrue(splitter(500, 50).split(null).isEmpty());
        assertTrue(splitter(500, 50).split("").isEmpty());
        assertTrue(splitter(500, 50).split("   \n  ").isEmpty());
    }

    @Test
    void multipleParagraphs_joinedWhenUnderChunkSize() {
        List<String> chunks = splitter(100, 10).split("第一段\n\n第二段\n\n第三段");
        assertEquals(List.of("第一段\n第二段\n第三段"), chunks);
    }

    @Test
    void longParagraph_hardSplits() {
        // 25 字符，chunkSize=10、overlap=2
        List<String> chunks = splitter(10, 2).split("abcdefghijklmnopqrstuvwxy");
        assertEquals(3, chunks.size());
        assertEquals("abcdefghij", chunks.get(0));
        assertEquals("ijklmnopqr", chunks.get(1));
        assertEquals("qrstuvwxy", chunks.get(2));
    }

    @Test
    void overlap_carriesTailOfPreviousChunk() {
        List<String> chunks = splitter(10, 2).split("abcdefghijklmnopqrstuvwxy");
        // 第二段应以第一段的最后 overlap 个字符开头
        String tail = chunks.get(0).substring(chunks.get(0).length() - 2);
        assertTrue(chunks.get(1).startsWith(tail),
                "第二段应以上一段结尾重叠开头，实际: " + chunks.get(1));
    }

    @Test
    void cursorAlwaysAdvances_terminatesWhenOverlapNearlyEqualsChunkSize() {
        // overlap 被收敛到 chunkSize-1，仍需保证终止
        List<String> chunks = splitter(5, 4).split("abcdefghijklmnopqrstuvwxyz");
        assertFalse(chunks.isEmpty());
        // 每个分段都不应超过 chunkSize
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 5, "分段超长: " + chunk);
        }
    }

    @Test
    void overlapLargerThanChunkSize_isClampedAndTerminates() {
        List<String> chunks = splitter(10, 50).split("abcdefghijklmnopqrstuvwxy");
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).length() <= 10);
    }

    @Test
    void paragraphBoundary_chunkBreaksAtParagraphWhenBufferFull() {
        // 两个段落单独都放得下，但合起来超过 chunkSize
        String p1 = "a".repeat(8);
        String p2 = "b".repeat(8);
        List<String> chunks = splitter(10, 2).split(p1 + "\n\n" + p2);
        assertEquals(2, chunks.size());
        assertEquals(p1, chunks.get(0));
        assertTrue(chunks.get(1).contains(p2));
    }
}
