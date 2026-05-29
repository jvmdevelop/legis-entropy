import { apiClient } from '@/shared/api/client'
import type { ConversationResponse, Page } from '@/entities/conversation/types'

export const conversationApi = {
  list(page = 0, size = 20) {
    return apiClient
      .get<Page<ConversationResponse>>('/conversations', { params: { page, size } })
      .then(r => r.data)
  },

  listActive(page = 0, size = 50) {
    return apiClient
      .get<Page<ConversationResponse>>('/conversations/active', { params: { page, size } })
      .then(r => r.data)
  },

  create(title: string) {
    return apiClient
      .post<ConversationResponse>('/conversations', { title })
      .then(r => r.data)
  },

  delete(id: string) {
    return apiClient.delete(`/conversations/${id}`)
  },

  toggleActive(id: string) {
    return apiClient
      .patch<ConversationResponse>(`/conversations/${id}/toggle-active`)
      .then(r => r.data)
  },
}
