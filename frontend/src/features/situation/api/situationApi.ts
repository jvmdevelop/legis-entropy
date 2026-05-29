import { apiClient } from '@/shared/api/client'
import type {
  CreateSituationRequest,
  SituationDTO,
  UpdateSituationRequest,
} from '@/entities/situation'

const base = (graphId: string) => `/v1/user-graphs/${graphId}/situations`

export const situationApi = {
  list: async (graphId: string): Promise<SituationDTO[]> => {
    const res = await apiClient.get(base(graphId))
    return res.data
  },

  get: async (graphId: string, situationId: string): Promise<SituationDTO> => {
    const res = await apiClient.get(`${base(graphId)}/${situationId}`)
    return res.data
  },

  create: async (graphId: string, request: CreateSituationRequest): Promise<SituationDTO> => {
    const res = await apiClient.post(base(graphId), request)
    return res.data
  },

  update: async (
    graphId: string,
    situationId: string,
    request: UpdateSituationRequest,
  ): Promise<SituationDTO> => {
    const res = await apiClient.patch(`${base(graphId)}/${situationId}`, request)
    return res.data
  },

  remove: async (graphId: string, situationId: string): Promise<void> => {
    await apiClient.delete(`${base(graphId)}/${situationId}`)
  },

  linkDocument: async (graphId: string, situationId: string, documentId: string): Promise<void> => {
    await apiClient.post(`${base(graphId)}/${situationId}/documents/${documentId}`)
  },

  unlinkDocument: async (graphId: string, situationId: string, documentId: string): Promise<void> => {
    await apiClient.delete(`${base(graphId)}/${situationId}/documents/${documentId}`)
  },

  linkArticle: async (
    graphId: string,
    situationId: string,
    lawCode: string,
    number: string,
    country = 'RK',
  ): Promise<void> => {
    await apiClient.post(
      `${base(graphId)}/${situationId}/articles/${encodeURIComponent(lawCode)}/${encodeURIComponent(number)}`,
      null,
      { params: { country } },
    )
  },

  unlinkArticle: async (
    graphId: string,
    situationId: string,
    lawCode: string,
    number: string,
    country = 'RK',
  ): Promise<void> => {
    await apiClient.delete(
      `${base(graphId)}/${situationId}/articles/${encodeURIComponent(lawCode)}/${encodeURIComponent(number)}`,
      { params: { country } },
    )
  },
}
