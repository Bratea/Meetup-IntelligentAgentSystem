/**
 * SSE 流式对话 API
 */

import { API_BASE, authHeaders, clearAccessToken } from './request'

export type AgentEventType =
  | 'thinking'
  | 'token'
  | 'tool_calling'
  | 'tool_result'
  | 'done'
  | 'error'
  | 'max_steps'
  | string

export interface AgentEvent {
  type: AgentEventType
  data: unknown
  timestamp?: string
  step?: number
  state?: string
}

export interface StreamChatParams {
  agentId: number
  conversationId?: number
  message: string
  modelId?: string
  /** 是否启用知识库检索，默认 true */
  enableRag?: boolean
}

export interface RegenerateParams {
  conversationId: number
  modelId?: string
  enableRag?: boolean
}

export interface StreamCallbacks {
  onEvent: (event: AgentEvent) => void
  onError?: (error: Error) => void
  onComplete?: () => void
}

/**
 * 建立 SSE 连接并消费事件流。
 *
 * <p>EventSource 不支持 POST 与自定义 header，因此统一用 fetch stream 读取。</p>
 */
async function consumeSseStream(
  url: string,
  callbacks: StreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  let response: Response
  try {
    response = await fetch(url, {
      method: 'GET',
      headers: { Accept: 'text/event-stream', ...authHeaders() },
      signal,
    })
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      callbacks.onComplete?.()
      return
    }
    const err = new Error(
      `网络连接失败：${(error as Error).message}。请确认后端服务已启动`,
    )
    callbacks.onError?.(err)
    throw err
  }

  if (response.status === 401) {
    // 令牌无效：清掉本地令牌，让全局守卫把用户送回令牌输入页
    clearAccessToken()
  }

  if (!response.ok || !response.body) {
    const message = await readErrorMessage(response)
    const err = new Error(message)
    callbacks.onError?.(err)
    throw err
  }

  const reader = response.body.getReader()
  // SSE 的 data 字段可能跨 chunk 到达，必须用 stream 模式解码，避免多字节字符被截断
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 事件以空行分隔；\r\n\r\n 与 \n\n 都要兼容
      const parts = buffer.split(/\r?\n\r?\n/)
      buffer = parts.pop() ?? ''
      for (const part of parts) {
        dispatch(part, callbacks)
      }
    }
    // flush：处理末尾残留的多字节字符
    buffer += decoder.decode()
    if (buffer) dispatch(buffer, callbacks)
    callbacks.onComplete?.()
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      callbacks.onComplete?.()
      return
    }
    callbacks.onError?.(error as Error)
    throw error
  }
}

/** 发送消息并流式接收回复 */
export async function streamChat(
  params: StreamChatParams,
  callbacks: StreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  const query = new URLSearchParams()
  query.set('agentId', String(params.agentId))
  if (params.conversationId) {
    query.set('conversationId', String(params.conversationId))
  }
  if (params.modelId) {
    query.set('modelId', params.modelId)
  }
  if (params.enableRag === false) {
    query.set('enableRag', 'false')
  }
  query.set('message', params.message)

  await consumeSseStream(
    `${API_BASE}/chat/stream?${query.toString()}`,
    callbacks,
    signal,
  )
}

/** 以会话内最后一条用户消息为输入重新生成回复 */
export async function streamRegenerate(
  params: RegenerateParams,
  callbacks: StreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  const query = new URLSearchParams()
  query.set('conversationId', String(params.conversationId))
  if (params.modelId) {
    query.set('modelId', params.modelId)
  }
  if (params.enableRag === false) {
    query.set('enableRag', 'false')
  }

  await consumeSseStream(
    `${API_BASE}/chat/regenerate?${query.toString()}`,
    callbacks,
    signal,
  )
}

/** 解析单个 SSE 事件块，取出 data 字段并回调 */
function dispatch(raw: string, callbacks: StreamCallbacks): void {
  const lines = raw.split(/\r?\n/)
  let dataLine = ''
  for (const line of lines) {
    if (line.startsWith('data:')) {
      dataLine += line.slice(5).trimStart()
    }
  }
  if (!dataLine) return
  try {
    callbacks.onEvent(JSON.parse(dataLine) as AgentEvent)
  } catch {
    // 忽略非 JSON 数据（如心跳注释行）
  }
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const text = await response.text()
    if (!text) return `SSE 连接失败（HTTP ${response.status}）`
    const parsed = JSON.parse(text) as { message?: string }
    if (parsed && typeof parsed.message === 'string' && parsed.message) {
      return parsed.message
    }
    return `SSE 连接失败（HTTP ${response.status}）`
  } catch {
    return `SSE 连接失败（HTTP ${response.status}）`
  }
}
