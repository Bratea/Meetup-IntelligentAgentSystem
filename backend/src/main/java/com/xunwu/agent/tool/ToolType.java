package com.xunwu.agent.tool;

import lombok.Getter;

/**
 * 工具类型
 *
 * <p>固定工具：Agent 运行必需（如知识库检索）；可选工具：按需启用。</p>
 *
 * @author xunwu
 */
@Getter
public enum ToolType {

    /** 固定工具，始终可用 */
    FIXED("固定工具"),

    /** 可选工具，按智能体配置启用 */
    OPTIONAL("可选工具");

    private final String label;

    ToolType(String label) {
        this.label = label;
    }
}
