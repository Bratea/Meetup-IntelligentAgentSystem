/**
 * 会话与消息 API
 */

import { request, type PageResult } from './request'

export interface Conversation {
  id: number
  agentId: number
  title: string
  status: string
  createdAt?: string
  updatedAt?: string
}

export interface Message {
  id: number
  conversationId: number
  role: 'user' | 'assistant' | 'system' | 'tool' | string
  content: string
  model?: string
  toolCalls?: string
  createdAt?: string
}

export const conversationApi = {
  /** 分页列表；agentId 可选，用于只看某个智能体的会话 */
  list: (agentId?: number, page?: number, size?: number) => {
    const query = new URLSearchParams()
    if (agentId) query.set('agentId', String(agentId))
    if (page !== undefined) query.set('page', String(page))
    if (size !== undefined) query.set('size', String(size))
    const qs = query.toString()
    return request<PageResult<Conversation>>(`/conversations${qs ? `?${qs}` : ''}`)
  },
  get: (id: number) => request<Conversation>(`/conversations/${id}`),
  create: (data: Partial<Conversation>) =>
    request<Conversation>('/conversations', { method: 'POST', body: JSON.stringify(data) }),
  /** 更新会话（改所属智能体 / 改名 / 归档），只提交需要变更的字段 */
  update: (id: number, data: { agentId?: number; title?: string; status?: string }) =>
    request<Conversation>(`/conversations/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) => request<void>(`/conversations/${id}`, { method: 'DELETE' }),
}

export const messageApi = {
  /**
   * 分页消息列表。
   *
   * 后端按「最新一页」翻页：page=1 是最新的 size 条，返回时已是时间升序。
   * 默认每页 200 条（聊天历史比管理列表长得多）。
   */
  list: (conversationId: number, page = 1, size = 200) =>
    request<PageResult<Message>>(
      `/conversations/${conversationId}/messages?page=${page}&size=${size}`,
    ),
}
