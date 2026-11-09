package com.xunwu.agent.controller;

import com.xunwu.agent.common.ApiResponse;
import com.xunwu.agent.common.BizException;
import com.xunwu.agent.entity.Agent;
import com.xunwu.agent.mapper.AgentMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;

/**
 * 智能体管理接口
 *
 * @author xunwu
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final AgentMapper agentMapper;

    public AgentController(AgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    /** 智能体列表 */
    @GetMapping
    public ApiResponse<List<Agent>> list() {
        return ApiResponse.ok(agentMapper.selectAll());
    }

    /** 智能体详情 */
    @GetMapping("/{id}")
    public ApiResponse<Agent> detail(@PathVariable Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw BizException.notFound("智能体不存在: " + id);
        }
        return ApiResponse.ok(agent);
    }

    /** 新建智能体 */
    @PostMapping
    public ApiResponse<Agent> create(@RequestBody AgentRequest request) {
        Agent agent = new Agent();
        applyRequest(agent, request, true);
        // 新增时补齐必填默认值，避免触发 DB 非空约束。
        // modelId 不强制：为空时由 INSERT 的 COALESCE 落到 'qwen2.5:7b'。
        if (agent.getStatus() == null || agent.getStatus().isBlank()) {
            agent.setStatus("ACTIVE");
        }
        if (agent.getMaxSteps() == null || agent.getMaxSteps() <= 0) {
            agent.setMaxSteps(8);
        }
        agentMapper.insert(agent);
        return ApiResponse.ok(agentMapper.selectById(agent.getId()));
    }

    /** 更新智能体 */
    @PutMapping("/{id}")
    public ApiResponse<Agent> update(@PathVariable Long id, @RequestBody AgentRequest request) {
        Agent existing = agentMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("智能体不存在: " + id);
        }
        Agent agent = new Agent();
        agent.setId(id);
        applyRequest(agent, request, false);
        agentMapper.update(agent);
        return ApiResponse.ok(agentMapper.selectById(id));
    }

    /** 删除智能体（会话与消息由外键级联删除） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (agentMapper.selectById(id) == null) {
            throw BizException.notFound("智能体不存在: " + id);
        }
        agentMapper.deleteById(id);
        return ApiResponse.ok();
    }

    /**
     * 将请求体字段写入实体。
     * 使用专用请求对象而非实体，避免客户端注入 id / createdAt 等字段。
     * 部分字段为 null 时保持原值（动态 SET 不会覆盖），因此支持局部更新。
     */
    private void applyRequest(Agent agent, AgentRequest request, boolean isCreate) {
        if (request.getName() != null && !request.getName().isBlank()) {
            agent.setName(request.getName().trim());
        } else if (isCreate) {
            throw BizException.badRequest("智能体名称不能为空");
        }
        // 与表结构对齐，避免超长值触发 DB 约束异常（500）
        assertFitsColumn(agent.getName(), 128, "智能体名称");
        if (request.getDescription() != null) {
            agent.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getSystemPrompt() != null) {
            agent.setSystemPrompt(trimToNull(request.getSystemPrompt()));
        }
        if (request.getAvatar() != null) {
            agent.setAvatar(trimToNull(request.getAvatar()));
        }
        if (request.getModelId() != null) {
            agent.setModelId(trimToNull(request.getModelId()));
        }
        assertFitsColumn(agent.getDescription(), 512, "智能体描述");
        assertFitsColumn(agent.getAvatar(), 255, "头像地址");
        assertFitsColumn(agent.getModelId(), 64, "模型标识");
        // modelId 为空时由数据库默认值 qwen2.5:7b 兜底，不强制客户端必填
        // tool_ids 为 JSONB NOT NULL，空值统一落 '[]'
        if (request.getToolIds() != null) {
            agent.setToolIds(toJsonArray(request.getToolIds()));
        } else if (isCreate) {
            agent.setToolIds("[]");
        }
        if (request.getMaxSteps() != null) {
            if (request.getMaxSteps() < 1 || request.getMaxSteps() > 50) {
                throw BizException.badRequest("maxSteps 必须在 1 ~ 50 之间");
            }
            agent.setMaxSteps(request.getMaxSteps());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toUpperCase();
            if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
                throw BizException.badRequest("status 只能是 ACTIVE 或 INACTIVE");
            }
            agent.setStatus(status);
        }
    }

    /**
     * 归一化工具列表：去空白、去重、丢弃空值，落库为 JSON 数组字符串。
     * 未注册的名称由 AgentExecutor 侧过滤，这里只做格式收敛。
     */
    private String toJsonArray(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "[]";
        }
        List<String> cleaned = new java.util.ArrayList<>();
        for (String name : names) {
            if (name == null) {
                continue;
            }
            String text = name.trim();
            if (!text.isEmpty() && !cleaned.contains(text)) {
                cleaned.add(text);
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(cleaned);
        } catch (JsonProcessingException e) {
            log.warn("toolIds 序列化失败，已置为空数组: {}", names);
            return "[]";
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 校验字符串字段不超过列宽，超长直接返回 400（否则会触发 DB 约束异常变成 500） */
    private void assertFitsColumn(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw BizException.badRequest(label + "不能超过 " + max + " 个字符");
        }
    }

    // ---------- 请求体 ----------

    @Data
    public static class AgentRequest {
        private String name;
        private String description;
        private String systemPrompt;
        private String avatar;
        private String modelId;
        private List<String> toolIds;
        private Integer maxSteps;
        private String status;
    }
}
