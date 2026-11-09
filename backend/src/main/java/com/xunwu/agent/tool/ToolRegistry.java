package com.xunwu.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

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
        // 用自定义 callback：Spring AI 的 FunctionToolCallback 强制做
        // JsonParser.fromJson(args, inputType)，与本项目"工具自行解析 JSON"的设计冲突
        ToolCallback callback = new RawJsonToolCallback(definition, handler);
        tools.put(name, new RegisteredTool(definition, callback, type, enabled));
        log.info("注册工具: name={}, type={}, enabled={}", name, type, enabled);
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
     *
     * <p>返回可变列表，方便调用方继续追加固定工具。</p>
     */
    public List<ToolCallback> getTools(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return names.stream()
                .filter(tools::containsKey)
                .peek(n -> {
                    if (!tools.get(n).enabled()) {
                        log.warn("工具 {} 已被禁用，已跳过", n);
                    }
                })
                .filter(n -> tools.get(n).enabled())
                .map(n -> tools.get(n).callback())
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    /**
     * 按名称列表获取实际可用的工具名（已过滤未注册与禁用项）。
     *
     * <p>用于填充 {@code ToolCallingChatOptions.toolNames}：
     * Spring AI 的 {@code DefaultToolCallingManager.resolveToolDefinitions} 会对
     * toolNames 中无法解析的名字抛 {@code IllegalStateException}，
     * 因此这里必须只返回真正已注册的工具名。</p>
     */
    public List<String> resolveToolNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .filter(tools::containsKey)
                .filter(n -> tools.get(n).enabled())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 按名称列表获取工具，并追加固定工具（同名去重）。
     *
     * <p>固定工具对所有智能体生效。若调用方已在 {@code names} 里显式列出某个固定工具，
     * 只保留一份 —— Spring AI 的 {@code ToolCallingChatOptions.validateToolCallbacks}
     * 对同名工具会直接抛 {@code IllegalStateException}，整个对话就彻底失败。</p>
     */
    public List<ToolCallback> getMergedTools(List<String> names) {
        Map<String, ToolCallback> merged = new LinkedHashMap<>();
        for (ToolCallback tool : getTools(names)) {
            merged.put(tool.getToolDefinition().name(), tool);
        }
        for (ToolCallback tool : getFixedTools()) {
            merged.putIfAbsent(tool.getToolDefinition().name(), tool);
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * {@link #getMergedTools(List)} 对应的工具名列表（同样同名去重）。
     */
    public List<String> getMergedToolNames(List<String> names) {
        List<String> mergedNames = new ArrayList<>();
        for (ToolCallback tool : getMergedTools(names)) {
            mergedNames.add(tool.getToolDefinition().name());
        }
        return mergedNames;
    }

    /**
     * 获取固定工具（仅返回启用中的）
     */
    public List<ToolCallback> getFixedTools() {
        return tools.values().stream()
                .filter(t -> t.type() == ToolType.FIXED)
                .filter(RegisteredTool::enabled)
                .map(RegisteredTool::callback)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    /**
     * 获取固定工具名（仅返回启用中的）
     */
    public List<String> getFixedToolNames() {
        return tools.values().stream()
                .filter(t -> t.type() == ToolType.FIXED)
                .filter(RegisteredTool::enabled)
                .map(t -> t.definition().name())
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
