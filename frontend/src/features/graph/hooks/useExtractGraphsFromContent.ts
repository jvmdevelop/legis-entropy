import { useCallback } from "react";
import { graphApi } from "../api/graphApi";
import { useGraphStore } from "../model/useGraphStore";

export function useExtractGraphsFromContent() {
  const { addGraph, setLoading, setError } = useGraphStore();

  return useCallback(
    async (content: string) => {
      try {
        const lawCodePattern = /№\s*(\d+(?:-\w+)?)/gi;
        const matches = Array.from(content.matchAll(lawCodePattern));
        const uniqueCodes = new Set(matches.map((m) => m[1]));

        if (uniqueCodes.size === 0) return;

        setLoading(true);

        for (const code of Array.from(uniqueCodes).slice(0, 3)) {
          try {
            const graph = await graphApi.getLawWithRelationships(code);
            addGraph(graph);
          } catch (err) {
            console.error(`Failed to fetch graph for ${code}:`, err);
          }
        }
      } catch (err) {
        const message =
          err instanceof Error ? err.message : "Failed to extract graphs";
        setError(message);
      } finally {
        setLoading(false);
      }
    },
    [addGraph, setLoading, setError],
  );
}
