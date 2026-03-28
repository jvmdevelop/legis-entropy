import { useRef, useCallback, useEffect, memo, useMemo } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import type { GraphData, GraphNode } from '../types';

interface GraphProps {
  data: GraphData;
  onNodeClick: (node: GraphNode, event?: MouseEvent) => void;
  selectedId: string | null;
  compareId: string | null;
  highlightIds?: string[];
}

const STATUS_COLOR: Record<string, string> = {
  active: '#000000',
  outdated: '#666666',
  unknown: '#999999',
};
const STATUS_DIM: Record<string, string> = {
  active: '#e5e5e5',
  outdated: '#d4d4d4',
  unknown: '#f5f5f5',
};

/**
 * Fibonacci sunflower — each node gets a geometric position.
 * Hubs (high ref_count) placed at center, leaves radiate outward.
 * All nodes are PINNED (fx=x, fy=y) — simulation never moves them.
 * Each node gets _index for progressive reveal.
 */
/**
 * Grid layout — nodes sorted by ref_count (hubs first), placed in a square grid.
 * All nodes are pinned (fx=x, fy=y) so simulation never runs.
 * spacing scales so the grid always fits in ~2000×2000 world units.
 */
function gridLayout(nodes: GraphNode[]): GraphNode[] {
  const sorted = [...nodes].sort((a, b) => (b.ref_count ?? 0) - (a.ref_count ?? 0));
  const n = sorted.length;
  const cols = Math.ceil(Math.sqrt(n));
  const spacing = Math.max(200, 18000 / Math.max(cols, 1));
  sorted.forEach((node, i) => {
    const col = i % cols;
    const row = Math.floor(i / cols);
    const x = (col - cols / 2) * spacing;
    const y = (row - Math.ceil(n / cols) / 2) * spacing;
    (node as any).x = x;
    (node as any).y = y;
    (node as any).fx = x;
    (node as any).fy = y;
    (node as any)._index = i;
  });
  return sorted;
}

export const Graph = memo(function Graph({ data, onNodeClick, selectedId, compareId, highlightIds }: GraphProps) {
  const graphRef = useRef<any>(null);

  const highlightSet = useMemo(
    () => (highlightIds?.length ? new Set(highlightIds) : null),
    [highlightIds],
  );
  const hasHighlight = highlightSet !== null;

  // Full layout — computed once per data change, passed to ForceGraph2D ONCE
  const layoutNodes = useMemo(
    () => gridLayout(data.nodes.map(n => ({ ...n }))),
    [data],
  );
  const layoutLinks = useMemo(() => data.links.map(l => ({ ...l })), [data]);
  const nodeCount = layoutNodes.length;

  // id → reveal index map (for links before D3 mutates source/target to objects)
  const idToIndex = useMemo(() => {
    const m = new Map<string, number>();
    layoutNodes.forEach(n => m.set(n.id, (n as any)._index));
    return m;
  }, [layoutNodes]);

  // Stable graph object — passed to ForceGraph2D ONCE per data change
  // Never changes during progressive reveal → no ForceGraph2D reinitialisation
  const fullGraph = useMemo(
    () => ({ nodes: layoutNodes, links: layoutLinks }),
    [layoutNodes, layoutLinks],
  );

  // Disable all forces — layout is purely geometric (grid), no simulation needed
  useEffect(() => {
    const fg = graphRef.current;
    if (!fg) return;
    fg.d3Force('charge', null);
    fg.d3Force('collision', null);
    fg.d3Force('link', null);
    fg.d3Force('center', null);
  }, [layoutNodes]);

  // Progressive reveal via ref — NO React state, NO re-renders
  // The canvas render loop (~60fps) picks up revealRef.current automatically
  const revealRef = useRef(0);

  useEffect(() => {
    const BATCH = Math.max(20, Math.ceil(nodeCount / 40));
    revealRef.current = Math.min(BATCH, nodeCount);

    if (nodeCount <= BATCH) {
      revealRef.current = nodeCount;
      setTimeout(() => graphRef.current?.zoomToFit(400, 60), 100);
      return;
    }

    const id = setInterval(() => {
      revealRef.current = Math.min(revealRef.current + BATCH, nodeCount);
      if (revealRef.current >= nodeCount) {
        clearInterval(id);
        setTimeout(() => graphRef.current?.zoomToFit(400, 60), 100);
      }
    }, 120);

    return () => clearInterval(id);
  }, [layoutNodes, nodeCount]);

  // Hide links to unrevealed nodes — reads revealRef.current live
  const linkVisibility = useCallback((link: any) => {
    const srcIdx = typeof link.source === 'object'
      ? ((link.source as any)._index ?? 0)
      : (idToIndex.get(String(link.source)) ?? 0);
    const tgtIdx = typeof link.target === 'object'
      ? ((link.target as any)._index ?? 0)
      : (idToIndex.get(String(link.target)) ?? 0);
    return srcIdx < revealRef.current && tgtIdx < revealRef.current;
  }, [idToIndex]);

  const paintNode = useCallback(
    (node: GraphNode, ctx: CanvasRenderingContext2D, globalScale: number) => {
      // Skip unrevealed nodes — reads revealRef.current live, no stale closure
      if ((node as any)._index >= revealRef.current) return;

      const x = node.x ?? 0;
      const y = node.y ?? 0;
      const r = Math.max(4, Math.sqrt((node.ref_count ?? 0) + 1) * 3);

      // Sub-pixel culling
      if (r * globalScale < 0.4) return;

      const isSelected = node.id === selectedId;
      const isCompare = node.id === compareId;
      const isHit = hasHighlight && highlightSet!.has(node.id);
      const isDimmed = (hasHighlight && !isHit) || (!isSelected && !isCompare && selectedId !== null);

      if (isSelected) {
        ctx.beginPath(); ctx.arc(x, y, r + 6, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(0,0,0,0.15)'; ctx.fill();
      }
      if (isCompare) {
        ctx.beginPath(); ctx.arc(x, y, r + 6, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(102,102,102,0.15)'; ctx.fill();
      }
      if (isHit) {
        ctx.beginPath(); ctx.arc(x, y, r + 5, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(249,115,22,0.25)'; ctx.fill();
      }

      ctx.beginPath();
      ctx.arc(x, y, r, 0, 2 * Math.PI);
      ctx.fillStyle = isHit
        ? '#f97316'
        : node.is_amendment && !isDimmed
          ? '#333333'
          : isDimmed
            ? STATUS_DIM[node.status] ?? '#f5f5f5'
            : STATUS_COLOR[node.status] ?? '#999999';
      ctx.fill();

      if (globalScale > 0.3) {
        if (node.is_amendment && !isDimmed) {
          ctx.save();
          ctx.setLineDash([3 / globalScale, 2 / globalScale]);
          ctx.beginPath(); ctx.arc(x, y, r + 3, 0, 2 * Math.PI);
          ctx.strokeStyle = 'rgba(51,51,51,0.5)';
          ctx.lineWidth = 1.5 / globalScale; ctx.stroke();
          ctx.restore();
        }
        if (node.issue_count > 0 && !isDimmed && !node.is_amendment) {
          ctx.strokeStyle = '#666666';
          ctx.lineWidth = 1.5 / globalScale; ctx.stroke();
        }
        if (isSelected || isCompare) {
          ctx.strokeStyle = isCompare ? '#666666' : '#000000';
          ctx.lineWidth = 2.5 / globalScale; ctx.stroke();
        }
      }

      if ((r > 6 || isSelected || isHit) && globalScale > 0.5) {
        const label = node.title.length > 28 ? node.title.slice(0, 28) + '…' : node.title;
        const fontSize = Math.max(10, 12 / globalScale);
        ctx.font = `${fontSize}px Inter, system-ui, sans-serif`;
        ctx.fillStyle = isDimmed ? 'rgba(156,163,175,0.5)' : 'rgba(17,24,39,0.85)';
        ctx.textAlign = 'center';
        ctx.fillText(label, x, y + r + fontSize * 0.9);
      }
    },
    [selectedId, compareId, highlightSet, hasHighlight],
  );

  const nodeLabel = useCallback((node: GraphNode) => {
    const issues = node.issue_count > 0 ? ` | ${node.issue_count} проблем` : '';
    return `${node.title}${issues}`;
  }, []);

  return (
    <ForceGraph2D
      ref={graphRef}
      graphData={fullGraph as any}
      nodeId="id"
      nodeLabel={nodeLabel as any}
      nodeCanvasObject={paintNode as any}
      nodeCanvasObjectMode={() => 'replace'}
      linkColor={() => 'rgba(107,114,128,0.4)'}
      linkWidth={nodeCount > 1000 ? 0.5 : 1}
      linkVisibility={linkVisibility as any}
      linkDirectionalArrowLength={nodeCount > 500 ? 0 : 4}
      linkDirectionalArrowRelPos={1}
      linkDirectionalArrowColor={() => 'rgba(107,114,128,0.5)'}
      onNodeClick={(node: any, event: any) => onNodeClick(node as GraphNode, event)}
      backgroundColor="#f9fafb"
      cooldownTicks={0}
    />
  );
});
