package com.xunwu.agent.common;

import lombok.Getter;

/**
 * 统一 API 响应结构
 *
 * @author xunwu
 */
@Getter
public class ApiResponse<T> {

    /** 状态码：0 表示成功 */
    private final int code;

    /** 提示信息 */
    private final String message;

    /** 业务数据 */
    private final T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
