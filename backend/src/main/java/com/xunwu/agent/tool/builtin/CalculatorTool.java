package com.xunwu.agent.tool.builtin;

import com.xunwu.agent.tool.ToolType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 示例工具：计算器
 *
 * @author xunwu
 */
@Slf4j
@Component
public class CalculatorTool {

    public String call(String args, ToolContext context) {
        log.info("调用工具 CalculatorTool, args={}", args);
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(args);
            String expression = node.has("expression") ? node.get("expression").asText() : null;
            if (expression == null || expression.isBlank()) {
                return "{\"error\":\"缺少 expression 参数\"}";
            }
            double result = new ExpressionEvaluator().evaluate(expression);
            return mapper.writeValueAsString(Map.of(
                    "expression", expression,
                    "result", result));
        } catch (Exception e) {
            log.warn("计算失败: {}", e.getMessage());
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 简单四则表达式求值（支持 + - * / 括号）
     */
    static class ExpressionEvaluator {
        private String expr;
        private int pos;

        double evaluate(String expression) {
            this.expr = expression.replaceAll("\\s+", "");
            this.pos = 0;
            double value = parseExpression();
            if (pos < expr.length()) {
                throw new IllegalArgumentException("无法解析: " + expression);
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
            if (pos < expr.length() && expr.charAt(pos) == '(') {
                pos++;
                double value = parseExpression();
                if (pos >= expr.length() || expr.charAt(pos) != ')') {
                    throw new IllegalArgumentException("括号不匹配");
                }
                pos++;
                return value;
            }
            int start = pos;
            while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("表达式格式错误: " + expr);
            }
            return Double.parseDouble(expr.substring(start, pos));
        }
    }

    public static com.xunwu.agent.tool.ToolDefinitionInfo definition() {
        return new com.xunwu.agent.tool.ToolDefinitionInfo(
                "calculator",
                "计算数学表达式。支持 + - * / 和括号，如 (2+3)*4。当用户要求计算数值时使用。",
                ToolType.OPTIONAL,
                "{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\",\"description\":\"数学表达式，如 (2+3)*4\"}}}",
                true);
    }
}
