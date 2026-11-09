package com.xunwu.agent.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CalculatorTool} 单元测试
 *
 * @author xunwu
 */
class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String args) throws Exception {
        return mapper.readValue(tool.call(args, null), Map.class);
    }

    private double result(String args) throws Exception {
        return ((Number) call(args).get("result")).doubleValue();
    }

    @Test
    void basicArithmetic() throws Exception {
        assertEquals(20.0, result("{\"expression\":\"(2+3)*4\"}"), 1e-9);
        assertEquals(6.0, result("{\"expression\":\"2*3\"}"), 1e-9);
        assertEquals(1.0, result("{\"expression\":\"5-4\"}"), 1e-9);
        assertEquals(2.5, result("{\"expression\":\"5/2\"}"), 1e-9);
    }

    @Test
    void negativeAndUnarySign() throws Exception {
        assertEquals(-2.0, result("{\"expression\":\"-5+3\"}"), 1e-9);
        assertEquals(-6.0, result("{\"expression\":\"2*-3\"}"), 1e-9);
        assertEquals(5.0, result("{\"expression\":\"-(-5)\"}"), 1e-9);
    }

    @Test
    void operatorPrecedence() throws Exception {
        // * 优先于 +
        assertEquals(14.0, result("{\"expression\":\"2+3*4\"}"), 1e-9);
        assertEquals(10.0, result("{\"expression\":\"2*3+4\"}"), 1e-9);
        // 括号改变优先级
        assertEquals(20.0, result("{\"expression\":\"(2+3)*4\"}"), 1e-9);
    }

    @Test
    void divisionByZero_returnsError() throws Exception {
        Map<String, Object> result = call("{\"expression\":\"1/0\"}");
        assertTrue(result.containsKey("error"));
    }

    @Test
    void invalidExpression_returnsError() throws Exception {
        assertTrue(call("{\"expression\":\"1+\"}").containsKey("error"));
        assertTrue(call("{\"expression\":\"abc\"}").containsKey("error"));
        assertTrue(call("{\"expression\":\"(1+2\"}").containsKey("error"));
    }

    @Test
    void missingOrNullExpression_returnsError() throws Exception {
        assertTrue(call("{}").containsKey("error"));
        assertTrue(call("{\"expression\":\"\"}").containsKey("error"));
        assertTrue(call(null).containsKey("error"));
    }
}
