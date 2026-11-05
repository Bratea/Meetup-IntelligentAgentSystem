package com.xunwu.agent.tool.builtin;

import com.xunwu.agent.tool.ToolType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 示例工具：获取当前时间
 *
 * @author xunwu
 */
@Slf4j
@Component
public class CurrentTimeTool {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public String call(String args, ToolContext context) {
        log.info("调用工具 CurrentTimeTool, args={}", args);
        String pattern = "yyyy-MM-dd HH:mm:ss";
        // 支持从参数中解析格式（简单实现）
        if (args != null && args.contains("pattern")) {
            try {
                var node = OBJECT_MAPPER.readTree(args);
                if (node.has("pattern")) {
                    pattern = node.get("pattern").asText();
                }
            } catch (Exception ignored) {
                // 使用默认格式
            }
        }
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
        Map<String, String> result = new HashMap<>();
        result.put("datetime", now);
        result.put("pattern", pattern);
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"datetime\":\"" + now + "\"}";
        }
    }

    public static com.xunwu.agent.tool.ToolDefinitionInfo definition() {
        return new com.xunwu.agent.tool.ToolDefinitionInfo(
                "current_time",
                "获取当前日期时间。当用户询问当前时间、日期、今天是几号时使用。",
                ToolType.OPTIONAL,
                "{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\",\"description\":\"日期时间格式，如 yyyy-MM-dd HH:mm:ss\"}}}",
                true);
    }
}
