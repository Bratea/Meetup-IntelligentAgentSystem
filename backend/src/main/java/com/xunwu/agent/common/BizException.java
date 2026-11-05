package com.xunwu.agent.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author xunwu
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    /** 依赖服务不可用（Ollama / 远端模型等） */
    public static BizException serviceUnavailable(String message) {
        return new BizException(503, message);
    }
}
