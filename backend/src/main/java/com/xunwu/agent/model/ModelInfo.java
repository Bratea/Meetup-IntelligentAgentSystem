package com.xunwu.agent.model;

import lombok.Data;


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

}
