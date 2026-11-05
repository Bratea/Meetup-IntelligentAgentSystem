package com.xunwu.agent.tool.builtin;

import com.xunwu.agent.tool.ToolType;
import com.xunwu.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


/**
 * 工具注册器：将内置工具注册到 {@link ToolRegistry}
 *
 * @author xunwu
 */
@Slf4j
@Component
public class ToolBootstrap {

    private final ToolRegistry toolRegistry;
    private final CurrentTimeTool currentTimeTool;
    private final CalculatorTool calculatorTool;
    private final KnowledgeSearchTool knowledgeSearchTool;

    public ToolBootstrap(ToolRegistry toolRegistry,
                         CurrentTimeTool currentTimeTool,
                         CalculatorTool calculatorTool,
                         KnowledgeSearchTool knowledgeSearchTool) {
        this.toolRegistry = toolRegistry;
        this.currentTimeTool = currentTimeTool;
        this.calculatorTool = calculatorTool;
        this.knowledgeSearchTool = knowledgeSearchTool;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void registerBuiltinTools() {
        toolRegistry.register(
                "current_time",
                CurrentTimeTool.definition().getDescription(),
                CurrentTimeTool.definition().getInputSchema(),
                ToolType.OPTIONAL,
                true,
                (args, ctx) -> currentTimeTool.call(args, ctx));

        toolRegistry.register(
                "calculator",
                CalculatorTool.definition().getDescription(),
                CalculatorTool.definition().getInputSchema(),
                ToolType.OPTIONAL,
                true,
                (args, ctx) -> calculatorTool.call(args, ctx));

        // 固定工具：所有智能体默认可用
        toolRegistry.register(
                "knowledge_search",
                KnowledgeSearchTool.definition().getDescription(),
                KnowledgeSearchTool.definition().getInputSchema(),
                ToolType.FIXED,
                true,
                (args, ctx) -> knowledgeSearchTool.call(args, ctx));

        log.info("内置工具注册完成: {}", toolRegistry.names());
    }
}
