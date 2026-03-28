import { useState, useCallback } from 'react';
import { api } from '../api/client';

export interface CorpusReview {
  review: string;
  llm_ready: boolean;
}

export function useCorpusReview() {
  const [data, setData] = useState<CorpusReview | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api.corpusReview());
    } finally {
      setLoading(false);
    }
  }, []);

  const dismiss = useCallback(() => setData(null), []);

  return { data, loading, load, dismiss };
}
