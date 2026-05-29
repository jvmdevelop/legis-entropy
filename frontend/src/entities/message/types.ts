export type MessageRole = 'USER' | 'ASSISTANT'

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  last: boolean
  first: boolean
}

export interface MessageResponse {
  id: number | string
  conversationId: number | string
  role: MessageRole
  content: string
  tokens?: number | null
  createdAt: string
}

export interface ChatResponse {
  userMessage: MessageResponse
  assistantMessage: MessageResponse
}

export interface StreamingMessage {
  id: string
  content: string
  isStreaming: boolean
}
