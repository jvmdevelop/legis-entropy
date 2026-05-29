import { apiClient } from '@/shared/api/client'
import type { ArticleDTO, ArticleHistoryDTO } from '@/entities/graph'

const BASE = '/graph'

export interface LinkClauseToArticleRequest {
  graphId: string
  documentId: string
  lawCode: string
  country?: string
  articleNumber: string
  clauseRef?: string
  documentSnippet?: string
  articleSnippet?: string
  extractedBy?: string
  confidence?: number
}

export const articleApi = {
  listByLaw: async (code: string, country: string = 'RK'): Promise<ArticleDTO[]> => {
    const res = await apiClient.get(`${BASE}/laws/${encodeURIComponent(code)}/articles`, {
      params: { country },
    })
    return res.data
  },

  get: async (code: string, number: string, country: string = 'RK'): Promise<ArticleDTO> => {
    const res = await apiClient.get(
      `${BASE}/laws/${encodeURIComponent(code)}/articles/${encodeURIComponent(number)}`,
      { params: { country } },
    )
    return res.data
  },

  search: async (query: string, country: string = 'RK'): Promise<ArticleDTO[]> => {
    const res = await apiClient.get(`${BASE}/articles/search`, {
      params: { query, country },
    })
    return res.data
  },

  history: async (
    code: string,
    number: string,
    country: string = 'RK',
  ): Promise<ArticleHistoryDTO> => {
    const res = await apiClient.get(
      `${BASE}/laws/${encodeURIComponent(code)}/articles/${encodeURIComponent(number)}/history`,
      { params: { country } },
    )
    return res.data
  },

  linkDocumentClause: async (req: LinkClauseToArticleRequest): Promise<ArticleDTO> => {
    const res = await apiClient.post(`${BASE}/articles/link-document`, {
      country: 'RK',
      ...req,
    })
    return res.data
  },
}
