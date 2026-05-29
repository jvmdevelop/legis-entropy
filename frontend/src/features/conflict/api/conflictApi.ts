import { apiClient } from '@/shared/api/client'

export type ConflictKind = 'ARTICLE_ARTICLE' | 'DOC_ARTICLE'

export interface ConflictRow {
  kind: ConflictKind
  codeA: string | null
  numberA: string | null
  titleA: string | null
  codeB: string | null
  numberB: string | null
  titleB: string | null
  documentId: string | null
  clauseRef: string | null
  reason: string | null
  confidence: number | null
  extractedAt: string | null
}

export const conflictApi = {
  list: async (graphId: string): Promise<ConflictRow[]> => {
    const res = await apiClient.get('/graph/conflicts/list', { params: { graphId } })
    return res.data
  },

  count: async (graphId: string): Promise<number> => {
    const res = await apiClient.get('/graph/conflicts/count', { params: { graphId } })
    return res.data?.count ?? 0
  },
}
