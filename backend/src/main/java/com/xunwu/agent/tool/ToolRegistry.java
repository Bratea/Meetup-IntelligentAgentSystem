package com.xunwu.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 统一工具接口与工具管理服务
 *
 * <p>区分固定工具与可选工具，支持为智能体配置可用工具。
 * 工具实现通过 Spring AI 的 {@link ToolCallback} 暴露给模型。</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class ToolRegistry {

    /** 所有已注册工具，key = 工具名 */
    private final Map<String, RegisteredTool> tools = new java.util.LinkedHashMap<>();

    /**
     * 注册工具
     */
    public void register(String name, String description, String inputSchema,
                         ToolType type, boolean enabled,
                         BiFunction<String, ToolContext, String> handler) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .build();
        ToolCallback callback = FunctionToolCallback
                .builder(name, (BiFunction<String, ToolContext, String>) handler)
                .description(description)
                .inputType(String.class)
                .build();
        tools.put(name, new RegisteredTool(definition, callback, type, enabled));
        log.info("注册工具: name={}, type={}, enabled={}", name, type, enabled);
    }

    /**
     * 注册工具（Supplier 形式，无入参）
     */
    public void register(String name, String description, ToolType type,
                         boolean enabled, java.util.function.Supplier<String> handler) {
        register(name, description, "{}", type, enabled,
                (args, ctx) -> handler.get());
    }

    /**
     * 获取工具
     */
    public ToolCallback getTool(String name) {
        RegisteredTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("未注册的工具: " + name);
        }
        return tool.callback();
    }

    /**
     * 按名称列表获取工具（过滤未注册与禁用项）
     */
    public List<ToolCallback> getTools(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .filter(tools::containsKey)
                .peek(n -> {
                    if (!tools.get(n).enabled()) {
                        log.warn("工具 {} 已被禁用", n);
                    }
                })
                .filter(n -> tools.get(n).enabled())
                .map(n -> tools.get(n).callback())
                .collect(Collectors.toList());
    }

    /**
     * 获取固定工具
     */
    public List<ToolCallback> getFixedTools() {
        return tools.values().stream()
                .filter(t -> t.type() == ToolType.FIXED)
                .map(RegisteredTool::callback)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有可选工具
     */
    public List<ToolCallback> getOptionalTools() {
        return tools.values().stream()
                .filter(t -> t.type() == ToolType.OPTIONAL)
                .map(RegisteredTool::callback)
                .collect(Collectors.toList());
    }

    /**
     * 判断工具是否存在
     */
    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /**
     * 工具元信息列表
     */
    public List<ToolDefinitionInfo> listTools() {
        return tools.values().stream()
                .map(t -> new ToolDefinitionInfo(
                        t.definition().name(),
                        t.definition().description(),
                        t.type(),
                        t.definition().inputSchema(),
                        t.enabled()))
                .collect(Collectors.toList());
    }

    /**
     * 已注册工具名
     */
    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    /**
     * 内部注册记录
     */
    private record RegisteredTool(ToolDefinition definition,
                                  ToolCallback callback,
                                  ToolType type,
                                  boolean enabled) {
    }
}
