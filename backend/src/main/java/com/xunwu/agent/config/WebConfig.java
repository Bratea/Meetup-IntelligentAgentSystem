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
     * <p>超时是<b>整条 SSE 流</b>的上限，而一次对话会跑多步、每步各有一次模型调用（单次调用上限 10 分钟）。若按单步时长设成 10 分钟，多步对话会被 MVC 提前截断。这里取 30 分钟作为整体上限：足够覆盖多步对话，同时避免异常情况下异步任务无限期挂住（客户端断连仍由 {@code FluxSink.onCancel} 提前结束）。</p>
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor);
        configurer.setDefaultTimeout(1_800_000L);
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
