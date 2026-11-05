package com.xunwu.agent.agent;

import com.xunwu.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 工具执行服务
 *
 * <p>统一管理工具调度与执行</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class AgentToolService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolRegistry toolRegistry;

    public AgentToolService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行工具
     *
     * @param name      工具名
     * @param arguments 参数 JSON 字符串
     * @return 工具执行结果（失败时返回 JSON 错误对象，而不是异常）
     */
    public String execute(String name, String arguments) {
        long start = System.currentTimeMillis();
        try {
            ToolCallback tool = toolRegistry.getTool(name);
            String result = tool.call(arguments, new ToolContext(Map.of()));
            log.info("工具执行成功: name={}, cost={}ms", name, System.currentTimeMillis() - start);
            return result == null ? "" : result;
        } catch (Exception e) {
            log.error("工具执行失败: name={}", name, e);
            // 必须用 JSON 序列化输出，避免消息中的引号/反斜杠破坏 JSON 结构
            return toErrorJson(name, e.getMessage());
        }
    }

    private String toErrorJson(String name, String message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "error", "工具 " + name + " 执行失败",
                    "detail", message == null ? "" : message));
        } catch (Exception e) {
            return "{\"error\":\"工具执行失败\"}";
        }
    }
}
