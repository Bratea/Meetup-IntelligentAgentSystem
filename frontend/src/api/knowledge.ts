/**
 * 知识库 API
 */

import { request } from './request'

export interface KnowledgeDocument {
  id: number
  name: string
  /** 列表接口仅返回 200 字预览，详情接口才返回全文 */
  contentPreview?: string
  /** 全文（仅详情接口返回） */
  content?: string
  status: 'PROCESSING' | 'READY' | 'FAILED' | string
  segmentCount: number
  /** 处理失败原因，仅 status=FAILED 时有值 */
  errorMessage?: string
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeSearchResult {
  id: number
  documentId: number
  documentName: string
  content: string
  similarity: number
}

/** 分段明细（不含向量，仅用 hasEmbedding 标记是否已向量化） */
export interface KnowledgeSegment {
  id: number
  documentId: number
  chunkIndex: number
  content: string
  hasEmbedding: boolean
  createdAt?: string
}

export const knowledgeApi = {
  listDocuments: () => request<KnowledgeDocument[]>('/knowledge/documents'),
  getDocument: (id: number) => request<KnowledgeDocument>(`/knowledge/documents/${id}`),
  upload: (name: string, content: string) =>
    request<KnowledgeDocument>('/knowledge/documents', {
      method: 'POST',
      body: JSON.stringify({ name, content }),
    }),
  delete: (id: number) => request<void>(`/knowledge/documents/${id}`, { method: 'DELETE' }),

  /** 分段明细 */
  listSegments: (documentId: number) =>
    request<KnowledgeSegment[]>(`/knowledge/documents/${documentId}/segments`),
  /** 重新向量化整个文档（异步） */
  reprocess: (documentId: number) =>
    request<void>(`/knowledge/documents/${documentId}/reprocess`, { method: 'POST' }),
  /** 编辑分段内容并重新向量化 */
  updateSegment: (id: number, content: string) =>
    request<void>(`/knowledge/segments/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ content }),
    }),
  /** 删除单个分段 */
  deleteSegment: (id: number) =>
    request<void>(`/knowledge/segments/${id}`, { method: 'DELETE' }),

  search: (query: string, topK?: number) =>
    request<KnowledgeSearchResult[]>('/knowledge/search', {
      method: 'POST',
      body: JSON.stringify({ query, topK }),
    }),
}
