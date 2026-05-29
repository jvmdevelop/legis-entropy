
export interface CommentDTO {
  id: string
  graphId: string
  userId: string
  title: string

  body: string

  preview?: string

  kind?: string

  subjectKind?: string
  subjectId?: string
  createdAt: string
}
