import { apiClient } from '@/shared/api/client'
import type { CommentDTO } from '@/entities/comment'

const base = (graphId: string) => `/v1/user-graphs/${graphId}/comments`

export const commentApi = {
  list: async (graphId: string): Promise<CommentDTO[]> => {
    const res = await apiClient.get(base(graphId))
    return res.data
  },

  get: async (graphId: string, commentId: string): Promise<CommentDTO> => {
    const res = await apiClient.get(`${base(graphId)}/${commentId}`)
    return res.data
  },

  create: async (
    graphId: string,
    payload: {
      title?: string
      body: string
      preview?: string
      kind?: string
      subjectKind?: string
      subjectId?: string
      referencedLawCodes?: string[]
      referencedArticles?: { lawCode: string; number: string; country?: string; reason?: string }[]
    },
  ): Promise<CommentDTO> => {
    const res = await apiClient.post(base(graphId), payload)
    return res.data
  },

  remove: async (graphId: string, commentId: string): Promise<void> => {
    await apiClient.delete(`${base(graphId)}/${commentId}`)
  },
}
