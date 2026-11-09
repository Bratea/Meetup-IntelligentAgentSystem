package com.xunwu.agent.controller;

import com.xunwu.agent.common.GlobalExceptionHandler;
import com.xunwu.agent.entity.Message;
import com.xunwu.agent.mapper.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link MessageController} 单元测试。
 *
 * <p>重点验证 P22 引入的分页语义：消息按「最新一页」翻页
 * （page=1 取最新的 size 条），但返回给前端时必须翻转为时间升序 ——
 * SQL 为了拿最新一页是倒序查的，直接返回会让聊天记录上下颠倒。</p>
 *
 * @author xunwu
 */
class MessageControllerTest {

    private MessageMapper mapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mapper = mock(MessageMapper.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MessageController(mapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsLatestPageInChronologicalOrder() throws Exception {
        // SQL 倒序返回最新一页
        when(mapper.selectPageByConversationId(eq(7L), eq(0), anyInt()))
                .thenReturn(List.of(message(2L), message(1L)));
        when(mapper.countByConversationId(7L)).thenReturn(2L);

        mockMvc.perform(get("/api/conversations/7/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 返回给前端时必须翻转为时间升序
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[1].id").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                // 消息列表默认每页 200 条（比通用列表长得多）
                .andExpect(jsonPath("$.data.size").value(200));

        verify(mapper).selectPageByConversationId(7L, 0, 200);
        verify(mapper).countByConversationId(7L);
    }

    @Test
    void list_secondPage_offsetsFromTheEnd() throws Exception {
        when(mapper.selectPageByConversationId(eq(7L), eq(200), eq(200)))
                .thenReturn(List.of());
        when(mapper.countByConversationId(7L)).thenReturn(450L);

        mockMvc.perform(get("/api/conversations/7/messages")
                        .param("page", "2").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        // page=2 = 从最新往回数第 200 条开始
        verify(mapper).selectPageByConversationId(7L, 200, 200);
    }

    @Test
    void list_oversizedPageSize_isCappedAt500() throws Exception {
        when(mapper.selectPageByConversationId(eq(7L), eq(0), anyInt()))
                .thenReturn(List.of());
        when(mapper.countByConversationId(7L)).thenReturn(0L);

        mockMvc.perform(get("/api/conversations/7/messages").param("size", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(500));

        verify(mapper).selectPageByConversationId(7L, 0, 500);
    }

    @Test
    void list_emptyConversation_returnsEmptyPage() throws Exception {
        when(mapper.selectPageByConversationId(eq(7L), eq(0), anyInt()))
                .thenReturn(List.of());
        when(mapper.countByConversationId(7L)).thenReturn(0L);

        mockMvc.perform(get("/api/conversations/7/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    private Message message(Long id) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(7L);
        message.setRole("user");
        message.setContent("m" + id);
        return message;
    }
}
