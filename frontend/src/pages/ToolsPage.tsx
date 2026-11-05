import { useEffect, useState } from 'react'
import { toolApi, type ToolDefinitionInfo } from '../api/tool'
import { usePageIntro } from '../hooks/gsap'
import '../styles/main.scss'
import './ToolsPage.scss'

export default function ToolsPage() {
  const root = usePageIntro()
  const [tools, setTools] = useState<ToolDefinitionInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    toolApi
      .list()
      .then(setTools)
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

  return (
    <div className="page" ref={root}>
      <header className="page__header">
        <div>
          <h1 className="page__title">🔧 工具管理</h1>
          <div className="page__desc">
            内置工具与 MCP 工具统一注册，供智能体按需调用
          </div>
        </div>
      </header>

      <div className="page__body tools">
        {error && <div className="error-banner">{error}</div>}

        {tools.length === 0 ? (
          <div className="tools__empty">
            <div className="empty__icon">🔧</div>
            <div>暂无可用工具</div>
          </div>
        ) : (
          <div className="tools__grid">
            {tools.map((tool) => (
              <div key={tool.name} className="card tools__card">
                <div className="tools__card-head">
                  <span className="tools__card-name">{tool.name}</span>
                  <span
                    className={`badge ${
                      tool.type === 'FIXED' ? 'badge--primary' : 'badge--accent'
                    }`}
                  >
                    {tool.type}
                  </span>
                </div>
                <div className="tools__card-desc">{tool.description}</div>
                <pre className="tools__card-schema">{tool.inputSchema}</pre>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
