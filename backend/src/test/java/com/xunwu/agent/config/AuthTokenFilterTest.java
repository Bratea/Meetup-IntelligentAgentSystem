package com.xunwu.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthTokenFilter} 单元测试。
 *
 * <p>重点验证四条边界：</p>
 * <ul>
 *   <li>未配置 token 时鉴权关闭（本地开发模式）；</li>
 *   <li>缺少 / 错误令牌一律 401，且响应体结构与 {@code ApiResponse} 一致，
 *       前端能直接读出可读文案；</li>
 *   <li>健康检查白名单放行，Docker / K8s 探针不受影响；</li>
 *   <li>CORS 预检放行，否则浏览器跨域请求在鉴权之前就被拦下。</li>
 * </ul>
 *
 * @author xunwu
 */
class AuthTokenFilterTest {

    private static final String TOKEN = "test-secret-token";

    private AuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new ProbeController())
                .addFilter(new AuthTokenFilter(properties, new ObjectMapper()))
                .build();
    }

    @Test
    void blankToken_disablesAuth() throws Exception {
        // 未配置 token = 鉴权关闭，所有接口直接放行
        mockMvc().perform(get("/probe/secured"))
                .andExpect(status().isOk());
    }

    @Test
    void missingToken_returns401() throws Exception {
        properties.setToken(TOKEN);

        mockMvc().perform(get("/probe/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未授权：缺少或无效的访问令牌"))
                // RFC 6750：Bearer 挑战
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void wrongToken_returns401() throws Exception {
        properties.setToken(TOKEN);

        mockMvc().perform(get("/probe/secured").header("Authorization", "Bearer nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyBearerToken_returns401() throws Exception {
        properties.setToken(TOKEN);

        mockMvc().perform(get("/probe/secured").header("Authorization", "Bearer  "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctToken_passes() throws Exception {
        properties.setToken(TOKEN);

        mockMvc().perform(get("/probe/secured").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void lowercaseBearerScheme_passes() throws Exception {
        // scheme 大小写不敏感（RFC 7235）
        properties.setToken(TOKEN);

        mockMvc().perform(get("/probe/secured").header("Authorization", "bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpoint_isWhitelisted() throws Exception {
        properties.setToken(TOKEN);

        // 健康检查必须放行：Docker / K8s 探针没有令牌
        mockMvc().perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void optionsRequest_isWhitelisted() throws Exception {
        properties.setToken(TOKEN);

        // CORS 预检不带凭据，必须在鉴权之前放行
        mockMvc().perform(options("/probe/secured"))
                .andExpect(status().isOk());
    }

    /** 仅用于触发过滤器的最小控制器 */
    @RestController
    static class ProbeController {

        @GetMapping("/probe/secured")
        String secured() {
            return "ok";
        }

        @GetMapping("/api/health")
        String health() {
            return "UP";
        }
    }
}
