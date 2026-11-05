package com.xunwu.agent.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 寻晤智能agent系统 配置项
 *
 * @author xunwu
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "xunwu")
public class XunwuProperties {

    /** Agent 执行引擎配置 */
    private Agent agent = new Agent();

    /** RAG 知识库配置 */
    private Rag rag = new Rag();

    /**
     * 启动时校验配置，避免错误的分段参数把运行期拖入死循环或静默降级。
     */
    @PostConstruct
    public void validate() {
        if (agent.getMaxSteps() < 1) {
            throw new IllegalStateException("xunwu.agent.max-steps 必须 >= 1，当前: " + agent.getMaxSteps());
        }
        if (rag.getChunkSize() < 1) {
            throw new IllegalStateException("xunwu.rag.chunk-size 必须 >= 1，当前: " + rag.getChunkSize());
        }
        if (rag.getChunkOverlap() < 0) {
            throw new IllegalStateException("xunwu.rag.chunk-overlap 不能为负数，当前: " + rag.getChunkOverlap());
        }
        if (rag.getChunkOverlap() >= rag.getChunkSize()) {
            throw new IllegalStateException(
                    "xunwu.rag.chunk-overlap (" + rag.getChunkOverlap()
                            + ") 必须小于 xunwu.rag.chunk-size (" + rag.getChunkSize() + ")");
        }
        if (rag.getTopK() < 1) {
            throw new IllegalStateException("xunwu.rag.top-k 必须 >= 1，当前: " + rag.getTopK());
        }
        log.info("配置校验通过: maxSteps={}, rag.topK={}, rag.chunkSize={}, rag.chunkOverlap={}",
                agent.getMaxSteps(), rag.getTopK(), rag.getChunkSize(), rag.getChunkOverlap());
    }

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
