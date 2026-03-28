import { useState, useEffect, useRef } from 'react';
import type { CompareResult } from '../types';
import { api } from '../api/client';

/**
 * Fetches comparison result for a pair of documents.
 * Deduplicates requests: same pair key → no re-fetch.
 */
export function useCompare(id1: string, id2: string) {
  const [result, setResult] = useState<CompareResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pairKey = `${id1}__${id2}`;
  const fetchedRef = useRef<string | null>(null);

  useEffect(() => {
    if (fetchedRef.current === pairKey) return;
    fetchedRef.current = pairKey;

    let cancelled = false;
    setLoading(true);
    setError(null);
    setResult(null);

    api.compare(id1, id2)
      .then(data => { if (!cancelled) setResult(data); })
      .catch(e => { if (!cancelled) setError(e instanceof Error ? e.message : String(e)); })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [pairKey, id1, id2]);

  return { result, loading, error };
}
