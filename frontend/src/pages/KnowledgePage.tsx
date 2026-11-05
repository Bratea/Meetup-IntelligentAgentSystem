import { useCallback, useEffect, useState } from 'react'
import { knowledgeApi, type KnowledgeDocument, type KnowledgeSearchResult } from '../api/knowledge'
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

  useEffect(() => {
    load()
  }, [load])

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
      await load()
      // 向量化是异步的，稍后刷新状态
      setTimeout(load, 3000)
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
                  {doc.createdAt && ` · 创建于 ${doc.createdAt}`}
                </div>
                <div className="knowledge__doc-preview">{doc.content.slice(0, 180)}</div>
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
