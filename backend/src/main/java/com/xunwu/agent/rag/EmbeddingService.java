package com.xunwu.agent.rag;

import com.xunwu.agent.common.BizException;
import com.xunwu.agent.common.ThrowableUtils;
import com.xunwu.agent.config.XunwuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

    /** 向量化模型名称，用于提示用户拉取 */
    private static final String EMBEDDING_MODEL = "bge-m3";

    /** Spring AI 把远端 HTTP 状态码写在异常 message 里，如 "HTTP 404 - ..." */
    private static final java.util.regex.Pattern HTTP_STATUS_PATTERN =
            java.util.regex.Pattern.compile("HTTP\\s+(\\d{3})");

    /**
     * 获取 EmbeddingModel（优先 Ollama）
     */
    public EmbeddingModel getEmbeddingModel() {
        OllamaEmbeddingModel model = ollamaEmbeddingProvider.getIfAvailable();
        if (model == null) {
            throw BizException.serviceUnavailable(
                    "向量化模型未配置，请确认 Ollama 服务已启动");
        }
        return model;
    }

    /**
     * 把向量化失败翻译成用户可读的提示。
     * 只说明"该做什么"，不暴露堆栈与内部地址。
     *
     * <p>Spring AI 对远端错误会包一层 {@code NonTransientAiException}，
     * HTTP 状态码只出现在 message 里（形如 "HTTP 404 - ..."），
     * 因此这里同时按异常类型与 message 正则两条路径取状态码。</p>
     */
    private BizException toEmbeddingException(Exception e) {
        int status = resolveHttpStatus(e);

        if (status == 404) {
            return BizException.serviceUnavailable(
                    "向量化模型 " + EMBEDDING_MODEL + " 未安装，请先执行 "
                            + "ollama pull " + EMBEDDING_MODEL + " 拉取");
        }
        if (status == 401 || status == 403) {
            return BizException.serviceUnavailable(
                    "向量化服务鉴权失败，请检查配置");
        }
        if (status >= 500) {
            return BizException.serviceUnavailable(
                    "向量化服务暂时不可用（HTTP " + status + "），请稍后重试");
        }
        if (status >= 400) {
            return BizException.serviceUnavailable(
                    "向量化失败（HTTP " + status + "），请稍后重试");
        }

        if (ThrowableUtils.isConnectionFailure(e)) {
            return BizException.serviceUnavailable("无法连接向量化服务，请确认 Ollama 已启动");
        }
        log.warn("向量化失败: {}", ThrowableUtils.rootCause(e));
        return BizException.serviceUnavailable("向量化服务调用失败，请稍后重试");
    }

    /** 沿异常链提取 HTTP 状态码，取不到返回 0 */
    private int resolveHttpStatus(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof WebClientResponseException wce) {
                return wce.getStatusCode().value();
            }
            java.util.regex.Matcher m = HTTP_STATUS_PATTERN.matcher(String.valueOf(current.getMessage()));
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return 0;
    }

    /**
     * 将文本向量化
     *
     * @param text 输入文本
     * @return 向量
     */
    public float[] embed(String text) {
        EmbeddingModel model = getEmbeddingModel();
        try {
            return model.embed(text);
        } catch (Exception e) {
            throw toEmbeddingException(e);
        }
    }

    /**
     * 批量向量化
     */
    public List<float[]> embed(List<String> texts) {
        EmbeddingModel model = getEmbeddingModel();
        try {
            return model.embed(texts);
        } catch (Exception e) {
            throw toEmbeddingException(e);
        }
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

}
