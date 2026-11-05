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

/** 新建 / 更新入参。toolIds 直接传数组，由后端归一化为 JSONB。 */
export interface AgentPayload {
  name: string
  description?: string
  systemPrompt?: string
  modelId: string
  toolIds: string[]
  maxSteps: number
  status: string
}

export const agentApi = {
  list: () => request<Agent[]>('/agents'),
  get: (id: number) => request<Agent>(`/agents/${id}`),
  create: (data: AgentPayload) =>
    request<Agent>('/agents', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: Partial<AgentPayload>) =>
    request<void>(`/agents/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id: number) => request<void>(`/agents/${id}`, { method: 'DELETE' }),
}
