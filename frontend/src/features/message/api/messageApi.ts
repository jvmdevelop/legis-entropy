import { apiClient } from '@/shared/api/client'
import type { MessageResponse, ChatResponse, Page } from '@/entities/message/types'

export interface ChatRequest {
  conversationId: string | number
  content: string
  documentId?: string | number | null

  graphId?: string | null
  situationId?: string | null
}

export const messageApi = {
  list(conversationId: string, page = 0, size = 50) {
    return apiClient
      .get<Page<MessageResponse>>(`/messages/conversation/${conversationId}`, {
        params: { page, size },
      })
      .then(r => r.data)
  },

  chat(req: ChatRequest) {
    return apiClient.post<ChatResponse>('/messages/chat', req).then(r => r.data)
  },

  getStreamUrl() {
    return '/api/messages/chat/stream'
  },
}
