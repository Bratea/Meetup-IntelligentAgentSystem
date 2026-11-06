package com.xunwu.agent.common;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.regex.Pattern;

/**
 * 异常链工具
 *
 * <p>Spring AI / WebClient 对底层网络故障的包装层次不固定：
 * 连接拒绝可能是 {@link ConnectException}，也可能是
 * {@link ClosedChannelException}、{@link java.net.http.HttpConnectTimeoutException}
 * 或包在 {@code ResourceAccessException} 里。统一在这里判定，
 * 避免给用户抛出"系统繁忙"这种没有下文的提示。</p>
 *
 * @author xunwu
 */
public final class ThrowableUtils {

    /** 连接类故障的关键字（覆盖连接被拒 / 被重置 / 已关闭 / 超时） */
    private static final Pattern CONNECTION_PATTERN = Pattern.compile(
            "connection\\s+(refused|reset|closed|timed\\s*out)|unknown\\s*host",
            Pattern.CASE_INSENSITIVE);

    private ThrowableUtils() {
    }

    /**
     * 沿异常链判断是否为"连不上模型/向量化服务"
     */
    public static boolean isConnectionFailure(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof ClosedChannelException
                    || current instanceof SocketException
                    || current instanceof java.net.http.HttpConnectTimeoutException
                    || current instanceof org.springframework.web.client.ResourceAccessException) {
                return true;
            }
            if (current.getMessage() != null
                    && CONNECTION_PATTERN.matcher(current.getMessage()).find()) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 取异常链最底层原因
     */
    public static Throwable rootCause(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 取最底层原因的信息；没有则退回异常类名
     */
    public static String rootMessage(Throwable e) {
        Throwable root = rootCause(e);
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
