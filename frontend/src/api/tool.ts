/**
 * 工具 API
 */

import { request } from './request'

export interface ToolDefinitionInfo {
  name: string
  description: string
  type: 'FIXED' | 'OPTIONAL'
  inputSchema: string
  enabled: boolean
}

export const toolApi = {
  list: () => request<ToolDefinitionInfo[]>('/tools'),
}
