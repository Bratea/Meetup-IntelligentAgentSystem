package com.xunwu.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 寻晤智能agent系统 配置项
 *
 * @author xunwu
 */
@Data
@ConfigurationProperties(prefix = "xunwu")
public class XunwuProperties {

    /** Agent 执行引擎配置 */
    private Agent agent = new Agent();

    /** RAG 知识库配置 */
    private Rag rag = new Rag();

    @Data
    public static class Agent {
        /** 单次任务最大执行步数 */
        private int maxSteps = 8;
        /** 默认模型标识 */
        private String defaultModel = "qwen2.5:7b";
    }

    @Data
    public static class Rag {
        /** Top-K 检索数量 */
        private int topK = 4;
        /** 文档分段长度 */
        private int chunkSize = 500;
        /** 分段重叠长度 */
        private int chunkOverlap = 50;
        /** 向量维度 */
        private int embeddingDimensions = 1024;
    }
}
