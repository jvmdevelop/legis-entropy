export interface ConversationResponse {
  id: string
  title: string
  active: boolean
  createdAt: string
  updatedAt: string
  userId: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  last: boolean
  first: boolean
}
