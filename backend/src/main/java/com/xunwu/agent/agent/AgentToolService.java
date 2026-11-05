package com.xunwu.agent.agent;

import com.xunwu.agent.tool.ToolRegistry;
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

    private final ToolRegistry toolRegistry;

    public AgentToolService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行工具
     *
     * @param name      工具名
     * @param arguments 参数 JSON 字符串
     * @return 工具执行结果
     */
    public String execute(String name, String arguments) {
        long start = System.currentTimeMillis();
        try {
            ToolCallback tool = toolRegistry.getTool(name);
            String result = tool.call(arguments, new ToolContext(Map.of()));
            log.info("工具执行成功: name={}, cost={}ms", name, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("工具执行失败: name={}", name, e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
