import { apiClient } from '@/shared/api/client'
import type { VoiceEvidenceDTO, VoiceMessageDTO } from '@/entities/voice'

export const voiceApi = {
  upload: async (params: {
    file: Blob
    fileName?: string
    graphId?: string
    situationId?: string
    conversationId?: string
    language?: string
  }): Promise<VoiceMessageDTO> => {
    const fd = new FormData()
    fd.append('file', params.file, params.fileName ?? 'voice.webm')
    if (params.graphId) fd.append('graphId', params.graphId)
    if (params.situationId) fd.append('situationId', params.situationId)
    if (params.conversationId) fd.append('conversationId', params.conversationId)
    if (params.language) fd.append('language', params.language)
    const res = await apiClient.post('/voice-messages', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return res.data
  },

  list: async (params: { graphId?: string; situationId?: string }): Promise<VoiceMessageDTO[]> => {
    const res = await apiClient.get('/voice-messages', { params })
    return res.data
  },

  get: async (id: string): Promise<VoiceMessageDTO> => {
    const res = await apiClient.get(`/voice-messages/${id}`)
    return res.data
  },

  reanalyze: async (id: string): Promise<VoiceMessageDTO> => {
    const res = await apiClient.post(`/voice-messages/${id}/reanalyze`)
    return res.data
  },

  audioUrl: (id: string) => `/api/voice-messages/${id}/audio`,

  fetchAudioObjectUrl: async (id: string): Promise<string> => {
    const res = await apiClient.get(`/voice-messages/${id}/audio`, {
      responseType: 'blob',
    })
    return URL.createObjectURL(res.data as Blob)
  },

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/voice-messages/${id}`)
  },
}

export const voiceEvidenceApi = {
  upsert: async (params: {
    graphId: string
    voiceId: string
    label?: string
    classification?: string
    severity?: string
    summary?: string
    speakers?: string
    situationId?: string
  }): Promise<VoiceEvidenceDTO> => {
    const { graphId, voiceId, ...body } = params
    const res = await apiClient.post(`/v1/user-graphs/${graphId}/voice-evidence/${voiceId}`, body)
    return res.data
  },

  list: async (graphId: string): Promise<VoiceEvidenceDTO[]> => {
    const res = await apiClient.get(`/v1/user-graphs/${graphId}/voice-evidence`)
    return res.data
  },

  linkArticle: async (
    graphId: string,
    voiceId: string,
    lawCode: string,
    number: string,
    country = 'RK',
    reason?: string,
  ): Promise<void> => {
    await apiClient.post(
      `/v1/user-graphs/${graphId}/voice-evidence/${voiceId}/articles/${encodeURIComponent(lawCode)}/${encodeURIComponent(number)}`,
      null,
      { params: { country, reason } },
    )
  },

  linkSituation: async (graphId: string, voiceId: string, situationId: string): Promise<void> => {
    await apiClient.post(`/v1/user-graphs/${graphId}/voice-evidence/${voiceId}/situations/${situationId}`)
  },

  remove: async (graphId: string, voiceId: string): Promise<void> => {
    await apiClient.delete(`/v1/user-graphs/${graphId}/voice-evidence/${voiceId}`)
  },
}
