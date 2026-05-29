export interface SituationDTO {
  id: string
  graphId: string
  userId: string
  title: string
  body: string
  plainText?: string
  createdAt: string
  updatedAt?: string
}

export interface CreateSituationRequest {
  title: string
  body: string
  plainText?: string
}

export interface UpdateSituationRequest {
  title?: string
  body?: string
  plainText?: string
}
