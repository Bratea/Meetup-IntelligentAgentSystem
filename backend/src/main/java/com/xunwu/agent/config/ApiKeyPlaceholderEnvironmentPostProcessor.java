package com.xunwu.agent.config;

import com.xunwu.agent.model.ChatClientRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

/**
 * 把空白的模型 API Key 环境变量改写为占位值。
 *
 * <p>Spring AI 的 {@code OpenAiChatAutoConfiguration} / {@code ZhiPuAiChatAutoConfiguration}
 * 都会对 api-key 做 {@code Assert.hasText} 校验；而 Spring 占位符
 * {@code ${VAR:default}} 只在变量<b>未定义</b>时才取默认值 —— 变量存在但为空串时
 * 仍然拿到空串，上下文会直接启动失败。docker-compose 用
 * {@code ${DEEPSEEK_API_KEY:-}} 传参时正是这种情况。</p>
 *
 * <p>这里把空白值统一改写为 {@link ChatClientRegistry#PLACEHOLDER_API_KEY}：
 * 既通过 Spring AI 的非空断言，又让 {@code ChatClientRegistry#hasText(String)}
 * 能正确识别出"未配置"，不会把占位值当成真实 Key。</p>
 *
 * @author xunwu
 */
public class ApiKeyPlaceholderEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 需要兜底的环境变量 -> 占位值 */
    private static final Map<String, String> BLANK_KEY_PLACEHOLDERS = Map.of(
            "DEEPSEEK_API_KEY", ChatClientRegistry.PLACEHOLDER_API_KEY,
            "ZHIPU_API_KEY", ChatClientRegistry.PLACEHOLDER_API_KEY);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        Map<String, Object> overrides = new HashMap<>();
        BLANK_KEY_PLACEHOLDERS.forEach((name, placeholder) -> {
            String value = environment.getProperty(name);
            if (value != null && value.isBlank()) {
                overrides.put(name, placeholder);
            }
        });
        if (overrides.isEmpty()) {
            return;
        }
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new MapPropertySource("xunwuApiKeyPlaceholder", overrides));
    }
}
