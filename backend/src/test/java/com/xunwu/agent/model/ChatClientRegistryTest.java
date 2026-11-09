package com.xunwu.agent.model;

import com.xunwu.agent.config.XunwuProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link ChatClientRegistry} 单元测试。
 *
 * <p>重点覆盖 LongCat（OpenAI 兼容协议）的注册逻辑：
 * 配置了 Key 才可用、未配置时降级为不可用、未知模型回退到默认模型。</p>
 *
 * @author xunwu
 */
@SuppressWarnings("unchecked")
class ChatClientRegistryTest {

    private ChatClientRegistry registry;

    @BeforeEach
    void setUp() {
        // 三个 provider 都返回 null，等价于"DeepSeek / 智谱 / Ollama 均未自动配置"
        registry = new ChatClientRegistry(
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                new XunwuProperties());
        // 指向不可达地址，避免单测依赖本机是否运行了 Ollama
        ReflectionTestUtils.setField(registry, "ollamaBaseUrl", "http://127.0.0.1:1");
    }

    private void configureLongcat(String apiKey, String model) {
        ReflectionTestUtils.setField(registry, "longcatApiKey", apiKey);
        ReflectionTestUtils.setField(registry, "longcatBaseUrl", "https://example.test/v1");
        ReflectionTestUtils.setField(registry, "longcatModel", model);
    }

    private ModelInfo find(String id) {
        return registry.listModels().stream()
                .filter(m -> id.equals(m.getId()))
                .findFirst()
                .orElse(null);
    }

    @Test
    void longcatRegisteredWhenApiKeyPresent() {
        configureLongcat("ak-test", "LongCat-2.5");
        registry.init();

        ModelInfo info = find("LongCat-2.5");
        assertNotNull(info, "LongCat 应被登记到模型列表");
        assertTrue(info.isAvailable(), "配置了 Key 后 LongCat 应可用");
        assertEquals("longcat", info.getProvider());
        assertTrue(registry.contains("LongCat-2.5"), "contains 应能解析已注册的 LongCat");
    }

    @Test
    void longcatUnavailableWhenApiKeyMissing() {
        configureLongcat("", "LongCat-2.5");
        registry.init();

        ModelInfo info = find("LongCat-2.5");
        assertNotNull(info, "即使未配置 Key，也应登记元信息供前端展示");
        assertFalse(info.isAvailable(), "未配置 Key 时 LongCat 应标记为不可用");
        assertFalse(registry.contains("LongCat-2.5"), "未配置 Key 时不应拿到 ChatClient");
    }

    @Test
    void blankModelFallsBackToDefault() {
        configureLongcat("", "LongCat-2.5");
        registry.init();

        assertEquals("qwen2.5:7b", registry.resolveModelId(null));
        assertEquals("qwen2.5:7b", registry.resolveModelId(""));
        assertEquals("qwen2.5:7b", registry.resolveModelId("  "));
    }

    @Test
    void unknownModelIsRejectedInsteadOfSilentlySwapped() {
        configureLongcat("", "LongCat-2.5");
        registry.init();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> registry.resolveModelId("no-such-model"));
        assertTrue(ex.getMessage().contains("no-such-model"));
        assertTrue(ex.getMessage().contains("可用模型"));

        // contains 是纯查询，未知模型返回 false 而不抛异常
        assertFalse(registry.contains("no-such-model"));
        assertFalse(registry.contains(null));
    }

    @Test
    void longcatModelIdIsConfigurable() {
        configureLongcat("ak-test", "LongCat-Flash");
        registry.init();

        ModelInfo info = find("LongCat-Flash");
        assertNotNull(info);
        assertTrue(info.isAvailable());
        assertTrue(registry.contains("LongCat-Flash"));
    }
}
