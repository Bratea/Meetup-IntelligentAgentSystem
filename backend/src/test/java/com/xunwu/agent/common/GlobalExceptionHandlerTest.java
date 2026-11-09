package com.xunwu.agent.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler} 单元测试。
 *
 * <p>重点验证两条约束：</p>
 * <ul>
 *   <li>数据约束冲突（外键 / 非空 / 唯一约束）必须翻译成可读的 400，
 *       而不是掉进兜底的 500 —— 这类问题基本都是客户端参数写错了；</li>
 *   <li>未知异常对外只暴露固定文案，内部细节只进日志。</li>
 * </ul>
 *
 * @author xunwu
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void dataIntegrityViolation_returns400WithReadableMessage() throws Exception {
        mockMvc.perform(get("/probe/fk"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("数据约束冲突：引用的关联数据不存在或状态不允许"));
    }

    @Test
    void unknownException_returns500WithoutLeakingDetail() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }

    /** 仅用于触发异常的最小控制器 */
    @RestController
    static class ProbeController {

        @GetMapping("/probe/fk")
        public void fk() {
            throw new DataIntegrityViolationException(
                    "Batch update failed: ERROR: insert or update on table \"conversation\" "
                            + "violates foreign key constraint \"fk_conversation_agent\"");
        }

        @GetMapping("/probe/boom")
        public void boom() {
            // 故意带上连接串，验证不会回给客户端
            throw new IllegalStateException("jdbc:postgresql://internal:5432/xunwu");
        }
    }
}
