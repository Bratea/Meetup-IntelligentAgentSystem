package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import com.xunwu.agent.tool.ToolDefinitionInfo;
import com.xunwu.agent.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工具管理接口
 *
 * @author xunwu
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolRegistry toolRegistry;

    public ToolController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 获取工具列表
     */
    @GetMapping
    public ApiResponse<List<ToolDefinitionInfo>> list() {
        return ApiResponse.ok(toolRegistry.listTools());
    }
}
