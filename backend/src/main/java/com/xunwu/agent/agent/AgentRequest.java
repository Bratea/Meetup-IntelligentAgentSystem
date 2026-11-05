package com.xunwu.agent.agent;

import lombok.Data;

import java.util.List;

/**
 * Agent 执行请求
 *
 * @author xunwu
 */
@Data
public class AgentRequest {

    /** 智能体 ID（为空时使用默认智能体配置） */
    private Long agentId;

    /** 会话 ID */
    private Long conversationId;

    /** 用户输入 */
    private String message;

    /** 模型标识（覆盖智能体默认模型） */
    private String modelId;

    /** 是否启用知识库检索 */
    private boolean enableRag = true;

    /** 最大执行步数（覆盖智能体配置） */
    private Integer maxSteps;
}
