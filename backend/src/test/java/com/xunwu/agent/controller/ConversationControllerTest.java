package com.xunwu.agent.controller;

import com.xunwu.agent.common.GlobalExceptionHandler;
import com.xunwu.agent.entity.Agent;
import com.xunwu.agent.entity.Conversation;
import com.xunwu.agent.mapper.AgentMapper;
import com.xunwu.agent.mapper.ConversationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link ConversationController} 单元测试。
 *
 * <p>重点验证 P14 引入的 DTO 隔离：客户端无法通过请求体注入
 * id / createdAt / updatedAt 等内部字段；以及状态白名单与存在性校验。</p>
 *
 * @author xunwu
 */
class ConversationControllerTest {

    private ConversationMapper mapper;
    private AgentMapper agentMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mapper = mock(ConversationMapper.class);
        agentMapper = mock(AgentMapper.class);
        // 默认智能体存在；具体用例可覆盖为 null
        when(agentMapper.selectById(anyLong())).thenReturn(new Agent());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConversationController(mapper, agentMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 创建 ====================

    @Test
    void create_persistsOnlyWhitelistedFields() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": 7,
                                  "title": "我的会话",
                                  "id": 999,
                                  "createdAt": "2020-01-01T00:00:00Z",
                                  "updatedAt": "2020-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(mapper).insert(captor.capture());
        Conversation saved = captor.getValue();
        assertEquals(7L, saved.getAgentId());
        assertEquals("我的会话", saved.getTitle());
        assertEquals("ACTIVE", saved.getStatus());
        // 注入的 id=999 必须被丢弃（实体是新建的，从未接收该字段）
        assertNull(saved.getId());
        assertNull(saved.getCreatedAt());
        assertNull(saved.getUpdatedAt());
    }

    @Test
    void create_missingAgentId_returns400() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"无智能体\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(mapper, never()).insert(any());
    }

    @Test
    void create_unknownAgentId_returns404() throws Exception {
        when(agentMapper.selectById(404L)).thenReturn(null);

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":404,\"title\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        // 不能直接插库：conversation.agent_id 是 NOT NULL 外键，会触发约束异常
        verify(mapper, never()).insert(any());
    }

    @Test
    void create_blankTitle_fallsBackToDefault() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1,\"title\":\"   \"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(mapper).insert(captor.capture());
        assertEquals("新会话", captor.getValue().getTitle());
    }

    @Test
    void create_invalidStatus_returns400() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1,\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("status 只能是 ACTIVE 或 ARCHIVED"));
    }

    @Test
    void create_oversizedTitle_returns400() throws Exception {
        String title = "x".repeat(300);
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":1,\"title\":\"" + title + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== 更新 ====================

    @Test
    void update_partialTitle_onlyChangesTitle() throws Exception {
        when(mapper.selectById(1L)).thenReturn(existing());
        when(mapper.update(any(Conversation.class))).thenReturn(1);

        mockMvc.perform(put("/api/conversations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改名了\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(mapper).update(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals("改名了", captor.getValue().getTitle());
        // 未提交的字段必须保持 null，由动态 SQL 跳过
        assertNull(captor.getValue().getStatus());
        assertNull(captor.getValue().getAgentId());
    }

    @Test
    void update_invalidStatus_returns400() throws Exception {
        when(mapper.selectById(1L)).thenReturn(existing());

        mockMvc.perform(put("/api/conversations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("status 只能是 ACTIVE 或 ARCHIVED"));

        verify(mapper, never()).update(any());
    }

    @Test
    void update_normalizesLowercaseStatus() throws Exception {
        when(mapper.selectById(1L)).thenReturn(existing());
        when(mapper.update(any(Conversation.class))).thenReturn(1);

        mockMvc.perform(put("/api/conversations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"archived\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(mapper).update(captor.capture());
        assertEquals("ARCHIVED", captor.getValue().getStatus());
    }

    @Test
    void update_withAgentId_changesAgent() throws Exception {
        when(mapper.selectById(1L)).thenReturn(existing());
        when(mapper.update(any(Conversation.class))).thenReturn(1);

        mockMvc.perform(put("/api/conversations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":9}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(mapper).update(captor.capture());
        assertEquals(9L, captor.getValue().getAgentId());
        // 未提交的字段必须保持 null，由动态 SQL 跳过
        assertNull(captor.getValue().getTitle());
    }

    @Test
    void update_withUnknownAgentId_returns404() throws Exception {
        when(mapper.selectById(1L)).thenReturn(existing());
        when(agentMapper.selectById(404L)).thenReturn(null);

        mockMvc.perform(put("/api/conversations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":404}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        // 不能带着不存在的 agentId 落库：conversation.agent_id 是外键
        verify(mapper, never()).update(any());
    }

    @Test
    void update_withoutAgentId_leavesAgentUntouched() throws Exception {
        when(mapper.selectById(1L)).thenReturn(existing());
        when(mapper.update(any(Conversation.class))).thenReturn(1);

        mockMvc.perform(put("/api/conversations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改名\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(mapper).update(captor.capture());
        assertNull(captor.getValue().getAgentId());
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        when(mapper.selectById(99L)).thenReturn(null);

        mockMvc.perform(put("/api/conversations/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"不存在\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 查询 / 删除 ====================

    @Test
    void detail_unknownId_returns404() throws Exception {
        when(mapper.selectById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/conversations/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        when(mapper.selectById(99L)).thenReturn(null);

        mockMvc.perform(delete("/api/conversations/99"))
                .andExpect(status().isNotFound());

        verify(mapper, never()).deleteById(any());
    }

    @Test
    void list_byAgentId_returnsPage() throws Exception {
        when(mapper.selectPageByAgentId(eq(3L), anyInt(), anyInt()))
                .thenReturn(List.of(existing()));
        when(mapper.countByAgentId(3L)).thenReturn(1L);

        mockMvc.perform(get("/api/conversations").param("agentId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(mapper).selectPageByAgentId(eq(3L), eq(0), eq(20));
        verify(mapper).countByAgentId(3L);
        verify(mapper, never()).selectPageAll(anyInt(), anyInt());
    }

    @Test
    void list_withoutAgentId_returnsPage() throws Exception {
        when(mapper.selectPageAll(anyInt(), anyInt())).thenReturn(List.of(existing()));
        when(mapper.countAll()).thenReturn(1L);

        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(mapper).selectPageAll(0, 20);
    }

    @Test
    void list_oversizedPageSize_isCappedAt200() throws Exception {
        when(mapper.selectPageAll(anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countAll()).thenReturn(0L);

        mockMvc.perform(get("/api/conversations").param("size", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(200));

        // 单页上限 200：不允许 size=99999 一次拉走整表
        verify(mapper).selectPageAll(0, 200);
    }

    @Test
    void list_nonPositiveParams_fallBackToDefaults() throws Exception {
        when(mapper.selectPageAll(anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countAll()).thenReturn(0L);

        mockMvc.perform(get("/api/conversations").param("page", "0").param("size", "-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        // 分页参数写错应按默认分页，而不是报错
        verify(mapper).selectPageAll(0, 20);
    }

    @Test
    void list_thirdPage_offsetsCorrectly() throws Exception {
        when(mapper.selectPageAll(anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countAll()).thenReturn(0L);

        mockMvc.perform(get("/api/conversations").param("page", "3").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(3));

        // (3-1) * 10 = 20
        verify(mapper).selectPageAll(20, 10);
    }

    private Conversation existing() {
        Conversation c = new Conversation();
        c.setId(1L);
        c.setAgentId(7L);
        c.setTitle("原会话");
        c.setStatus("ACTIVE");
        return c;
    }
}
