package com.xunwu.agent.agent;

import lombok.Getter;

/**
 * Agent 执行状态
 *
 * <p>使用状态机管理任务执行状态</p>
 *
 * @author xunwu
 */
@Getter
public enum AgentState {

    /** 空闲 */
    IDLE("空闲"),

    /** 思考中：模型推理 */
    THINKING("思考中"),

    /** 工具调用中 */
    TOOL_CALLING("工具调用中"),

    /** 已完成 */
    COMPLETED("已完成"),

    /** 达到最大步数终止 */
    MAX_STEPS_REACHED("已达最大步数"),

    /** 执行失败 */
    FAILED("执行失败");

    private final String label;

    AgentState(String label) {
        this.label = label;
    }
}
