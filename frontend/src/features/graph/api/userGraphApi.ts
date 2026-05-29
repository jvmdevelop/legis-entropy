import { apiClient } from '@/shared/api/client'
import type { GraphContentResponse, UserGraphDTO } from '@/entities/graph'

const BASE = '/v1/user-graphs'

export const userGraphApi = {
  list: async (workspaceId: string): Promise<UserGraphDTO[]> => {
    const res = await apiClient.get(BASE, { params: { workspaceId } })
    return res.data
  },

  create: async (workspaceId: string, name: string, description?: string): Promise<UserGraphDTO> => {
    const res = await apiClient.post(BASE, { workspaceId, name, description })
    return res.data
  },

  get: async (graphId: string): Promise<UserGraphDTO> => {
    const res = await apiClient.get(`${BASE}/${graphId}`)
    return res.data
  },

  getContent: async (graphId: string): Promise<GraphContentResponse> => {
    const res = await apiClient.get(`${BASE}/${graphId}/content`)
    return res.data
  },

  remove: async (graphId: string): Promise<void> => {
    await apiClient.delete(`${BASE}/${graphId}`)
  },

  addLaw: async (graphId: string, code: string, country: string = 'RK'): Promise<void> => {
    await apiClient.post(`${BASE}/${graphId}/laws/${encodeURIComponent(code)}`, null, { params: { country } })
  },

  removeLaw: async (graphId: string, code: string, country: string = 'RK'): Promise<void> => {
    await apiClient.delete(`${BASE}/${graphId}/laws/${encodeURIComponent(code)}`, { params: { country } })
  },

  addDocument: async (
    graphId: string,
    documentId: string,
    fileName: string,
    mimeType?: string,
    fileUrl?: string,
    summary?: string,
  ): Promise<void> => {
    await apiClient.post(`${BASE}/${graphId}/documents`, { documentId, fileName, mimeType, fileUrl, summary })
  },

  removeDocument: async (graphId: string, documentId: string): Promise<void> => {
    await apiClient.delete(`${BASE}/${graphId}/documents/${documentId}`)
  },

  linkDocumentToLaw: async (
    graphId: string,
    documentId: string,
    lawCode: string,
    country: string = 'RK',
    kind: string = 'SEMANTIC',
  ): Promise<void> => {
    await apiClient.post(
      `${BASE}/${graphId}/documents/${documentId}/links/${encodeURIComponent(lawCode)}`,
      null,
      { params: { country, kind } },
    )
  },
}
