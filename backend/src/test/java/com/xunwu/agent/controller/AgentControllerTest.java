package com.xunwu.agent.controller;

import com.xunwu.agent.common.GlobalExceptionHandler;
import com.xunwu.agent.entity.Agent;
import com.xunwu.agent.mapper.AgentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link AgentController} 单元测试。
 *
 * <p>覆盖入参校验（名称必填 / 长度上限 / maxSteps 范围）与新增时的默认值补齐。</p>
 *
 * @author xunwu
 */
class AgentControllerTest {

    private AgentMapper agentMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentMapper = mock(AgentMapper.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentController(agentMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("智能体名称不能为空"));

        verify(agentMapper, never()).insert(any());
    }

    @Test
    void create_oversizedName_returns400() throws Exception {
        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(129) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("智能体名称不能超过 128 个字符"));

        verify(agentMapper, never()).insert(any());
    }

    @Test
    void create_nameExactlyAtLimit_succeeds() throws Exception {
        givenInsertBackfillsId();
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "x".repeat(128), "ACTIVE", 8));

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(128) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void create_invalidMaxSteps_returns400() throws Exception {
        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"助手\",\"maxSteps\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("maxSteps 必须在 1 ~ 50 之间"));

        verify(agentMapper, never()).insert(any());
    }

    @Test
    void create_invalidStatus_returns400() throws Exception {
        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"助手\",\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("status 只能是 ACTIVE 或 INACTIVE"));

        verify(agentMapper, never()).insert(any());
    }

    @Test
    void create_valid_appliesDefaultsAndReturns() throws Exception {
        givenInsertBackfillsId();
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "助手", "ACTIVE", 8));

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"助手\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("助手"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.maxSteps").value(8));

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentMapper).insert(captor.capture());
        // tool_ids 为 NOT NULL，未传时必须落 '[]'
        assertEquals("[]", captor.getValue().getToolIds());
    }

    @Test
    void create_toolIdsDeduplicatedAndSerialized() throws Exception {
        givenInsertBackfillsId();
        when(agentMapper.selectById(1L)).thenReturn(agent(1L, "助手", "ACTIVE", 8));

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"助手\",\"toolIds\":[\"calculator\",\" calculator \",\"calculator\",\"\"]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentMapper).insert(captor.capture());
        assertEquals("[\"calculator\"]", captor.getValue().getToolIds());
    }

    @Test
    void detail_unknown_returns404() throws Exception {
        when(agentMapper.selectById(9L)).thenReturn(null);

        mockMvc.perform(get("/api/agents/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void update_unknown_returns404() throws Exception {
        when(agentMapper.selectById(9L)).thenReturn(null);

        mockMvc.perform(put("/api/agents/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"助手\"}"))
                .andExpect(status().isNotFound());

        verify(agentMapper, never()).update(any());
    }

    @Test
    void delete_unknown_returns404() throws Exception {
        when(agentMapper.selectById(9L)).thenReturn(null);

        mockMvc.perform(delete("/api/agents/9"))
                .andExpect(status().isNotFound());

        verify(agentMapper, never()).deleteById(any());
    }

    // ==================== 辅助 ====================

    private void givenInsertBackfillsId() {
        when(agentMapper.insert(any(Agent.class))).thenAnswer(inv -> {
            inv.getArgument(0, Agent.class).setId(1L);
            return 1;
        });
    }

    private Agent agent(Long id, String name, String status, Integer maxSteps) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setStatus(status);
        agent.setMaxSteps(maxSteps);
        return agent;
    }
}
