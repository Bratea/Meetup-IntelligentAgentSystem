package com.xunwu.agent.tool.builtin;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CurrentTimeTool} 单元测试。
 *
 * <p>重点验证模型给出的非法 pattern 不会让工具抛异常 ——
 * pattern 完全由模型生成，未转义的字母会让
 * {@link java.time.format.DateTimeFormatter#ofPattern} 抛
 * {@link IllegalArgumentException}。</p>
 *
 * @author xunwu
 */
class CurrentTimeToolTest {

    private final CurrentTimeTool tool = new CurrentTimeTool();

    private static final ToolContext EMPTY_CTX = new ToolContext(Map.of());

    @Test
    void validPattern_isApplied() {
        String result = tool.call("{\"pattern\":\"yyyy-MM-dd\"}", EMPTY_CTX);
        assertNotNull(result);
        assertTrue(result.contains("\"pattern\":\"yyyy-MM-dd\""));
        assertTrue(result.contains("\"datetime\""));
    }

    @Test
    void illegalPattern_fallsBackToDefaultInsteadOfThrowing() {
        // 'T' 不是合法的 DateTimeFormatter 模式字母，ofPattern 会抛 IllegalArgumentException
        String result = tool.call("{\"pattern\":\"yyyy-To\"}", EMPTY_CTX);
        assertNotNull(result);
        assertTrue(result.contains("\"datetime\""), "非法格式应回退默认格式而不是报错");
        assertTrue(result.contains("yyyy-MM-dd HH:mm:ss"), "应回退到默认格式并回填 pattern");
    }

    @Test
    void malformedJson_fallsBackToDefault() {
        String result = tool.call("not-json", EMPTY_CTX);
        assertTrue(result.contains("yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    void nullArgs_fallsBackToDefault() {
        String result = tool.call(null, EMPTY_CTX);
        assertTrue(result.contains("yyyy-MM-dd HH:mm:ss"));
    }
}
