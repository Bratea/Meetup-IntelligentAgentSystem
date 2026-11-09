package com.xunwu.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolRegistry} 单元测试
 *
 * @author xunwu
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register("tool_a", "A", "{}", ToolType.OPTIONAL, true, (a, c) -> "a");
        registry.register("tool_b", "B", "{}", ToolType.OPTIONAL, true, (a, c) -> "b");
        registry.register("tool_disabled", "D", "{}", ToolType.OPTIONAL, false, (a, c) -> "d");
        registry.register("fixed_tool", "F", "{}", ToolType.FIXED, true, (a, c) -> "f");
    }

    @Test
    void getTools_filtersUnregisteredAndDisabled() {
        List<ToolCallback> tools = registry.getTools(
                List.of("tool_a", "tool_disabled", "not_exist"));
        assertEquals(1, tools.size());
    }

    @Test
    void getTools_returnsMutableList() {
        List<ToolCallback> tools = registry.getTools(List.of("tool_a"));
        // 调用方会 addAll 固定工具，不可变列表会抛 UnsupportedOperationException
        assertDoesNotThrow(() -> tools.addAll(new ArrayList<>()));
    }

    @Test
    void resolveToolNames_onlyRegisteredAndEnabled() {
        List<String> names = registry.resolveToolNames(
                List.of("tool_a", "tool_b", "tool_disabled", "not_exist"));
        assertEquals(List.of("tool_a", "tool_b"), names);
    }

    @Test
    void getFixedTools_onlyFixedAndEnabled() {
        assertEquals(1, registry.getFixedTools().size());
        assertEquals(List.of("fixed_tool"), registry.getFixedToolNames());
    }

    @Test
    void getTool_unknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> registry.getTool("not_exist"));
    }

    @Test
    void contains_reflectsRegistration() {
        assertTrue(registry.contains("tool_a"));
        assertFalse(registry.contains("not_exist"));
    }

    @Test
    void listTools_containsAllRegistered() {
        List<ToolDefinitionInfo> infos = registry.listTools();
        assertEquals(4, infos.size());
        assertTrue(infos.stream().anyMatch(i -> i.getName().equals("tool_a")));
        assertTrue(infos.stream().anyMatch(i -> i.getName().equals("fixed_tool")));
    }

    @Test
    void getTools_emptyOrNullNames_returnsEmptyMutableList() {
        assertTrue(registry.getTools(null).isEmpty());
        assertTrue(registry.getTools(List.of()).isEmpty());
        assertDoesNotThrow(() -> registry.getTools(null).addAll(new ArrayList<>()));
    }

    @Test
    void getMergedTools_appendsFixedAndDeduplicates() {
        // 显式列出固定工具时只保留一份，否则 Spring AI 会抛同名工具异常
        List<ToolCallback> tools = registry.getMergedTools(
                List.of("tool_a", "fixed_tool"));
        assertEquals(List.of("tool_a", "fixed_tool"),
                tools.stream().map(t -> t.getToolDefinition().name()).toList());
    }

    @Test
    void getMergedTools_filtersUnregisteredAndDisabled() {
        List<ToolCallback> tools = registry.getMergedTools(
                List.of("tool_a", "tool_disabled", "not_exist"));
        assertEquals(List.of("tool_a", "fixed_tool"),
                tools.stream().map(t -> t.getToolDefinition().name()).toList());
    }

    @Test
    void getMergedToolNames_deduplicates() {
        List<String> names = registry.getMergedToolNames(
                List.of("tool_a", "tool_b", "fixed_tool", "not_exist"));
        assertEquals(List.of("tool_a", "tool_b", "fixed_tool"), names);
    }

    @Test
    void getMergedTools_emptyNames_stillReturnsFixed() {
        List<ToolCallback> tools = registry.getMergedTools(List.of());
        assertEquals(1, tools.size());
        assertEquals("fixed_tool", tools.get(0).getToolDefinition().name());
    }

    @Test
    void getMergedToolNames_nullNames_stillReturnsFixed() {
        assertEquals(List.of("fixed_tool"), registry.getMergedToolNames(null));
    }
}
