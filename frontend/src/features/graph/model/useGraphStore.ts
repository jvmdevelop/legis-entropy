import { create } from "zustand";
import type { LawGraphResponse, GraphData } from "@/entities/graph";

interface GraphState {
  graphs: LawGraphResponse[];
  selectedGraphCode: string | null;
  isLoading: boolean;
  error: string | null;

  addGraph: (graph: LawGraphResponse) => void;
  removeGraph: (code: string) => void;
  setSelectedGraph: (code: string | null) => void;
  clearGraphs: () => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
}

export const useGraphStore = create<GraphState>((set) => ({
  graphs: [],
  selectedGraphCode: null,
  isLoading: false,
  error: null,

  addGraph: (graph) =>
    set((state) => ({
      graphs: [graph, ...state.graphs].slice(0, 10),
    })),

  removeGraph: (code) =>
    set((state) => ({
      graphs: state.graphs.filter((g) => g.law.code !== code),
      selectedGraphCode:
        state.selectedGraphCode === code ? null : state.selectedGraphCode,
    })),

  setSelectedGraph: (code) => set({ selectedGraphCode: code }),

  clearGraphs: () => set({ graphs: [], selectedGraphCode: null }),

  setLoading: (loading) => set({ isLoading: loading }),

  setError: (error) => set({ error }),
}));

export function convertToGraphData(response: LawGraphResponse): GraphData {
  const nodes: GraphData["nodes"] = [
    {
      id: response.law.code,
      label: response.law.title,
      code: response.law.code,
      type: "main",
    },
  ];

  const edges: GraphData["edges"] = [];

  Object.entries(response.relationships).forEach(([relationType, laws]) => {
    laws.forEach((law) => {
      const nodeId = `${law.code}-${relationType}`;
      nodes.push({
        id: nodeId,
        label: law.title,
        code: law.code,
        type: "related",
      });
      edges.push({
        source: response.law.code,
        target: nodeId,
        relationType: relationType as any,
      });
    });
  });

  return { nodes, edges };
}
