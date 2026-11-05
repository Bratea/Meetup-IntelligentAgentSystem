package com.xunwu.agent.rag;

import com.xunwu.agent.config.XunwuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分段器
 *
 * <p>将长文档切分为带重叠的文本片段，便于向量化与检索。</p>
 *
 * <p>实现保证终止：重叠量会被收敛到 {@code chunkSize - 1} 以内，
 * 且硬切分每轮都校验起始游标严格前进，避免死循环与内存膨胀。</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class DocumentSplitter {

    private final XunwuProperties properties;

    public DocumentSplitter(XunwuProperties properties) {
        this.properties = properties;
    }

    /**
     * 分段
     *
     * @param content 文档内容
     * @return 分段列表
     */
    public List<String> split(String content) {
        int chunkSize = Math.max(1, properties.getRag().getChunkSize());
        int configuredOverlap = properties.getRag().getChunkOverlap();
        // 重叠量必须小于分段长度，否则切分游标无法前进
        int overlap = Math.min(Math.max(0, configuredOverlap), chunkSize - 1);
        if (configuredOverlap != overlap) {
            log.warn("rag.chunk-overlap={} 超出有效范围，已自动收敛为 {}（必须小于 chunk-size={}）",
                    configuredOverlap, overlap, chunkSize);
        }

        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String text = paragraph.trim();
            if (text.isEmpty()) {
                continue;
            }

            if (current.length() + text.length() <= chunkSize) {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(text);
                continue;
            }

            // 当前缓冲已满，先落盘
            if (current.length() > 0) {
                chunks.add(current.toString());
                String overlapText = current.substring(Math.max(0, current.length() - overlap));
                current = new StringBuilder(overlapText).append('\n').append(text);
            } else {
                // 单个段落超过分段长度，硬切分
                current = new StringBuilder(hardSplit(text, chunkSize, overlap, chunks));
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /**
     * 硬切分超长段落，返回最后一段（可能不足 chunkSize）作为后续累积的起点。
     */
    private String hardSplit(String text, int chunkSize, int overlap, List<String> chunks) {
        int start = 0;
        int lastStart = -1;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                return "";
            }
            int next = Math.max(start + 1, end - overlap);
            if (next <= lastStart) {
                // 理论上不可达（overlap < chunkSize），作为兜底防止死循环
                log.warn("分段游标未前进，强制推进: start={}, next={}", start, next);
                next = end;
            }
            lastStart = start;
            start = next;
        }
        return "";
    }
}
