export type VoiceMessageStatus =
  | 'UPLOADED'
  | 'TRANSCRIBING'
  | 'TRANSCRIBED'
  | 'ANALYZING'
  | 'ANALYZED'
  | 'ERROR'

export interface VoiceSegment {
  speaker: string
  startMs: number
  endMs: number
  text: string
}

export interface VoiceAnalysis {
  kind: string
  severity: string
  summary: string
  quotes: string[]
  articles: { lawCode: string; number: string; reason: string }[]
}

export interface VoiceMessageDTO {
  id: string
  userId: string
  graphId?: string
  situationId?: string
  conversationId?: string
  fileName?: string
  contentType?: string
  durationMs?: number
  language?: string
  status: VoiceMessageStatus
  transcript?: string
  transcriptJson?: string
  analysisJson?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

export interface VoiceEvidenceDTO {
  id: string
  graphId: string
  userId: string
  label: string
  classification?: string
  severity?: string
  summary?: string
  speakers?: string
  createdAt: string
}
