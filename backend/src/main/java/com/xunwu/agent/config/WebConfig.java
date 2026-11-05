package com.xunwu.agent.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：跨域、编码等
 *
 * <p>CORS 仅在前后端分离开发（Vite dev server）时需要；
 * 生产环境由 nginx 同源反代，无需跨域。允许的来源通过
 * {@code XUNWU_CORS_ALLOWED_ORIGINS} 配置，默认只放行本地开发端口。</p>
 *
 * @author xunwu
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${xunwu.cors.allowed-origins:http://localhost:5173,http://localhost:3000,"
            + "http://127.0.0.1:5173,http://127.0.0.1:3000}")
    private String[] allowedOrigins;

    private final ThreadPoolTaskExecutor mvcAsyncExecutor;

    public WebConfig(@Qualifier("mvcAsyncExecutor") ThreadPoolTaskExecutor mvcAsyncExecutor) {
        this.mvcAsyncExecutor = mvcAsyncExecutor;
    }

    /**
     * 配置 Spring MVC 异步执行器。
     *
     * <p>不配的话 Spring MVC 会用 {@code SimpleAsyncTaskExecutor}：
     * 每个请求新建线程、不复用、不限制并发，生产环境存在线程爆炸风险。</p>
     *
     * <p>超时必须大于 {@code AgentExecutor.MODEL_TIMEOUT}（10 分钟），
     * 否则流式响应会先被 MVC 掐断。</p>
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor);
        configurer.setDefaultTimeout(600_000L);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type", "Cache-Control", "Connection")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
