/**
 * 智能体 API
 */

import { request, type PageResult } from './request'

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
  /** 分页列表；不传参数时后端按默认（第 1 页 / 每页 20 条）处理 */
  list: (page?: number, size?: number) => {
    const query = new URLSearchParams()
    if (page !== undefined) query.set('page', String(page))
    if (size !== undefined) query.set('size', String(size))
    const qs = query.toString()
    return request<PageResult<Agent>>(`/agents${qs ? `?${qs}` : ''}`)
  },
  get: (id: number) => request<Agent>(`/agents/${id}`),
  create: (data: AgentPayload) =>
    request<Agent>('/agents', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: Partial<AgentPayload>) =>
    request<void>(`/agents/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id: number) => request<void>(`/agents/${id}`, { method: 'DELETE' }),
}
