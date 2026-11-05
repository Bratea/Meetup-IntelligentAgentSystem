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

export const knowledgeApi = {
  listDocuments: () => request<KnowledgeDocument[]>('/knowledge/documents'),
  getDocument: (id: number) => request<KnowledgeDocument>(`/knowledge/documents/${id}`),
  upload: (name: string, content: string) =>
    request<KnowledgeDocument>('/knowledge/documents', {
      method: 'POST',
      body: JSON.stringify({ name, content }),
    }),
  delete: (id: number) => request<void>(`/knowledge/documents/${id}`, { method: 'DELETE' }),
  search: (query: string, topK?: number) =>
    request<KnowledgeSearchResult[]>('/knowledge/search', {
      method: 'POST',
      body: JSON.stringify({ query, topK }),
    }),
}
