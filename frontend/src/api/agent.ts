/**
 * 智能体 API
 */

import { request } from './request'

export interface Agent {
  id: number
  name: string
  description?: string
  systemPrompt?: string
  avatar?: string
  modelId: string
  toolIds?: string
  maxSteps: number
  status: string
  createdAt?: string
  updatedAt?: string
}

export const agentApi = {
  list: () => request<Agent[]>('/agents'),
  get: (id: number) => request<Agent>(`/agents/${id}`),
  create: (data: Partial<Agent>) =>
    request<Agent>('/agents', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: Partial<Agent>) =>
    request<void>(`/agents/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id: number) => request<void>(`/agents/${id}`, { method: 'DELETE' }),
}
