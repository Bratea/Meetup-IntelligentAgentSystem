package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查
 *
 * @author xunwu
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Value("${spring.application.name:xunwu-agent}")
    private String applicationName;

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", applicationName);
        data.put("status", "UP");
        data.put("timestamp", System.currentTimeMillis());
        return ApiResponse.ok(data);
    }
}
