import type { CompareResult, GraphData, SearchResult } from '../types';

export class ApiError extends Error {
  readonly status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, options);
  if (!res.ok) throw new ApiError(`HTTP ${res.status}`, res.status);
  return res.json() as Promise<T>;
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const api = {
  graph(seeds?: string): Promise<GraphData> {
    const qs = seeds ? `?seeds=${encodeURIComponent(seeds)}` : '';
    return request(`/api/graph${qs}`);
  },

  search(query: string, topK = 6): Promise<{ results: SearchResult[] }> {
    return request('/api/search', {
      method: 'POST',
      headers: JSON_HEADERS,
      body: JSON.stringify({ query, top_k: topK }),
    });
  },

  compare(id1: string, id2: string): Promise<CompareResult> {
    return request('/api/compare', {
      method: 'POST',
      headers: JSON_HEADERS,
      body: JSON.stringify({ id1, id2 }),
    });
  },

  corpusReview(): Promise<{ review: string; llm_ready: boolean }> {
    return request('/api/corpus-review');
  },
};
