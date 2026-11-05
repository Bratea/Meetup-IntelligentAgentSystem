package com.xunwu.agent.tool.builtin;

import com.xunwu.agent.tool.ToolDefinitionInfo;
import com.xunwu.agent.tool.ToolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 内置工具：计算器
 *
 * <p>支持 {@code + - * /}、括号与一元负号，如 {@code -5 + 3}、{@code 2 * -3}。</p>
 *
 * @author xunwu
 */
@Slf4j
@Component
public class CalculatorTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String call(String args, ToolContext context) {
        log.info("调用工具 CalculatorTool, args={}", args);
        try {
            String expression = OBJECT_MAPPER.readTree(args == null ? "{}" : args)
                    .path("expression")
                    .asText("");
            if (expression.isBlank()) {
                return toJson(Map.of("error", "缺少 expression 参数"));
            }
            double result = new ExpressionEvaluator().evaluate(expression);
            return toJson(Map.of("expression", expression, "result", result));
        } catch (Exception e) {
            log.warn("计算失败: {}", e.getMessage());
            return toJson(Map.of("error", "计算失败: " + e.getMessage()));
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return "{\"error\":\"结果序列化失败\"}";
        }
    }

    /**
     * 简单四则表达式求值（支持 + - * / 、括号与一元正负号）
     */
    static class ExpressionEvaluator {
        private String expr;
        private int pos;

        double evaluate(String expression) {
            this.expr = expression.replaceAll("\\s+", "");
            this.pos = 0;
            if (expr.isEmpty()) {
                throw new IllegalArgumentException("表达式不能为空");
            }
            double value = parseExpression();
            if (pos < expr.length()) {
                throw new IllegalArgumentException("无法解析的内容: " + expr.substring(pos));
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("计算结果超出可表示范围");
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (pos < expr.length()) {
                char op = expr.charAt(pos);
                if (op == '+') {
                    pos++;
                    value += parseTerm();
                } else if (op == '-') {
                    pos++;
                    value -= parseTerm();
                } else {
                    break;
                }
            }
            return value;
        }

        private double parseTerm() {
            double value = parseFactor();
            while (pos < expr.length()) {
                char op = expr.charAt(pos);
                if (op == '*') {
                    pos++;
                    value *= parseFactor();
                } else if (op == '/') {
                    pos++;
                    double divisor = parseFactor();
                    if (divisor == 0) {
                        throw new IllegalArgumentException("除数不能为 0");
                    }
                    value /= divisor;
                } else {
                    break;
                }
            }
            return value;
        }

        private double parseFactor() {
            if (pos >= expr.length()) {
                throw new IllegalArgumentException("表达式意外结束");
            }
            char c = expr.charAt(pos);
            // 一元负号 / 正号
            if (c == '-') {
                pos++;
                return -parseFactor();
            }
            if (c == '+') {
                pos++;
                return parseFactor();
            }
            if (c == '(') {
                pos++;
                double value = parseExpression();
                if (pos >= expr.length() || expr.charAt(pos) != ')') {
                    throw new IllegalArgumentException("括号不匹配");
                }
                pos++;
                return value;
            }
            int start = pos;
            while (pos < expr.length()
                    && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("表达式格式错误: " + expr);
            }
            return Double.parseDouble(expr.substring(start, pos));
        }
    }

    public static ToolDefinitionInfo definition() {
        return new ToolDefinitionInfo(
                "calculator",
                "计算数学表达式。支持 + - * / 、括号与负数，如 (2+3)*4、-5+3。当用户要求计算数值时使用。",
                ToolType.OPTIONAL,
                "{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\",\"description\":\"数学表达式，如 (2+3)*4\"}}}",
                true);
    }
}
