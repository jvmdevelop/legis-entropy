export interface TemplatePlaceholder {
  name: string
  label: string
  required?: boolean
  multiline?: boolean
}

export interface TemplateSlot {
  id: string
  label: string
  context?: string
  type?: 'TEXT' | 'NAME' | 'DATE' | 'ADDRESS' | 'PHONE' | 'AMOUNT' | 'EMAIL' | 'MULTILINE' | 'ENUM' | 'ARTICLE_REF'
  placement?: { preceding?: string; following?: string }
  aiHint?: string
  examples?: string[]
  required?: boolean
  multiline?: boolean
}

export interface TemplateDTO {
  id: string
  code: string
  title: string
  description?: string
  kind: string
  body: string
  placeholdersJson?: string
  slotsJson?: string
  suggestedLawCodes?: string
  systemTemplate: boolean
  userId?: string | null
  sourceFormat?: string
  aiSummary?: string
  createdAt: string
  updatedAt: string
}

export interface RenderTemplateRequest {
  title?: string
  graphId?: string
  situationId?: string
  variables: Record<string, string>
}

export interface GeneratedDocumentDTO {
  id: string
  userId: string
  templateId?: string
  templateCode?: string
  graphId?: string
  situationId?: string
  title: string
  bodyMarkdown: string
  objectName?: string
  variablesJson?: string
  createdAt: string
  updatedAt: string
}
