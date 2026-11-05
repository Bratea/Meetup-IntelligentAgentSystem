package com.xunwu.agent.tool;

import lombok.Data;

/**
 * 工具定义元信息（用于前端展示与配置）
 *
 * @author xunwu
 */
@Data
public class ToolDefinitionInfo {

    /** 工具名称 */
    private String name;

    /** 工具描述 */
    private String description;

    /** 工具类型：FIXED / OPTIONAL */
    private ToolType type;

    /** 输入参数说明（JSON Schema 摘要） */
    private String inputSchema;

    /** 是否启用 */
    private boolean enabled;

    public ToolDefinitionInfo() {
    }

    public ToolDefinitionInfo(String name, String description, ToolType type,
                              String inputSchema, boolean enabled) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.inputSchema = inputSchema;
        this.enabled = enabled;
    }
}
