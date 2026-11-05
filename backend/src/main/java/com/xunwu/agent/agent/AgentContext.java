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

    /** 知识库上下文（由执行引擎在异步线程检索后填充） */
    private String knowledgeContext;

    /** 知识库检索查询；为 null 表示本次对话不启用 RAG */
    private String knowledgeQuery;

    /** 知识库是否已完成检索（避免非流式与流式重复检索） */
    private boolean knowledgeLoaded;

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
