/**
 * 会话与消息 API
 */

import { request } from './request'

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
  list: (agentId?: number) =>
    request<Conversation[]>(`/conversations${agentId ? `?agentId=${agentId}` : ''}`),
  get: (id: number) => request<Conversation>(`/conversations/${id}`),
  create: (data: Partial<Conversation>) =>
    request<Conversation>('/conversations', { method: 'POST', body: JSON.stringify(data) }),
  /** 更新会话（改名 / 归档），只提交需要变更的字段 */
  update: (id: number, data: { title?: string; status?: string }) =>
    request<Conversation>(`/conversations/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: number) => request<void>(`/conversations/${id}`, { method: 'DELETE' }),
}

export const messageApi = {
  list: (conversationId: number) =>
    request<Message[]>(`/conversations/${conversationId}/messages`),
}
