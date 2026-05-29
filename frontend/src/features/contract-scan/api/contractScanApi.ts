import { apiClient } from '@/shared/api/client'

export type CitationStatus =
  | 'OK'
  | 'RECENTLY_AMENDED'
  | 'NOT_FOUND'
  | 'UNKNOWN_CODE'

export interface CitationCheck {
  raw: string
  number: string
  inferredCode: string | null
  contextSnippet: string | null
  foundInGraph: boolean
  articleTitle: string | null
  lastAmendmentDate: string | null
  anchor: string | null
  sourceUri: string | null
  status: CitationStatus
}

export interface ContractScanResponse {
  citations: CitationCheck[]
  totalCitations: number
  totalFound: number
  totalMissing: number
  totalUnknownCode: number
}

export type RiskLevel = 'COMPLIANT' | 'WARNING' | 'VIOLATION'

export interface ClauseRiskResult {
  clauseId: string
  clauseText: string
  riskLevel: RiskLevel
  lawCitation: string | null
  reason: string | null
}

export interface MissingClauseResult {
  requiredClause: string
  lawBasis: string
}

export interface RiskScanResponse {
  contractType: string
  clauses: ClauseRiskResult[]
  missingClauses: MissingClauseResult[]
  totalViolations: number
  totalWarnings: number
  totalCompliant: number
}

export const contractScanApi = {
  scan: async (text: string, country = 'RK'): Promise<ContractScanResponse> => {
    const res = await apiClient.post('/graph/contracts/scan', { text, country })
    return res.data as ContractScanResponse
  },

  riskScan: async (text: string, country = 'RK'): Promise<RiskScanResponse> => {
    const res = await apiClient.post('/brain/contracts/risk-scan', { text, country })
    return res.data as RiskScanResponse
  },
}
