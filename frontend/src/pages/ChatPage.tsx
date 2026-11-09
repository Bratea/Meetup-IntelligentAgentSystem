import { useCallback, useEffect, useRef, useState } from 'react'
import { agentApi, type Agent } from '../api/agent'
import { modelApi, type ModelInfo } from '../api/model'
import { streamChat, streamRegenerate, type AgentEvent } from '../api/chat'
import {
  conversationApi,
  messageApi,
  type Conversation,
} from '../api/conversation'
import Markdown from '../components/Markdown'
import { gsap, useGSAP } from '../hooks/gsap'
import '../styles/main.scss'
import './ChatPage.scss'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  stopped?: boolean
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
  const [pickerOpen, setPickerOpen] = useState(false)
  const [renamingId, setRenamingId] = useState<number | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const [copiedId, setCopiedId] = useState<string | null>(null)

  const abortRef = useRef<AbortController | null>(null)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)
  const messagesRef = useRef<HTMLDivElement | null>(null)
  const idSeedRef = useRef(0)
  const lastCountRef = useRef(0)
  const pickerRef = useRef<HTMLDivElement | null>(null)

  const nextMessageId = () => `m${++idSeedRef.current}`

  const makeMessage = (
    role: 'user' | 'assistant',
    content: string,
    extra?: Partial<ChatMessage>,
  ): ChatMessage => ({ id: nextMessageId(), role, content, ...extra })

  const formatTime = (value?: string) => {
    if (!value) return ''
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return ''
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  }

  useEffect(() => {
    Promise.all([agentApi.list(), modelApi.list()])
      .then(([agentList, list]) => {
        setAgents(agentList)
        // 只保留对话模型；不可用（未配置 API Key / 未安装）的直接排除
        const filtered = list.filter((m) => m.type === 'chat' && m.available !== false)
        setChatModels(filtered)
        // 停用的智能体不再出现在对话页的可选列表
        const defaultAgent = agentList.find((a) => a.status !== 'INACTIVE')
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

  // 选中的智能体被停用后，回落到第一个可用智能体，避免对着停用智能体发消息
  useEffect(() => {
    if (!selectedAgent || selectedAgent.status !== 'INACTIVE') return
    const fallback = agents.find((a) => a.status !== 'INACTIVE') ?? null
    setSelectedAgent(fallback)
    setConversationId(null)
    setMessages([])
    setStatus('')
  }, [agents, selectedAgent])

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

  // 点击会话选择器外部时关闭
  useEffect(() => {
    if (!pickerOpen) return
    const onPointerDown = (e: PointerEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setPickerOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [pickerOpen])

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

  /** 统一的 Agent 事件处理，供发送与重新生成共用 */
  const handleAgentEvent = useCallback(
    (event: AgentEvent) => {
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
          updateLastAssistant((msg) => ({ ...msg, streaming: false }))
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
    [updateLastAssistant],
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
          onEvent: handleAgentEvent,
          onError: (err) => {
            setStatus(`连接异常: ${err.message}`)
            // 流中断时收敛消息状态，避免气泡一直停在“思考中”
            updateLastAssistant((msg) => ({
              ...msg,
              streaming: false,
              content: msg.content || '（连接中断，请重试）',
            }))
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
    }
  }, [
    input,
    sending,
    selectedAgent,
    selectedModel,
    conversationId,
    enableRag,
    updateLastAssistant,
    handleAgentEvent,
  ])

  /** 重新生成：以最后一条用户消息为输入，服务端会清除旧回复 */
  const handleRegenerate = useCallback(async () => {
    if (!conversationId || sending || !selectedAgent) return

    // 本地移除旧回复，插入新的流式占位
    setMessages((prev) => {
      const next = [...prev]
      if (next[next.length - 1]?.role === 'assistant') {
        next.pop()
      }
      return [...next, makeMessage('assistant', '', { streaming: true, toolEvents: [] })]
    })
    setSending(true)
    setStatus('重新生成中...')

    const controller = new AbortController()
    abortRef.current = controller

    try {
      await streamRegenerate(
        {
          conversationId,
          modelId: selectedModel || undefined,
          enableRag,
        },
        {
          onEvent: handleAgentEvent,
          onError: (err) => {
            setStatus(`连接异常: ${err.message}`)
            updateLastAssistant((msg) => ({
              ...msg,
              streaming: false,
              content: msg.content || '（连接中断，请重试）',
            }))
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
    }
  }, [
    conversationId,
    sending,
    selectedAgent,
    selectedModel,
    enableRag,
    updateLastAssistant,
    handleAgentEvent,
  ])

  const handleStop = useCallback(() => {
    abortRef.current?.abort()
    setSending(false)
    setStatus('已停止')
    // 流式中断后消息可能仍停在 streaming 状态，这里补一次收敛
    updateLastAssistant((msg) => ({
      ...msg,
      streaming: false,
      stopped: true,
      content: msg.content || '（已停止生成）',
    }))
  }, [updateLastAssistant])

  const handleClear = useCallback(() => {
    setMessages([])
    setConversationId(null)
    setStatus('')
    setPickerOpen(false)
  }, [])

  const handleSelectConversation = useCallback(
    async (id: number) => {
      if (sending || switchingConversation) return
      setSwitchingConversation(true)
      setConversationId(id)
      setPickerOpen(false)
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

  const handleNewConversation = useCallback(() => {
    if (sending) return
    setMessages([])
    setConversationId(null)
    setStatus('')
    setPickerOpen(false)
  }, [sending])

  const startRename = useCallback((conversation: Conversation) => {
    setRenamingId(conversation.id)
    setRenameValue(conversation.title || '')
  }, [])

  const commitRename = useCallback(async () => {
    if (renamingId == null) return
    const title = renameValue.trim()
    if (!title) {
      setRenamingId(null)
      return
    }
    try {
      const updated = await conversationApi.update(renamingId, { title })
      setConversations((prev) => prev.map((c) => (c.id === renamingId ? updated : c)))
      setRenamingId(null)
    } catch (err) {
      setStatus(`重命名失败: ${(err as Error).message}`)
    }
  }, [renamingId, renameValue])

  /** 归档 / 取消归档：归档后不再作为“新会话”的默认落点，但历史仍可查看 */
  const handleToggleArchive = useCallback(async (conversation: Conversation) => {
    const next = conversation.status === 'ARCHIVED' ? 'ACTIVE' : 'ARCHIVED'
    try {
      const updated = await conversationApi.update(conversation.id, { status: next })
      setConversations((prev) =>
        prev.map((c) => (c.id === conversation.id ? updated : c)),
      )
    } catch (err) {
      setStatus(`操作失败: ${(err as Error).message}`)
    }
  }, [])

  const handleDeleteConversation = useCallback(
    async (id: number) => {
      if (!window.confirm('确认删除该会话及其所有消息？')) return
      try {
        await conversationApi.delete(id)
        setConversations((prev) => prev.filter((c) => c.id !== id))
        if (conversationId === id) {
          setConversationId(null)
          setMessages([])
          setStatus('')
        }
      } catch (err) {
        setStatus(`删除会话失败: ${(err as Error).message}`)
      }
    },
    [conversationId],
  )

  const handleCopy = useCallback(async (msg: ChatMessage) => {
    try {
      await navigator.clipboard.writeText(msg.content)
    } catch {
      // 剪贴板 API 不可用（非安全上下文等）时降级
      const textarea = document.createElement('textarea')
      textarea.value = msg.content
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    setCopiedId(msg.id)
    window.setTimeout(() => {
      setCopiedId((current) => (current === msg.id ? null : current))
    }, 1600)
  }, [])

  // 停用的智能体不出现在选择器里
  const selectableAgents = agents.filter((a) => a.status !== 'INACTIVE')
  const currentConversation = conversations.find((c) => c.id === conversationId)
  const lastMessage = messages[messages.length - 1]
  const canRegenerate =
    Boolean(conversationId) &&
    !sending &&
    lastMessage?.role === 'assistant' &&
    !lastMessage.streaming

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
              const agent = selectableAgents.find((a) => a.id === Number(e.target.value))
              setSelectedAgent(agent ?? null)
              setConversationId(null)
              setMessages([])
              setStatus('')
              setPickerOpen(false)
            }}
          >
            {selectableAgents.length === 0 && <option value="">暂无可用智能体</option>}
            {selectableAgents.map((a) => (
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

          {/* 会话选择器：列表 + 重命名 / 删除 */}
          <div className="chat__picker" ref={pickerRef}>
            <button
              className="chat__picker-trigger"
              onClick={() => setPickerOpen((v) => !v)}
              disabled={switchingConversation}
            >
              <span className="chat__picker-title">
                {currentConversation?.title || '新会话'}
              </span>
              <span className={`chat__picker-caret${pickerOpen ? ' is-open' : ''}`}>
                ▾
              </span>
            </button>

            {pickerOpen && (
              <div className="chat__picker-menu">
                <button className="chat__picker-new" onClick={handleNewConversation}>
                  ＋ 新会话
                </button>
                <div className="chat__picker-list">
                  {conversations.length === 0 && (
                    <div className="chat__picker-empty">暂无历史会话</div>
                  )}
                  {conversations.map((c) => (
                    <div
                      key={c.id}
                      className={`chat__picker-item${c.id === conversationId ? ' is-active' : ''}`}
                    >
                      {renamingId === c.id ? (
                        <input
                          className="input chat__picker-rename"
                          value={renameValue}
                          autoFocus
                          onChange={(e) => setRenameValue(e.target.value)}
                          onBlur={commitRename}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') {
                              e.preventDefault()
                              void commitRename()
                            }
                            if (e.key === 'Escape') {
                              setRenamingId(null)
                            }
                          }}
                        />
                      ) : (
                        <>
                          <button
                            className="chat__picker-item-main"
                            onClick={() => void handleSelectConversation(c.id)}
                          >
                            <span className="chat__picker-item-title">
                              {c.title || `会话 ${c.id}`}
                            </span>
                            <span className="chat__picker-item-meta">
                              {c.status === 'ARCHIVED' && (
                                <span className="chat__picker-item-badge">已归档</span>
                              )}
                              <span className="chat__picker-item-time">
                                {formatTime(c.updatedAt)}
                              </span>
                            </span>
                          </button>
                          <div className="chat__picker-item-actions">
                            <button
                              className="chat__picker-icon"
                              title="重命名"
                              onClick={() => startRename(c)}
                            >
                              ✏️
                            </button>
                            <button
                              className="chat__picker-icon"
                              title={c.status === 'ARCHIVED' ? '取消归档' : '归档'}
                              onClick={() => void handleToggleArchive(c)}
                            >
                              {c.status === 'ARCHIVED' ? '📤' : '📁'}
                            </button>
                            <button
                              className="chat__picker-icon chat__picker-icon--danger"
                              title="删除"
                              onClick={() => void handleDeleteConversation(c.id)}
                            >
                              🗑
                            </button>
                          </div>
                        </>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          <button
            className={`btn btn--sm${enableRag ? ' btn--ghost' : ''}`}
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

        {messages.map((msg, index) => {
          const isLast = index === messages.length - 1
          return (
            <div key={msg.id} className={`message message--${msg.role}`}>
              <div className="message__avatar">
                {msg.role === 'user' ? '我' : '寻'}
              </div>
              <div className="message__body">
                <div className="message__role">
                  {msg.role === 'user' ? '我' : selectedAgent?.name ?? '智能体'}
                  {msg.streaming && <span className="message__cursor">▋</span>}
                </div>

                {msg.role === 'assistant' ? (
                  <Markdown
                    content={msg.content || (msg.streaming ? '思考中…' : '')}
                    className="message__markdown"
                  />
                ) : (
                  <div className="message__content">{msg.content}</div>
                )}

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

                {msg.role === 'assistant' && !msg.streaming && msg.content && (
                  <div className="message__actions">
                    <button
                      className={`message__action${copiedId === msg.id ? ' is-active' : ''}`}
                      onClick={() => void handleCopy(msg)}
                      title="复制内容"
                    >
                      {copiedId === msg.id ? '✓ 已复制' : '复制'}
                    </button>
                    {isLast && (
                      <button
                        className="message__action"
                        onClick={() => void handleRegenerate()}
                        disabled={!canRegenerate}
                        title="以该会话最后一条消息重新生成"
                      >
                        ↻ 重新生成
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          )
        })}
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
