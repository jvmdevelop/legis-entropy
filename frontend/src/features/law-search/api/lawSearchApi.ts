import { apiClient } from '@/shared/api/client'

export interface SemanticChunk {
  text: string
  documentId: string | null
  fileName: string | null
  page: number | null
  chunkIndex: number | null
  score: number | null
  metadata: Record<string, unknown> | null
}

export const lawSearchApi = {

  semantic: async (query: string, country = 'RK'): Promise<SemanticChunk[]> => {
    const res = await apiClient.get(`/laws/search/${encodeURIComponent(country)}`, {
      params: { query },
    })
    return res.data as SemanticChunk[]
  },
}
