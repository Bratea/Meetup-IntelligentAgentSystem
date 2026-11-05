package com.xunwu.agent.tool.builtin;

import com.xunwu.agent.config.XunwuProperties;
import com.xunwu.agent.entity.KnowledgeSearchResult;
import com.xunwu.agent.rag.KnowledgeSearchService;
import com.xunwu.agent.tool.ToolDefinitionInfo;
import com.xunwu.agent.tool.ToolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置工具：知识库检索
 *
 * <p>固定工具（FIXED），所有智能体默认可用。
 * 与"自动注入知识库上下文"互补：自动注入解决首轮问答，
 * 本工具让模型在需要时自主发起二次检索。</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class KnowledgeSearchTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KnowledgeSearchService knowledgeSearchService;
    private final XunwuProperties properties;

    public KnowledgeSearchTool(KnowledgeSearchService knowledgeSearchService,
                               XunwuProperties properties) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.properties = properties;
    }

    /**
     * @param args {"query": "检索内容", "topK": 3}
     */
    public String call(String args, ToolContext context) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> params = parseArgs(args);
            String query = (String) params.get("query");
            if (query == null || query.isBlank()) {
                return toJson(Map.of("error", "缺少 query 参数"));
            }
            int topK = parseInt(params.get("topK"), properties.getRag().getTopK());
            topK = Math.max(1, Math.min(topK, 10));

            List<KnowledgeSearchResult> results = knowledgeSearchService.search(query, topK);

            List<Map<String, Object>> items = new java.util.ArrayList<>();
            for (KnowledgeSearchResult r : results) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("documentName", r.getDocumentName());
                item.put("content", r.getContent());
                item.put("similarity", r.getSimilarity());
                items.add(item);
            }
            log.info("知识库检索工具执行完成: hits={}, cost={}ms",
                    items.size(), System.currentTimeMillis() - start);
            return toJson(Map.of("query", query, "count", items.size(), "results", items));
        } catch (Exception e) {
            log.warn("知识库检索工具执行失败: {}", e.getMessage());
            return toJson(Map.of("error", "知识库检索失败: " + e.getMessage()));
        }
    }

    private Map<String, Object> parseArgs(String args) {
        if (args == null || args.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(args, Map.class);
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private int parseInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String toJson(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return "{\"error\":\"结果序列化失败\"}";
        }
    }

    public static ToolDefinitionInfo definition() {
        return new ToolDefinitionInfo(
                "knowledge_search",
                "在知识库中检索相关内容。当用户的问题需要依赖上传的文档资料（产品手册、规章制度、技术文档等）时使用。",
                ToolType.FIXED,
                "{\"type\":\"object\",\"properties\":{"
                        + "\"query\":{\"type\":\"string\",\"description\":\"检索内容，建议提炼为关键词或短句\"},"
                        + "\"topK\":{\"type\":\"integer\",\"description\":\"返回片段数量，默认 4，最大 10\"}}"
                        + ",\"required\":[\"query\"]}",
                true);
    }
}
