import { apiClient } from '@/shared/api/client'
import type {
  GeneratedDocumentDTO,
  RenderTemplateRequest,
  TemplateDTO,
} from '@/entities/template'

export const templateApi = {
  list: async (kind?: string): Promise<TemplateDTO[]> => {
    const res = await apiClient.get('/templates', { params: kind ? { kind } : undefined })
    return res.data
  },

  upload: async (params: { file: Blob; fileName?: string; title?: string; kind?: string }): Promise<TemplateDTO> => {
    const fd = new FormData()
    fd.append('file', params.file, params.fileName ?? 'template.docx')
    if (params.title) fd.append('title', params.title)
    if (params.kind) fd.append('kind', params.kind)
    const res = await apiClient.post('/templates/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return res.data
  },

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/templates/${id}`)
  },

  get: async (id: string): Promise<TemplateDTO> => {
    const res = await apiClient.get(`/templates/${id}`)
    return res.data
  },

  getByCode: async (code: string): Promise<TemplateDTO> => {
    const res = await apiClient.get(`/templates/by-code/${code}`)
    return res.data
  },

  render: async (id: string, request: RenderTemplateRequest): Promise<GeneratedDocumentDTO> => {
    const res = await apiClient.post(`/templates/${id}/render`, request)
    return res.data
  },

  renderByCode: async (code: string, request: RenderTemplateRequest): Promise<GeneratedDocumentDTO> => {
    const res = await apiClient.post(`/templates/by-code/${code}/render`, request)
    return res.data
  },
}

export const generatedDocumentApi = {
  list: async (params: { situationId?: string; graphId?: string }): Promise<GeneratedDocumentDTO[]> => {
    const res = await apiClient.get('/generated-documents', { params })
    return res.data
  },

  get: async (id: string): Promise<GeneratedDocumentDTO> => {
    const res = await apiClient.get(`/generated-documents/${id}`)
    return res.data
  },

  rawUrl: (id: string) => `/api/generated-documents/${id}/raw`,

  updateBody: async (id: string, bodyMarkdown: string): Promise<GeneratedDocumentDTO> => {
    const res = await apiClient.put(`/generated-documents/${id}`, { bodyMarkdown })
    return res.data
  },

  regenerate: async (id: string): Promise<GeneratedDocumentDTO> => {
    const res = await apiClient.post(`/generated-documents/${id}/regenerate`)
    return res.data
  },

  exportUrl: (id: string, format: 'pdf' | 'docx') =>
    `/api/generated-documents/${id}/export?format=${format}`,

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/generated-documents/${id}`)
  },
}
