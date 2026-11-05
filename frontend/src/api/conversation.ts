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
  delete: (id: number) => request<void>(`/conversations/${id}`, { method: 'DELETE' }),
}

export const messageApi = {
  list: (conversationId: number) =>
    request<Message[]>(`/conversations/${conversationId}/messages`),
}
