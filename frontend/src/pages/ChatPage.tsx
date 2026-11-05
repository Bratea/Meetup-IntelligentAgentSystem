import { useCallback, useEffect, useRef, useState } from 'react'
import { agentApi, type Agent } from '../api/agent'
import { modelApi, type ModelInfo } from '../api/model'
import { streamChat, type AgentEvent } from '../api/chat'
import {
  conversationApi,
  messageApi,
  type Conversation,
} from '../api/conversation'
import { gsap, useGSAP } from '../hooks/gsap'
import '../styles/main.scss'
import './ChatPage.scss'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  toolEvents?: ToolEvent[]
}

interface ToolEvent {
  name: string
  arguments?: string
  result?: string
}

export default function ChatPage() {
  const [agents, setAgents] = useState<Agent[]>([])
  const [chatModels, setChatModels] = useState<ModelInfo[]>([])
  const [selectedAgent, setSelectedAgent] = useState<Agent | null>(null)
  const [selectedModel, setSelectedModel] = useState<string>('')
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [sending, setSending] = useState(false)
  const [status, setStatus] = useState('')
  const [enableRag, setEnableRag] = useState(true)

  const [conversations, setConversations] = useState<Conversation[]>([])
  const [conversationId, setConversationId] = useState<number | null>(null)
  const [switchingConversation, setSwitchingConversation] = useState(false)

  const abortRef = useRef<AbortController | null>(null)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)
  const messagesRef = useRef<HTMLDivElement | null>(null)
  const idSeedRef = useRef(0)
  const lastCountRef = useRef(0)

  const nextMessageId = () => `m${++idSeedRef.current}`

  const makeMessage = (
    role: 'user' | 'assistant',
    content: string,
    extra?: Partial<ChatMessage>,
  ): ChatMessage => ({ id: nextMessageId(), role, content, ...extra })

  useEffect(() => {
    Promise.all([agentApi.list(), modelApi.list()])
      .then(([agentList, list]) => {
        setAgents(agentList)
        // 只保留对话模型；不可用（未配置 API Key / 未安装）的直接排除
        const filtered = list.filter((m) => m.type === 'chat' && m.available !== false)
        setChatModels(filtered)
        const defaultAgent = agentList[0]
        if (defaultAgent) {
          setSelectedAgent(defaultAgent)
        }
        // 只在 chat 类型里挑默认模型，避免选中 embedding 模型导致对话必然失败
        const defaultModel = filtered.find((m) => m.defaultModel) ?? filtered[0]
        if (defaultModel) {
          setSelectedModel(defaultModel.id)
        }
      })
      .catch((err) => setStatus(`加载失败: ${err.message}`))
  }, [])

  // 模型元信息可能晚于智能体返回，做一次兜底纠正
  useEffect(() => {
    if (chatModels.length === 0) return
    if (!chatModels.some((m) => m.id === selectedModel)) {
      setSelectedModel((chatModels.find((m) => m.defaultModel) ?? chatModels[0]).id)
    }
  }, [chatModels, selectedModel])

  // 智能体切换时拉取它的会话列表
  useEffect(() => {
    if (!selectedAgent) {
      setConversations([])
      return
    }
    conversationApi
      .list(selectedAgent.id)
      .then(setConversations)
      .catch(() => setConversations([]))
  }, [selectedAgent])

  // 页面头部与输入区入场
  useGSAP(
    () => {
      gsap.fromTo(
        '.page__header',
        { opacity: 0, y: -16 },
        { opacity: 1, y: 0, duration: 0.5, ease: 'power3.out' },
      )
      gsap.fromTo(
        '.chat__composer',
        { opacity: 0, y: 20 },
        { opacity: 1, y: 0, duration: 0.5, delay: 0.15, ease: 'power3.out' },
      )
    },
    { dependencies: [] },
  )

  // 新消息入场动画（仅在消息数量变化时触发）
  useEffect(() => {
    if (!messagesRef.current || messages.length === 0) return
    const nodes = messagesRef.current.querySelectorAll('.message')
    const last = nodes[nodes.length - 1]
    if (!last) return
    gsap.fromTo(
      last,
      { opacity: 0, y: 16, scale: 0.97 },
      {
        opacity: 1,
        y: 0,
        scale: 1,
        duration: 0.38,
        ease: 'power2.out',
        clearProps: 'transform,opacity',
      },
    )
    const tools = last.querySelectorAll('.tool-call')
    if (tools.length) {
      gsap.fromTo(
        tools,
        { opacity: 0, x: -8 },
        { opacity: 1, x: 0, duration: 0.3, stagger: 0.08, ease: 'power2.out' },
      )
    }
  }, [messages.length])

  // 仅在"消息条数增加"时平滑滚动；流式更新不改变条数，避免持续触发滚动
  useEffect(() => {
    if (messages.length > lastCountRef.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
    lastCountRef.current = messages.length
  }, [messages.length])

  const updateLastAssistant = useCallback(
    (updater: (msg: ChatMessage) => ChatMessage) => {
      setMessages((prev) => {
        const next = [...prev]
        const last = next[next.length - 1]
        if (last && last.role === 'assistant') {
          next[next.length - 1] = updater(last)
        }
        return next
      })
    },
    [],
  )

  const handleSend = useCallback(async () => {
    const text = input.trim()
    if (!text || sending) return
    if (!selectedAgent) {
      setStatus('请先创建智能体')
      return
    }

    const userMessage = makeMessage('user', text)
    const assistantMessage = makeMessage('assistant', '', {
      streaming: true,
      toolEvents: [],
    })
    setMessages((prev) => [...prev, userMessage, assistantMessage])
    setInput('')
    setSending(true)
    setStatus('连接中...')

    // 首次发消息时创建会话，之后复用同一会话
    let cid = conversationId
    if (!cid) {
      try {
        const created = await conversationApi.create({
          agentId: selectedAgent.id,
          title: text.slice(0, 30),
          status: 'ACTIVE',
        })
        cid = created.id
        setConversationId(cid)
        setConversations((prev) => [created, ...prev])
      } catch (err) {
        setStatus(`创建会话失败: ${(err as Error).message}`)
        setSending(false)
        updateLastAssistant((msg) => ({ ...msg, streaming: false }))
        return
      }
    }

    const controller = new AbortController()
    abortRef.current = controller

    try {
      await streamChat(
        {
          agentId: selectedAgent.id,
          conversationId: cid,
          message: text,
          modelId: selectedModel || undefined,
          enableRag,
        },
        {
          onEvent: (event: AgentEvent) => {
            switch (event.type) {
              case 'thinking':
                setStatus(`第 ${event.step ?? 1} 步 · 思考中`)
                break
              case 'token':
                updateLastAssistant((msg) => ({
                  ...msg,
                  content: msg.content + String(event.data ?? ''),
                }))
                setStatus(`第 ${event.step ?? 1} 步 · 生成中`)
                break
              case 'tool_calling': {
                const payload = event.data as { name: string; arguments: string }
                updateLastAssistant((msg) => ({
                  ...msg,
                  toolEvents: [
                    ...(msg.toolEvents ?? []),
                    { name: payload.name, arguments: payload.arguments },
                  ],
                }))
                setStatus(`调用工具 ${payload.name}`)
                break
              }
              case 'tool_result': {
                const payload = event.data as { name: string; result: string }
                updateLastAssistant((msg) => ({
                  ...msg,
                  toolEvents: (msg.toolEvents ?? []).map((t) =>
                    t.name === payload.name ? { ...t, result: payload.result } : t,
                  ),
                }))
                setStatus(`工具 ${payload.name} 返回结果`)
                break
              }
              case 'done':
                updateLastAssistant((msg) => ({ ...msg, streaming: false }))
                setStatus('已完成')
                break
              case 'max_steps':
                setStatus('已达最大执行步数')
                break
              case 'error':
                // 服务端已把异常翻译成可读提示，直接展示给用户
                updateLastAssistant((msg) => ({
                  ...msg,
                  content: msg.content
                    ? `${msg.content}\n\n⚠️ ${String(event.data)}`
                    : `执行出错：${String(event.data)}`,
                  streaming: false,
                }))
                setStatus('执行失败')
                break
            }
          },
          onError: (err) => {
            setStatus(`连接异常: ${err.message}`)
            updateLastAssistant((msg) => ({ ...msg, streaming: false }))
          },
          onComplete: () => {
            setSending(false)
          },
        },
        controller.signal,
      )
    } catch (err) {
      setStatus(`请求失败: ${(err as Error).message}`)
      setSending(false)
      updateLastAssistant((msg) => ({ ...msg, streaming: false }))
    }
  }, [
    input,
    sending,
    selectedAgent,
    selectedModel,
    conversationId,
    enableRag,
    updateLastAssistant,
  ])

  const handleStop = useCallback(() => {
    abortRef.current?.abort()
    setSending(false)
    setStatus('已停止')
  }, [])

  const handleClear = useCallback(() => {
    setMessages([])
    setConversationId(null)
    setStatus('')
  }, [])

  const handleSelectConversation = useCallback(
    async (id: number) => {
      if (sending || switchingConversation) return
      setSwitchingConversation(true)
      setConversationId(id)
      try {
        const history = await messageApi.list(id)
        setMessages(
          history
            .filter((m) => m.role === 'user' || m.role === 'assistant')
            .map((m) =>
              makeMessage(m.role as 'user' | 'assistant', m.content),
            ),
        )
        setStatus('')
        requestAnimationFrame(() =>
          messagesEndRef.current?.scrollIntoView({ behavior: 'auto' }),
        )
      } catch (err) {
        setStatus(`加载会话失败: ${(err as Error).message}`)
        setMessages([])
      } finally {
        setSwitchingConversation(false)
      }
    },
    [sending, switchingConversation],
  )

  return (
    <div className="page">
      <header className="page__header">
        <div>
          <h1 className="page__title">💬 智能对话</h1>
          <div className="page__desc">
            Think-Execute 执行循环 · 工具调用 · SSE 实时流式输出
          </div>
        </div>
        <div className="chat__toolbar">
          <select
            className="select chat__select"
            value={selectedAgent?.id ?? ''}
            onChange={(e) => {
              const agent = agents.find((a) => a.id === Number(e.target.value))
              setSelectedAgent(agent ?? null)
              setConversationId(null)
              setMessages([])
              setStatus('')
            }}
          >
            {agents.length === 0 && <option value="">暂无智能体</option>}
            {agents.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>

          <select
            className="select chat__select"
            value={selectedModel}
            onChange={(e) => setSelectedModel(e.target.value)}
          >
            {chatModels.length === 0 && <option value="">暂无可用模型</option>}
            {chatModels.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </select>

          <select
            className="select chat__select"
            value={conversationId ?? ''}
            onChange={(e) => {
              const value = e.target.value
              if (value) {
                void handleSelectConversation(Number(value))
              }
            }}
            disabled={sending || switchingConversation}
          >
            <option value="">新会话</option>
            {conversations.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title || `会话 ${c.id}`}
              </option>
            ))}
          </select>

          <button
            className={`btn btn--sm ${enableRag ? 'btn--ghost' : 'btn--sm'}`}
            onClick={() => setEnableRag((v) => !v)}
            disabled={sending}
            title={enableRag ? '已启用知识库检索' : '未启用知识库检索'}
          >
            {enableRag ? 'RAG 开' : 'RAG 关'}
          </button>

          <button className="btn btn--sm" onClick={handleClear} disabled={sending}>
            清空
          </button>
        </div>
      </header>

      <div className="chat__messages" ref={messagesRef}>
        {messages.length === 0 && (
          <div className="empty chat__welcome">
            <div className="chat__welcome-logo">
              <span className="chat__welcome-logo-text">寻</span>
            </div>
            <div className="chat__welcome-title">
              你好，我是 <span className="text-gradient">寻晤智能agent系统</span>
            </div>
            <div className="chat__welcome-sub">
              多模型驱动 · 工具调用 · 知识库检索 · 实时流式输出
            </div>
            <div className="chat__welcome-chips">
              <button className="chip" onClick={() => setInput('现在几点了？')}>
                🕐 现在几点了？
              </button>
              <button className="chip" onClick={() => setInput('帮我计算 128 * 46 的结果')}>
                🧮 帮我计算 128 × 46
              </button>
              <button className="chip" onClick={() => setInput('介绍一下你自己')}>
                👋 介绍一下你自己
              </button>
            </div>
          </div>
        )}

        {messages.map((msg) => (
          <div key={msg.id} className={`message message--${msg.role}`}>
            <div className="message__avatar">
              {msg.role === 'user' ? '我' : '寻'}
            </div>
            <div className="message__body">
              <div className="message__role">
                {msg.role === 'user' ? '我' : selectedAgent?.name ?? '智能体'}
                {msg.streaming && <span className="message__cursor">▋</span>}
              </div>
              <div className="message__content">
                {msg.content || (msg.streaming ? '思考中…' : '')}
              </div>

              {msg.toolEvents && msg.toolEvents.length > 0 && (
                <div className="message__tools">
                  {msg.toolEvents.map((tool, i) => (
                    <div key={`${tool.name}-${i}`} className="tool-call">
                      <div className="tool-call__header">
                        <span className="tool-call__icon">🔧</span>
                        <span className="tool-call__name">{tool.name}</span>
                        {tool.result && <span className="tool-call__ok">✓</span>}
                      </div>
                      {tool.arguments && tool.arguments !== '{}' && (
                        <pre className="tool-call__args">{tool.arguments}</pre>
                      )}
                      {tool.result && (
                        <pre className="tool-call__result">{tool.result}</pre>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <div className="chat__composer">
        {status && <div className="chat__status">{status}</div>}
        {!selectedAgent && (
          <div className="chat__status chat__status--warn">
            请先在「智能体」页创建并选择一个智能体，再开始对话
          </div>
        )}
        <div className="chat__input-row">
          <textarea
            className="textarea chat__textarea"
            value={input}
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                void handleSend()
              }
            }}
            disabled={sending}
          />
          {sending ? (
            <button className="btn btn--danger" onClick={handleStop}>
              停止
            </button>
          ) : (
            <button
              className="btn btn--primary"
              onClick={handleSend}
              disabled={!input.trim() || !selectedAgent}
            >
              发送
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
