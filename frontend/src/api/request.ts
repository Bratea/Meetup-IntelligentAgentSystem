/**
 * 寻晤智能agent系统 - API 基础配置
 */

export const API_BASE = '/api'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${url}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  })
  const body = (await response.json()) as ApiResponse<T>
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}
