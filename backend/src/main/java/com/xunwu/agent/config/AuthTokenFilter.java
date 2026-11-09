package com.xunwu.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xunwu.agent.common.ApiResponse;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 访问令牌过滤器：所有 {@code /api/**} 接口默认要求
 * {@code Authorization: Bearer <token>}。
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li><b>单 Token 而非用户体系</b>：本项目是单智能体演示系统，
 *       没有用户表、没有登录页。引入 Spring Security + 用户表属于过度设计，
 *       而「所有接口裸奔」又让任何能访问端口的人都能读写全部数据。
 *       配置一个 Bearer Token 是两者之间最划算的一档。</li>
 *   <li><b>token 留空 = 鉴权关闭</b>：保证本地开发 / Docker Compose
 *       开箱即能用，但启动时会打 warn 日志明确提示当前处于无防护状态。</li>
 *   <li><b>白名单只放健康检查</b>：Docker / K8s 探针需要无鉴权探活，
 *       其余接口（含 actuator）一律要带 token。</li>
 *   <li><b>常量时间比较</b>：避免攻击者通过响应时间差异逐字节试探令牌。</li>
 * </ul>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;

    public AuthTokenFilter(AuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void logAuthState() {
        if (isBlankToken()) {
            log.warn("xunwu.auth.token 未配置 —— 接口鉴权已关闭，任何能访问本服务的人都能读写全部数据；"
                    + "生产环境请务必设置 XUNWU_AUTH_TOKEN");
        } else {
            log.info("接口鉴权已启用（xunwu.auth.token）");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // 未配置 token：鉴权关闭，直接放行（本地开发模式）
        if (isBlankToken()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isWhitelisted(request) || matchesToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("拒绝未授权请求: {} {}", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(401, "未授权：缺少或无效的访问令牌")));
    }

    private boolean matchesToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.length() <= BEARER_PREFIX.length()) {
            return false;
        }
        // regionMatches(ignoreCase) 容忍 "bearer" 小写写法
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }
        String provided = header.substring(BEARER_PREFIX.length()).trim();
        if (provided.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                properties.getToken().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 白名单：健康检查（供 Docker / K8s 探针使用）与 CORS 预检。
     *
     * <p>不放行 actuator：{@code /actuator/health} 同样会暴露运行状态，
     * 探针直接用 {@code /api/health} 即可。</p>
     */
    private boolean isWhitelisted(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri.endsWith("/api/health") || uri.equals("/error");
    }

    private boolean isBlankToken() {
        String token = properties.getToken();
        return token == null || token.isBlank();
    }
}
