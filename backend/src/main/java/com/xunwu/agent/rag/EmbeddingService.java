package com.xunwu.agent.rag;

import com.xunwu.agent.config.XunwuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量化服务
 *
 * <p>使用 Ollama 本地运行的 bge-m3 模型生成文档及查询文本的向量。</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class EmbeddingService {

    private final ObjectProvider<OllamaEmbeddingModel> ollamaEmbeddingProvider;
    private final XunwuProperties properties;

    public EmbeddingService(ObjectProvider<OllamaEmbeddingModel> ollamaEmbeddingProvider,
                            XunwuProperties properties) {
        this.ollamaEmbeddingProvider = ollamaEmbeddingProvider;
        this.properties = properties;
    }

    /**
     * 获取 EmbeddingModel（优先 Ollama）
     */
    public EmbeddingModel getEmbeddingModel() {
        OllamaEmbeddingModel model = ollamaEmbeddingProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("Ollama EmbeddingModel 不可用，请确认 Ollama 服务已启动");
        }
        return model;
    }

    /**
     * 将文本向量化
     *
     * @param text 输入文本
     * @return 向量
     */
    public float[] embed(String text) {
        EmbeddingModel model = getEmbeddingModel();
        return model.embed(text);
    }

    /**
     * 批量向量化
     */
    public List<float[]> embed(List<String> texts) {
        EmbeddingModel model = getEmbeddingModel();
        return model.embed(texts);
    }

    /**
     * 向量转 PostgreSQL 数组字符串，如 [0.1,0.2,...]
     */
    public static String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * 将 PostgreSQL 向量字符串转 float 数组
     */
    public static float[] parseVector(String vectorString) {
        if (vectorString == null || vectorString.isBlank()) {
            return new float[0];
        }
        String trimmed = vectorString.replace("[", "").replace("]", "").trim();
        if (trimmed.isEmpty()) {
            return new float[0];
        }
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
