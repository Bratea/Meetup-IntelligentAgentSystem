package com.xunwu.agent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型元信息
 *
 * @author xunwu
 */
@Data
public class ModelInfo {

    /** 模型标识，如 deepseek-chat / glm-4-plus / qwen2.5:7b */
    private String id;

    /** 展示名称 */
    private String name;

    /** 供应方：deepseek / zhipu / ollama */
    private String provider;

    /** 模型类型：chat / embedding */
    private String type;

    /** 描述 */
    private String description;

    /** 是否可用（本地模型已安装 / 远端配置了 API Key） */
    private boolean available;

    /** 是否默认模型 */
    private boolean defaultModel;

    public static ModelInfo of(String id, String name, String provider,
                               String type, String description) {
        return new ModelInfo(id, name, provider, type, description, true, false);
    }

    public ModelInfo(String id, String name, String provider, String type,
                     String description, boolean available, boolean defaultModel) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.type = type;
        this.description = description;
        this.available = available;
        this.defaultModel = defaultModel;
    }

    public ModelInfo() {
    }

    public static List<ModelInfo> defaults() {
        List<ModelInfo> list = new ArrayList<>();
        list.add(new ModelInfo("deepseek-chat", "DeepSeek V3", "deepseek", "chat",
                "DeepSeek 对话模型（OpenAI 兼容协议）", true, false));
        list.add(new ModelInfo("glm-4-plus", "智谱 GLM-4-Plus", "zhipu", "chat",
                "智谱 GLM 对话模型", true, false));
        list.add(new ModelInfo("qwen2.5:7b", "Qwen2.5 7B", "ollama", "chat",
                "Ollama 本地对话模型", true, true));
        list.add(new ModelInfo("bge-m3", "BGE-M3 向量模型", "ollama", "embedding",
                "Ollama 本地向量化模型（1024 维）", true, true));
        return list;
    }
}
