import { useState, useEffect, useCallback } from 'react';
import type { GraphData } from '../types';

export function useGraphData(seeds?: string) {
  const [data, setData] = useState<GraphData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchGraph = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = seeds ? `?seeds=${encodeURIComponent(seeds)}` : '';
      const res = await fetch(`/api/graph${params}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json: GraphData = await res.json();
      setData(json);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  }, [seeds]);

  useEffect(() => {
    fetchGraph();
  }, [fetchGraph]);

  return { data, loading, error, refetch: fetchGraph };
}
