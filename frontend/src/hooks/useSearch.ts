import { useState, useRef, useCallback } from 'react';
import { api } from '../api/client';

const DEBOUNCE_MS = 350;

/**
 * Debounced semantic search. Calls onResults with matched document IDs.
 * The callback ref pattern prevents stale closures without adding it to deps.
 */
export function useSearch(onResults: (ids: string[]) => void) {
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onResultsRef = useRef(onResults);
  onResultsRef.current = onResults;

  const search = useCallback((query: string) => {
    if (debounceRef.current) clearTimeout(debounceRef.current);

    if (!query.trim()) {
      onResultsRef.current([]);
      return;
    }

    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const data = await api.search(query);
        onResultsRef.current(data.results.map(r => r.id));
      } catch {
        onResultsRef.current([]);
      } finally {
        setLoading(false);
      }
    }, DEBOUNCE_MS);
  }, []);

  const clear = useCallback(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    onResultsRef.current([]);
  }, []);

  return { loading, search, clear };
}
