import { useEffect, useState } from 'react'
import { modelApi, type ModelInfo } from '../api/model'
import { usePageIntro } from '../hooks/gsap'
import '../styles/main.scss'
import './ModelsPage.scss'

const providerMeta: Record<string, { icon: string; label: string }> = {
  deepseek: { icon: '🌊', label: 'DeepSeek' },
  zhipu: { icon: '✨', label: '智谱 GLM' },
  ollama: { icon: '🦙', label: 'Ollama 本地' },
  longcat: { icon: '🐱', label: 'LongCat' },
}

export default function ModelsPage() {
  const root = usePageIntro()
  const [models, setModels] = useState<ModelInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    modelApi
      .list()
      .then(setModels)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false))
  }, [])

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

  const groups = models.reduce<Record<string, ModelInfo[]>>((acc, m) => {
    const key = m.provider
    if (!acc[key]) acc[key] = []
    acc[key].push(m)
    return acc
  }, {})

  return (
    <div className="page" ref={root}>
      <header className="page__header">
        <div>
          <h1 className="page__title">🧠 模型管理</h1>
          <div className="page__desc">
            DeepSeek · 智谱 GLM · Ollama 本地模型统一接入
          </div>
        </div>
      </header>

      <div className="page__body models">
        {error && <div className="error-banner">{error}</div>}

        {Object.entries(groups).map(([provider, list]) => {
          const meta = providerMeta[provider] ?? { icon: '🔹', label: provider }
          return (
            <section key={provider} className="models__group">
              <h2 className="models__group-title">
                <span className="models__group-icon">{meta.icon}</span>
                {meta.label}
                <span className="badge">{list.length}</span>
              </h2>
              <div className="models__grid">
                {list.map((m) => (
                  <div key={m.id} className="card models__card">
                    <div className="models__card-head">
                      <span className="models__card-name">{m.name}</span>
                      <span className="models__card-badges">
                        <span
                          className={`badge ${
                            m.available ? 'badge--success' : 'badge--danger'
                          }`}
                        >
                          {m.available ? '可用' : '不可用'}
                        </span>
                        {m.defaultModel && (
                          <span className="badge badge--primary">默认</span>
                        )}
                      </span>
                    </div>
                    <div className="models__card-desc">{m.description}</div>
                    <div className="models__card-meta">
                      {m.id} · {m.type}
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )
        })}

        {models.length === 0 && (
          <div className="models__empty">
            <div className="empty__icon">🧠</div>
            <div>暂无模型</div>
          </div>
        )}
      </div>
    </div>
  )
}
