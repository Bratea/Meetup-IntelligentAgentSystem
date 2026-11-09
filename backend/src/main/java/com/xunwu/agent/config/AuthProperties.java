package com.xunwu.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口鉴权配置。
 *
 * <p>token 为空时鉴权关闭，仅建议在本地开发环境使用（启动时会打 warn 日志提醒）。
 * 生产 / 自部署场景必须配置 {@code XUNWU_AUTH_TOKEN}。</p>
 *
 * @author xunwu
 */
@Data
@ConfigurationProperties(prefix = "xunwu.auth")
public class AuthProperties {

    /** 访问令牌；为空表示不启用鉴权 */
    private String token = "";
}
