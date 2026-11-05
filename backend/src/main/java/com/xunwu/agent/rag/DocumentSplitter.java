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
        int chunkSize = properties.getRag().getChunkSize();
        int overlap = properties.getRag().getChunkOverlap();

        if (content == null || content.isBlank()) {
            return List.of();
        }

        // 优先按空行（段落）切分，再按长度切分
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
            } else {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    // 重叠保留
                    String overlapText = current.substring(Math.max(0, current.length() - overlap));
                    current = new StringBuilder(overlapText).append('\n').append(text);
                } else {
                    // 单段超长，强制切分
                    int start = 0;
                    while (start < text.length()) {
                        int end = Math.min(start + chunkSize, text.length());
                        chunks.add(text.substring(start, end));
                        start = end - overlap;
                        if (start < 0) {
                            start = 0;
                        }
                    }
                    current = new StringBuilder();
                }
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
