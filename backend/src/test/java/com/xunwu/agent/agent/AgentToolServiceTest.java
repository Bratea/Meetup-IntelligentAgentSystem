package com.xunwu.agent.agent;

import com.xunwu.agent.tool.ToolRegistry;
import com.xunwu.agent.tool.ToolType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentToolService} 单元测试。
 *
 * <p>重点验证工具失败时的输出契约：必须是合法 JSON，且不能把底层异常
 * 的 message（可能含连接串、SQL、内部地址）回给模型进入对话内容。</p>
 *
 * @author xunwu
 */
class AgentToolServiceTest {

    private ToolRegistry registry;
    private AgentToolService service;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        service = new AgentToolService(registry);
    }

    @Test
    void execute_unknownTool_returnsErrorJsonWithName() {
        String result = service.execute("not_exist", "{}");

        assertTrue(result.contains("\"error\""), "必须是可解析的 JSON 错误对象");
        // 未注册的工具名要回给模型，让它不要再重复调用
        assertTrue(result.contains("not_exist"));
    }

    @Test
    void execute_toolThrows_hidesInternalMessage() {
        registry.register("boom", "B", "{}", ToolType.OPTIONAL, true,
                (args, ctx) -> {
                    throw new IllegalStateException("jdbc:postgresql://internal:5432/x");
                });

        String result = service.execute("boom", "{}");

        assertTrue(result.contains("\"error\""));
        // 底层异常 message 可能含连接串，不能进入对话内容
        assertFalse(result.contains("jdbc:postgresql"));
        assertFalse(result.contains("internal:5432"));
    }

    @Test
    void execute_success_returnsToolOutput() {
        registry.register("echo", "E", "{}", ToolType.OPTIONAL, true,
                (args, ctx) -> "{\"ok\":true}");

        assertEquals("{\"ok\":true}", service.execute("echo", "{}"));
    }

    @Test
    void execute_nullResult_becomesEmptyString() {
        registry.register("nil", "N", "{}", ToolType.OPTIONAL, true,
                (args, ctx) -> null);

        assertEquals("", service.execute("nil", "{}"));
    }
}
