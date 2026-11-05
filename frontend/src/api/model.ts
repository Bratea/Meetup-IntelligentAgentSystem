/**
 * 模型 API
 */

import { request } from './request'

export interface ModelInfo {
  id: string
  name: string
  provider: string
  type: string
  description: string
  available: boolean
  defaultModel: boolean
}

export const modelApi = {
  list: () => request<ModelInfo[]>('/models'),
  default: () => request<string>('/models/default'),
}
