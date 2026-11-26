import type { UserGraphEdge, UserGraphNode } from "@/entities/graph";

const ARTICLE_ID = /^(.+?):(\d+(?:[-.]\d+)*)$/;

export function sanitizeGraph(
  nodes: UserGraphNode[],
  edges: UserGraphEdge[],
): { nodes: UserGraphNode[]; edges: UserGraphEdge[] } {
  const byId = new Map<string, UserGraphNode>();
  for (const n of nodes) if (n?.id && !byId.has(n.id)) byId.set(n.id, n);

  const ensure = (id: string): boolean => {
    if (byId.has(id)) return true;
    const m = ARTICLE_ID.exec(id);
    if (!m) return false;
    byId.set(id, {
      id,
      kind: "ARTICLE",
      label: `${m[1]} ст. ${m[2]}`,
      code: m[1],
    });
    return true;
  };

  const kept = edges.filter(
    (e) => e?.source && e?.target && ensure(e.source) && ensure(e.target),
  );
  return { nodes: [...byId.values()], edges: kept };
}
