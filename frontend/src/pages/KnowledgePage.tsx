import { useCallback, useEffect, useRef, useState } from 'react'
import {
  knowledgeApi,
  type KnowledgeDocument,
  type KnowledgeSearchResult,
  type KnowledgeSegment,
} from '../api/knowledge'
import { usePageIntro } from '../hooks/gsap'
import '../styles/main.scss'
import './KnowledgePage.scss'

export default function KnowledgePage() {
  const root = usePageIntro()
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [name, setName] = useState('')
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')

  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<KnowledgeSearchResult[]>([])
  const [searching, setSearching] = useState(false)

  // 分段明细
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [segments, setSegments] = useState<KnowledgeSegment[]>([])
  const [segmentsLoading, setSegmentsLoading] = useState(false)
  const [editingSegmentId, setEditingSegmentId] = useState<number | null>(null)
  const [editingSegmentContent, setEditingSegmentContent] = useState('')
  const [reprocessingId, setReprocessingId] = useState<number | null>(null)
  const [actionError, setActionError] = useState('')

  const load = useCallback(async () => {
    try {
      const list = await knowledgeApi.listDocuments()
      setDocuments(list)
      setError('')
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }, [])

  const pollTimerRef = useRef<number | null>(null)
  const pollCountRef = useRef(0)
  const MAX_POLLS = 150 // 2s 一次，最多轮询 5 分钟

  useEffect(() => {
    load()
    return () => {
      if (pollTimerRef.current !== null) {
        window.clearInterval(pollTimerRef.current)
      }
    }
  }, [load])

  // 有文档在 PROCESSING 时轮询刷新；全部结束后自动停止
  useEffect(() => {
    const hasProcessing = documents.some((d) => d.status === 'PROCESSING')
    if (hasProcessing && pollTimerRef.current === null && pollCountRef.current < MAX_POLLS) {
      pollTimerRef.current = window.setInterval(() => {
        pollCountRef.current += 1
        if (pollCountRef.current >= MAX_POLLS) {
          window.clearInterval(pollTimerRef.current!)
          pollTimerRef.current = null
        }
        load()
      }, 2000)
    } else if (!hasProcessing && pollTimerRef.current !== null) {
      window.clearInterval(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }, [documents, load])

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim() || !content.trim()) {
      setError('文档名称与内容不能为空')
      return
    }
    setUploading(true)
    setError('')
    try {
      await knowledgeApi.upload(name.trim(), content.trim())
      setName('')
      setContent('')
      pollCountRef.current = 0
      await load()
      // 向量化在后台进行，状态由轮询自动刷新
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setUploading(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!window.confirm('确认删除该文档及其所有分段？')) return
    try {
      await knowledgeApi.delete(id)
      if (expandedId === id) {
        setExpandedId(null)
        setSegments([])
      }
      await load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!searchQuery.trim()) return
    setSearching(true)
    setError('')
    try {
      const results = await knowledgeApi.search(searchQuery.trim(), 5)
      setSearchResults(results)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSearching(false)
    }
  }

  /** 展开 / 收起分段明细 */
  const toggleSegments = useCallback(async (doc: KnowledgeDocument) => {
    if (expandedId === doc.id) {
      setExpandedId(null)
      setSegments([])
      return
    }
    setExpandedId(doc.id)
    setSegmentsLoading(true)
    setActionError('')
    try {
      const list = await knowledgeApi.listSegments(doc.id)
      setSegments(list)
    } catch (err) {
      setActionError((err as Error).message)
      setSegments([])
    } finally {
      setSegmentsLoading(false)
    }
  }, [expandedId])

  /** 重新向量化整个文档 */
  const handleReprocess = useCallback(async (id: number) => {
    setReprocessingId(id)
    setActionError('')
    try {
      await knowledgeApi.reprocess(id)
      pollCountRef.current = 0
      await load()
      // 刷新已展开的分段
      if (expandedId === id) {
        const list = await knowledgeApi.listSegments(id)
        setSegments(list)
      }
    } catch (err) {
      setActionError((err as Error).message)
    } finally {
      setReprocessingId(null)
    }
  }, [expandedId, load])

  const startEditSegment = useCallback((segment: KnowledgeSegment) => {
    setEditingSegmentId(segment.id)
    setEditingSegmentContent(segment.content)
  }, [])

  /** 保存分段内容并重新向量化 */
  const commitEditSegment = useCallback(async () => {
    if (editingSegmentId == null) return
    const text = editingSegmentContent.trim()
    if (!text) {
      setEditingSegmentId(null)
      return
    }
    setActionError('')
    try {
      await knowledgeApi.updateSegment(editingSegmentId, text)
      setEditingSegmentId(null)
      if (expandedId != null) {
        const list = await knowledgeApi.listSegments(expandedId)
        setSegments(list)
      }
    } catch (err) {
      setActionError((err as Error).message)
    }
  }, [editingSegmentId, editingSegmentContent, expandedId])

  const handleDeleteSegment = useCallback(async (id: number) => {
    if (!window.confirm('确认删除该分段？')) return
    setActionError('')
    try {
      await knowledgeApi.deleteSegment(id)
      if (expandedId != null) {
        const list = await knowledgeApi.listSegments(expandedId)
        setSegments(list)
      }
      await load()
    } catch (err) {
      setActionError((err as Error).message)
    }
  }, [expandedId, load])

  const formatTime = (value?: string) => {
    if (!value) return ''
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
      + `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }

  const statusBadge = (status: string) => {
    switch (status) {
      case 'READY':
        return <span className="badge badge--success">已就绪</span>
      case 'PROCESSING':
        return <span className="badge badge--warning">处理中</span>
      case 'FAILED':
        return <span className="badge badge--danger">失败</span>
      default:
        return <span className="badge">{status}</span>
    }
  }

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
          <h1 className="page__title">📚 知识库</h1>
          <div className="page__desc">文档分段 · bge-m3 向量化 · pgvector 相似度检索</div>
        </div>
      </header>

      <div className="page__body knowledge">
        {error && <div className="knowledge__error">{error}</div>}

        <div className="knowledge__grid">
          <section className="card">
            <h2 className="card__title">上传文档</h2>
            <form onSubmit={handleUpload}>
              <div className="field">
                <label className="field__label">文档名称</label>
                <input
                  className="input"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="如：产品使用手册"
                />
              </div>
              <div className="field">
                <label className="field__label">文档内容</label>
                <textarea
                  className="textarea knowledge__textarea"
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  placeholder="粘贴文档内容，系统将自动分段并向量化"
                />
              </div>
              <button type="submit" className="btn btn--primary" disabled={uploading}>
                {uploading ? '上传中…' : '上传并向量化'}
              </button>
            </form>
          </section>

          <section className="card">
            <h2 className="card__title">检索测试</h2>
            <form onSubmit={handleSearch}>
              <div className="field">
                <label className="field__label">查询内容</label>
                <input
                  className="input"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="输入问题，检索相关知识片段"
                />
              </div>
              <button type="submit" className="btn btn--primary" disabled={searching}>
                {searching ? '检索中…' : '检索 Top5'}
              </button>
            </form>

            {searchResults.length > 0 && (
              <div className="knowledge__results">
                {searchResults.map((r) => (
                  <div key={r.id} className="knowledge__result">
                    <div className="knowledge__result-head">
                      <span className="knowledge__result-doc">{r.documentName}</span>
                      <span className="badge badge--primary">
                        相似度 {(r.similarity * 100).toFixed(1)}%
                      </span>
                    </div>
                    <div className="knowledge__result-content">{r.content}</div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>

        <section className="knowledge__list">
          <h2 className="card__title">文档列表</h2>
          {documents.length === 0 && <div className="empty">知识库为空，先上传文档吧</div>}
          {documents.map((doc) => (
            <div key={doc.id} className="card knowledge__doc">
              <div className="knowledge__doc-main">
                <div className="knowledge__doc-head">
                  <span className="knowledge__doc-name">{doc.name}</span>
                  {statusBadge(doc.status)}
                </div>
                <div className="card__meta">
                  分段数: {doc.segmentCount}
                  {doc.createdAt && ` · 创建于 ${formatTime(doc.createdAt)}`}
                </div>
                <div className="knowledge__doc-preview">{doc.contentPreview ?? ''}</div>
                {doc.status === 'FAILED' && doc.errorMessage && (
                  <div className="knowledge__doc-error">失败原因：{doc.errorMessage}</div>
                )}

                <div className="knowledge__doc-actions">
                  <button
                    className="btn btn--sm"
                    onClick={() => void toggleSegments(doc)}
                    disabled={segmentsLoading}
                  >
                    {expandedId === doc.id ? '收起分段' : '分段明细'}
                  </button>
                  <button
                    className="btn btn--sm btn--ghost"
                    onClick={() => void handleReprocess(doc.id)}
                    disabled={reprocessingId === doc.id}
                    title="清除旧向量并重新分段、重新向量化"
                  >
                    {reprocessingId === doc.id ? '处理中…' : '重新向量化'}
                  </button>
                </div>

                {actionError && expandedId === doc.id && (
                  <div className="knowledge__doc-error">{actionError}</div>
                )}

                {expandedId === doc.id && (
                  <div className="knowledge__segments">
                    {segmentsLoading && (
                      <div className="knowledge__segments-loading">加载分段中…</div>
                    )}
                    {!segmentsLoading && segments.length === 0 && (
                      <div className="knowledge__segments-empty">
                        暂无分段，文档可能仍在处理中
                      </div>
                    )}
                    {segments.map((seg) => (
                      <div key={seg.id} className="knowledge__segment">
                        <div className="knowledge__segment-head">
                          <span className="knowledge__segment-index">
                            分段 #{seg.chunkIndex}
                          </span>
                          {seg.hasEmbedding ? (
                            <span className="badge badge--success">已向量化</span>
                          ) : (
                            <span className="badge badge--warning">未向量化</span>
                          )}
                        </div>

                        {editingSegmentId === seg.id ? (
                          <>
                            <textarea
                              className="textarea knowledge__segment-edit"
                              value={editingSegmentContent}
                              onChange={(e) => setEditingSegmentContent(e.target.value)}
                              autoFocus
                            />
                            <div className="knowledge__segment-actions">
                              <button
                                className="btn btn--sm btn--primary"
                                onClick={() => void commitEditSegment()}
                              >
                                保存并重新向量化
                              </button>
                              <button
                                className="btn btn--sm"
                                onClick={() => setEditingSegmentId(null)}
                              >
                                取消
                              </button>
                            </div>
                          </>
                        ) : (
                          <>
                            <div className="knowledge__segment-content">{seg.content}</div>
                            <div className="knowledge__segment-actions">
                              <button
                                className="btn btn--sm"
                                onClick={() => startEditSegment(seg)}
                              >
                                编辑
                              </button>
                              <button
                                className="btn btn--sm btn--danger"
                                onClick={() => void handleDeleteSegment(seg.id)}
                              >
                                删除
                              </button>
                            </div>
                          </>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <button className="btn btn--sm btn--danger" onClick={() => handleDelete(doc.id)}>
                删除
              </button>
            </div>
          ))}
        </section>
      </div>
    </div>
  )
}
