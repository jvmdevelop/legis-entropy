export type RelationType =
  | 'REFERENCES'
  | 'AMENDS'
  | 'DEFINES'
  | 'RELATED'
  | 'SUPERSEDES'
  | 'IMPLEMENTS'

export interface LawDTO {
  id: number
  title: string
  code: string
  country: string
  type: string
  adoptionDate: string
  effectiveDate: string
  status: string
  summary: string
}

export interface LawGraphResponse {
  law: LawDTO
  relationships: Record<RelationType, LawDTO[]>
}

export interface GraphNode {
  id: string
  label: string
  code: string
  type: 'main' | 'related'
}

export interface GraphEdge {
  source: string
  target: string
  relationType: RelationType
}

export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface UserGraphDTO {
  id: string
  workspaceId: string
  userId: string
  name: string
  description?: string
  createdAt: string
  updatedAt?: string
  lawCount: number
  documentCount: number
  situationCount?: number
}

export type UserGraphNodeKind =
  | 'LAW'
  | 'DOCUMENT'
  | 'ARTICLE'
  | 'SITUATION'
  | 'VOICE'
  | 'COMMENT'

export type ProvenanceSource =
  | 'GOV_PARSER'
  | 'LLM_INFERENCE'
  | 'USER_MANUAL'
  | 'IMPORT_CSV'
  | 'UNKNOWN'

export interface Provenance {
  source?: ProvenanceSource
  extractedBy?: string
  extractedAt?: string
  confidence?: number
  verifiedBy?: string
  verifiedAt?: string
  sourceUri?: string
}

export interface UserGraphNode {
  id: string
  kind: UserGraphNodeKind
  label: string
  code?: string
  type?: string
  summary?: string
  provenance?: Provenance

  severity?: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'

  generatedDocId?: string
  generatedDocTitle?: string
}

export interface UserGraphEdge {
  source: string
  target: string
  relationType: string
  kind: string
  clauseRef?: string
  confidence?: number
  reason?: string
}

export interface ArticleDTO {
  id: number
  lawCode: string
  country: string
  number: string
  title?: string
  body?: string
  anchor?: string
  lastAmendmentDate?: string
  validFrom?: string
  validUntil?: string
  source?: ProvenanceSource
  extractedBy?: string
  extractedAt?: string
  confidence?: number
  verifiedBy?: string
  verifiedAt?: string
  sourceUri?: string
}

export interface AmendmentEdgeDTO {
  amendmentDate?: string
  effectiveFrom?: string
  anchor?: string
  contextSnippet?: string
  byLawCode?: string
  byLawTitle?: string
  byLawSourceUri?: string
  byInternalNumber?: string
}

export interface ArticleHistoryDTO {
  article: ArticleDTO
  amendments: AmendmentEdgeDTO[]
  lastAmendmentDate?: string
}

export interface GraphContentResponse {
  graph: UserGraphDTO
  nodes: UserGraphNode[]
  edges: UserGraphEdge[]
}
