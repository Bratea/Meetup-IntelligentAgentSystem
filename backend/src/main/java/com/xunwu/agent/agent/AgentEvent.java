package com.xunwu.agent.agent;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Agent 执行事件（用于 SSE 推送）
 *
 * @author xunwu
 */
@Data
public class AgentEvent {

    /** 事件类型 */
    private String type;

    /** 事件数据 */
    private Object data;

    /** 事件时间 */
    private OffsetDateTime timestamp;

    /** 当前步数 */
    private Integer step;

    /** 当前状态 */
    private String state;

    public AgentEvent() {
        this.timestamp = OffsetDateTime.now();
    }

    public AgentEvent(String type, Object data, Integer step, String state) {
        this.type = type;
        this.data = data;
        this.step = step;
        this.state = state;
        this.timestamp = OffsetDateTime.now();
    }

    // ---------- 便捷构造 ----------

    public static AgentEvent thinking(int step) {
        return new AgentEvent("thinking", null, step, AgentState.THINKING.name());
    }

    public static AgentEvent token(String token, int step) {
        return new AgentEvent("token", token, step, AgentState.THINKING.name());
    }

    public static AgentEvent toolCalling(String toolName, String arguments, int step) {
        return new AgentEvent("tool_calling", new ToolCallPayload(toolName, arguments), step,
                AgentState.TOOL_CALLING.name());
    }

    public static AgentEvent toolResult(String toolName, String result, int step) {
        return new AgentEvent("tool_result", new ToolResultPayload(toolName, result), step,
                AgentState.TOOL_CALLING.name());
    }

    public static AgentEvent done(String content, int step) {
        return new AgentEvent("done", content, step, AgentState.COMPLETED.name());
    }

    /**
     * 执行失败事件。
     *
     * <p>SSE 连接一旦建立，HTTP 状态码就无法再改变，异常必须作为事件推给客户端，
     * 否则前端只会看到流被硬生生截断。</p>
     */
    public static AgentEvent error(String message) {
        return new AgentEvent("error", message, null, AgentState.FAILED.name());
    }

    public static AgentEvent maxSteps(int step) {
        return new AgentEvent("max_steps", "已达到最大执行步数 " + step, step,
                AgentState.MAX_STEPS_REACHED.name());
    }

    /**
     * 工具调用载荷
     */
    @Data
    public static class ToolCallPayload {
        private final String name;
        private final String arguments;

        public ToolCallPayload(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }

    /**
     * 工具结果载荷
     */
    @Data
    public static class ToolResultPayload {
        private final String name;
        private final String result;

        public ToolResultPayload(String name, String result) {
            this.name = name;
            this.result = result;
        }
    }
}
