import { apiClient } from '@/shared/api/client'
import type { LawGraphResponse, LawDTO } from '@/entities/graph'

const BASE_URL = '/graph/laws'

export const graphApi = {
  getLawWithRelationships: async (code: string, country: string = 'RK'): Promise<LawGraphResponse> => {
    const response = await apiClient.get(`${BASE_URL}/${code}`, { params: { country } })
    return response.data
  },

  findRelatedLaws: async (code: string, country: string = 'RK'): Promise<LawDTO[]> => {
    const response = await apiClient.get(`${BASE_URL}/${code}/related`, { params: { country } })
    return response.data
  },

  searchLaws: async (query: string, country: string = 'RK'): Promise<LawDTO[]> => {
    const response = await apiClient.get(`${BASE_URL}/search`, { params: { query, country } })
    return response.data
  },

  getLaw: async (code: string, country: string = 'RK'): Promise<LawDTO> => {
    const response = await apiClient.get(`${BASE_URL}/${code}/info`, { params: { country } })
    return response.data
  },
}
