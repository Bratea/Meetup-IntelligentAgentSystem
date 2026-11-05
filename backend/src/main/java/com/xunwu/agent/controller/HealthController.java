package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查
 *
 * <p>检查数据库与 Ollama 两个核心依赖，任一不可用时返回 DOWN。
 * 用于部署探活与前端"服务不可用"提示。</p>
 *
 * @author xunwu
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final DataSource dataSource;
    private final HttpClient httpClient;

    @Value("${spring.application.name:xunwu-agent}")
    private String applicationName;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(PROBE_TIMEOUT)
                .build();
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", applicationName);

        boolean dbUp = checkDatabase();
        boolean ollamaUp = checkOllama();

        data.put("status", dbUp && ollamaUp ? "UP" : "DOWN");
        data.put("database", dbUp ? "UP" : "DOWN");
        data.put("ollama", ollamaUp ? "UP" : "DOWN");
        data.put("timestamp", System.currentTimeMillis());
        return ApiResponse.ok(data);
    }

    private boolean checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid((int) PROBE_TIMEOUT.toSeconds());
        } catch (Exception e) {
            log.warn("数据库健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkOllama() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            log.warn("Ollama 健康检查失败: {}", e.getMessage());
            return false;
        }
    }
}
