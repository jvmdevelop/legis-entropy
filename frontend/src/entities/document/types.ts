export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  last: boolean
  first: boolean
}

export type DocumentStatus = 'UPLOADING' | 'INDEXING' | 'READY' | 'ERROR' | 'DELETED'

export interface DocumentStatusResponse {
  id: string
  userId: string
  fileName: string
  status: DocumentStatus
  chunkCount?: number
  errorMessage?: string | null
  createdAt: string
  updatedAt: string
}
