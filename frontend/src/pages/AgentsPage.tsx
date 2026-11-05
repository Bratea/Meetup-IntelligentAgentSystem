import { useCallback, useEffect, useState } from 'react'
import { agentApi, type Agent, type AgentPayload } from '../api/agent'
import { modelApi, type ModelInfo } from '../api/model'
import { toolApi, type ToolDefinitionInfo } from '../api/tool'
import { usePageIntro } from '../hooks/gsap'
import '../styles/main.scss'
import './AgentsPage.scss'

const emptyForm = {
  name: '',
  description: '',
  systemPrompt: '',
  modelId: '',
  toolIds: [] as string[],
  maxSteps: 8,
}

/** 数据库里 toolIds 存的是 JSON 字符串，编辑时解析成数组 */
function parseToolIds(raw?: string): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((n) => typeof n === 'string') : []
  } catch {
    return []
  }
}

export default function AgentsPage() {
  const root = usePageIntro()
  const [agents, setAgents] = useState<Agent[]>([])
  const [models, setModels] = useState<ModelInfo[]>([])
  const [tools, setTools] = useState<ToolDefinitionInfo[]>([])
  const [form, setForm] = useState({ ...emptyForm })
  const [editingId, setEditingId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [agentList, modelList, toolList] = await Promise.all([
        agentApi.list(),
        modelApi.list(),
        toolApi.list(),
      ])
      setAgents(agentList)
      // 只保留可用中的对话模型，避免选到未配置 Key 的远端模型
      setModels(modelList.filter((m) => m.type === 'chat' && m.available !== false))
      setTools(toolList)
      setError('')
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.name.trim()) {
      setError('智能体名称不能为空')
      return
    }
    setSaving(true)
    setError('')
    try {
      const payload: AgentPayload = {
        name: form.name.trim(),
        description: form.description,
        systemPrompt: form.systemPrompt,
        modelId: form.modelId || models[0]?.id || '',
        toolIds: form.toolIds,
        maxSteps: form.maxSteps,
        status: 'ACTIVE',
      }
      if (editingId) {
        await agentApi.update(editingId, payload)
      } else {
        await agentApi.create(payload)
      }
      setForm({ ...emptyForm })
      setEditingId(null)
      await load()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSaving(false)
    }
  }

  const handleEdit = (agent: Agent) => {
    setEditingId(agent.id)
    setForm({
      name: agent.name,
      description: agent.description ?? '',
      systemPrompt: agent.systemPrompt ?? '',
      modelId: agent.modelId,
      toolIds: parseToolIds(agent.toolIds),
      maxSteps: agent.maxSteps,
    })
  }

  const handleDelete = async (id: number) => {
    if (!window.confirm('确认删除该智能体？相关会话将一并删除。')) return
    try {
      await agentApi.delete(id)
      await load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const toggleTool = (name: string) => {
    setForm((prev) => ({
      ...prev,
      toolIds: prev.toolIds.includes(name)
        ? prev.toolIds.filter((n) => n !== name)
        : [...prev.toolIds, name],
    }))
  }

  const selectedTools = form.toolIds

  if (loading) {
    return (
      <div className="page">
        <div className="empty">
          <div className="loading">
            <span className="spinner" /> 加载中…
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="page" ref={root}>
      <header className="page__header">
        <div>
          <h1 className="page__title">🤖 智能体管理</h1>
          <div className="page__desc">配置智能体提示词、模型、工具与最大执行步数</div>
        </div>
      </header>

      <div className="page__body agents">
        {error && <div className="agents__error">{error}</div>}

        <section className="card">
          <h2 className="card__title">{editingId ? '编辑智能体' : '新建智能体'}</h2>
          <form onSubmit={handleSubmit} className="agents__form">
            <div className="agents__form-grid">
              <div className="field">
                <label className="field__label">名称 *</label>
                <input
                  className="input"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="如：客服助手"
                />
              </div>
              <div className="field">
                <label className="field__label">模型</label>
                <select
                  className="select"
                  value={form.modelId}
                  onChange={(e) => setForm({ ...form, modelId: e.target.value })}
                >
                  {models.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field__label">最大执行步数</label>
                <input
                  className="input"
                  type="number"
                  min={1}
                  max={20}
                  value={form.maxSteps}
                  onChange={(e) =>
                    setForm({ ...form, maxSteps: Number(e.target.value) })
                  }
                />
              </div>
              <div className="field">
                <label className="field__label">描述</label>
                <input
                  className="input"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  placeholder="智能体用途描述"
                />
              </div>
            </div>

            <div className="field">
              <label className="field__label">系统提示词</label>
              <textarea
                className="textarea"
                value={form.systemPrompt}
                onChange={(e) => setForm({ ...form, systemPrompt: e.target.value })}
                placeholder="你是一个专业的…"
              />
            </div>

            <div className="field">
              <label className="field__label">可选工具</label>
              <div className="agents__tools">
                {tools.map((tool) => (
                  <label
                    key={tool.name}
                    className={`agents__tool${
                      selectedTools.includes(tool.name) ? ' agents__tool--active' : ''
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={selectedTools.includes(tool.name)}
                      onChange={() => toggleTool(tool.name)}
                    />
                    <span>{tool.name}</span>
                    <span className="badge badge--primary">{tool.type}</span>
                  </label>
                ))}
                {tools.length === 0 && (
                  <span className="card__meta">暂无可用工具</span>
                )}
              </div>
            </div>

            <div className="agents__actions">
              <button type="submit" className="btn btn--primary" disabled={saving}>
                {saving ? '保存中…' : editingId ? '保存修改' : '创建智能体'}
              </button>
              {editingId && (
                <button
                  type="button"
                  className="btn"
                  onClick={() => {
                    setEditingId(null)
                    setForm({ ...emptyForm })
                  }}
                >
                  取消编辑
                </button>
              )}
            </div>
          </form>
        </section>

        <section className="agents__list">
          {agents.length === 0 && <div className="empty">还没有智能体，先创建一个吧</div>}
          {agents.map((agent) => (
            <div key={agent.id} className="card agents__item">
              <div className="agents__item-main">
                <div className="agents__item-head">
                  <span className="agents__item-name">{agent.name}</span>
                  <span className={`badge ${agent.status === 'ACTIVE' ? 'badge--success' : ''}`}>
                    {agent.status}
                  </span>
                </div>
                <div className="card__meta">{agent.description || '暂无描述'}</div>
                <div className="agents__item-tags">
                  <span className="badge badge--primary">模型: {agent.modelId}</span>
                  <span className="badge">最大步数: {agent.maxSteps}</span>
                  <span className="badge">
                    工具: {(() => {
                      try {
                        const arr = JSON.parse(agent.toolIds || '[]') as string[]
                        return arr.length ? arr.join(', ') : '无'
                      } catch {
                        return '无'
                      }
                    })()}
                  </span>
                </div>
                {agent.systemPrompt && (
                  <pre className="agents__prompt">{agent.systemPrompt}</pre>
                )}
              </div>
              <div className="agents__item-actions">
                <button className="btn btn--sm" onClick={() => handleEdit(agent)}>
                  编辑
                </button>
                <button className="btn btn--sm btn--danger" onClick={() => handleDelete(agent.id)}>
                  删除
                </button>
              </div>
            </div>
          ))}
        </section>
      </div>
    </div>
  )
}
