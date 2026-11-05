/**
 * 寻晤智能agent系统 - API 基础配置
 */

export const API_BASE = '/api'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 网络 / 服务不可用（区别于业务错误） */
export class NetworkError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'NetworkError'
    this.status = status
  }
}

export async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options?.headers as Record<string, string> | undefined),
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE}${url}`, {
      ...options,
      headers,
    })
  } catch (error) {
    // fetch 抛错 = 网络层失败（断网、DNS、跨域、连接被拒）
    throw new NetworkError(
      `网络连接失败：${(error as Error).message}。请确认后端服务已启动`,
      0,
    )
  }

  if (!response.ok) {
    throw new NetworkError(
      await readErrorMessage(response, `请求失败（HTTP ${response.status}）`),
      response.status,
    )
  }

  let body: ApiResponse<T>
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new NetworkError('服务端返回了非 JSON 响应，请检查服务状态', response.status)
  }

  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

/** 尽量从错误响应里读出可读信息，读不到则用兜底文案 */
async function readErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const text = await response.text()
    if (!text) return fallback
    const parsed = JSON.parse(text) as ApiResponse<unknown>
    if (parsed && typeof parsed.message === 'string' && parsed.message) {
      return parsed.message
    }
    // HTML 错误页（nginx 502 / 404）
    return fallback
  } catch {
    return fallback
  }
}
