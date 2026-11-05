package com.xunwu.agent.agent;

import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Agent 执行上下文
 *
 * @author xunwu
 */
@Data
public class AgentContext {

    /** ChatClient */
    private ChatClient chatClient;

    /** 系统提示词 */
    private String systemPrompt;

    /** 知识库上下文 */
    private String knowledgeContext;

    /** 历史消息 */
    private List<Message> history;

    /** 用户输入 */
    private String userMessage;

    /** 可用工具 */
    private List<ToolCallback> tools;

    /** 工具名称 */
    private List<String> toolNames;

    /** 最大执行步数 */
    private int maxSteps;

    /** 模型标识 */
    private String modelId;
}
