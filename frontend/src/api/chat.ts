/**
 * SSE 流式对话 API
 */

import { API_BASE } from './request'

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
}

/**
 * 通过 fetch stream 建立 SSE 连接（EventSource 不支持 POST，故使用 fetch）
 */
export async function streamChat(
  params: StreamChatParams,
  callbacks: {
    onEvent: (event: AgentEvent) => void
    onError?: (error: Error) => void
    onComplete?: () => void
  },
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
  query.set('message', params.message)

  const response = await fetch(`${API_BASE}/chat/stream?${query.toString()}`, {
    method: 'GET',
    headers: { Accept: 'text/event-stream' },
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`SSE 连接失败: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const parseAndDispatch = (raw: string) => {
    // SSE 格式：data: {...}\n\n
    const lines = raw.split('\n')
    let dataLine = ''
    for (const line of lines) {
      if (line.startsWith('data:')) {
        dataLine += line.slice(5).trimStart()
      } else if (line === '' && dataLine) {
        try {
          callbacks.onEvent(JSON.parse(dataLine) as AgentEvent)
        } catch {
          // 忽略非 JSON 数据
        }
        dataLine = ''
      }
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() ?? ''
      for (const part of parts) {
        parseAndDispatch(part)
      }
    }
    if (buffer) {
      parseAndDispatch(buffer)
    }
  } catch (error) {
    callbacks.onError?.(error as Error)
  } finally {
    callbacks.onComplete?.()
  }
}
