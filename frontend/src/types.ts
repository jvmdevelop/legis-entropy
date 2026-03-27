export type DocumentStatus = 'active' | 'outdated' | 'unknown';
export type Severity = 'low' | 'medium' | 'high';
export type IssueKind = 'duplication' | 'contradiction' | 'outdated_reference' | 'circular_reference';
export type Assessment = 'duplicate' | 'highly_related' | 'related' | 'independent';

export interface Issue {
  kind: IssueKind;
  severity: Severity;
  document_ids: string[];
  explanation: string;
}

export interface GraphNode {
  id: string;
  title: string;
  url: string;
  status: DocumentStatus;
  ref_count: number;
  issue_count: number;
  x?: number;
  y?: number;
}

export interface GraphLink {
  source: string | GraphNode;
  target: string | GraphNode;
}

export interface GraphData {
  nodes: GraphNode[];
  links: GraphLink[];
  issues: Issue[];
}

export interface CorpusStats {
  total_documents: number;
  active_count: number;
  outdated_count: number;
  unknown_count: number;
  with_issues: number;
  total_links: number;
  avg_ref_count: number;
  issue_breakdown: Record<string, number>;
  severity_breakdown: Record<string, number>;
  top_referenced: GraphNode[];
  most_problematic: GraphNode[];
}

export interface SearchResult {
  id: string;
  title: string;
  score: number;
}

export interface CompareResult {
  doc_a: GraphNode;
  doc_b: GraphNode;
  similarity: number;
  assessment: Assessment;
  explanation: string;
  shared_issues: number;
}

export interface AnalysisEvent {
  stage: 'started' | 'graph_loaded' | 'analyzing' | 'complete' | 'error';
  message: string;
  progress: number;
  data?: { graph: GraphData; stats: CorpusStats } | null;
}
