import { apiClient } from '@/shared/api/client'

export interface SubscriptionDTO {
  id: string
  userId: string
  lawCode: string
  country: string
  articleNumber: string
  articleTitle: string | null
  createdAt: string
  lastCheckedAt: string | null
  unreadEvents: number
}

export interface ChangeEventDTO {
  id: string
  subscriptionId: string
  lawCode: string
  country: string
  articleNumber: string
  oldAmendmentDate: string | null
  newAmendmentDate: string | null
  detectedAt: string
  acknowledged: boolean
}

export const subscriptionApi = {
  create: async (
    lawCode: string,
    articleNumber: string,
    country = 'RK',
  ): Promise<SubscriptionDTO> => {
    const res = await apiClient.post('/graph/subscriptions', {
      lawCode, articleNumber, country,
    })
    return res.data as SubscriptionDTO
  },

  list: async (): Promise<SubscriptionDTO[]> => {
    const res = await apiClient.get('/graph/subscriptions')
    return res.data as SubscriptionDTO[]
  },

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/graph/subscriptions/${encodeURIComponent(id)}`)
  },

  events: async (): Promise<ChangeEventDTO[]> => {
    const res = await apiClient.get('/graph/subscriptions/events')
    return res.data as ChangeEventDTO[]
  },

  unreadCount: async (): Promise<number> => {
    const res = await apiClient.get('/graph/subscriptions/events/unread-count')
    return res.data?.count ?? 0
  },

  acknowledgeAll: async (): Promise<void> => {
    await apiClient.post('/graph/subscriptions/events/acknowledge')
  },
}
