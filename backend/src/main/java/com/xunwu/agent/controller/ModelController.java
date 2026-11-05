package com.xunwu.agent.model;

import com.xunwu.agent.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型管理接口
 *
 * @author xunwu
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ChatClientRegistry chatClientRegistry;

    public ModelController(ChatClientRegistry chatClientRegistry) {
        this.chatClientRegistry = chatClientRegistry;
    }

    /**
     * 获取模型列表
     */
    @GetMapping
    public ApiResponse<List<ModelInfo>> list() {
        return ApiResponse.ok(chatClientRegistry.listModels());
    }

    /**
     * 获取默认模型
     */
    @GetMapping("/default")
    public ApiResponse<String> defaultModel() {
        return ApiResponse.ok(chatClientRegistry.defaultModelId());
    }
}
