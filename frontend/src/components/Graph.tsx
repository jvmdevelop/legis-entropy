import { useRef, useCallback, useEffect } from 'react';
// @ts-ignore — d3-force-3d is a transitive dep without typings
import { forceCollide } from 'd3-force-3d';
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
  active: '#10b981',
  outdated: '#ef4444',
  unknown: '#9ca3af',
};

// Dimmed versions for light background
const STATUS_DIM: Record<string, string> = {
  active: '#d1fae5',
  outdated: '#fee2e2',
  unknown: '#f3f4f6',
};

export function Graph({ data, onNodeClick, selectedId, compareId, highlightIds }: GraphProps) {
  const graphRef = useRef<any>(null);
  const hasHighlight = highlightIds && highlightIds.length > 0;

  useEffect(() => {
    const fg = graphRef.current;
    if (!fg) return;
    // Strong repulsion + collision to prevent overlap
    fg.d3Force('charge')?.strength(-400);
    fg.d3Force('collision', forceCollide((node: any) =>
      Math.max(4, Math.sqrt((node.ref_count ?? 0) + 1) * 3) + 12
    ));
    fg.d3Force('link')?.distance(80);
    setTimeout(() => fg.zoomToFit(400, 60), 500);
  }, [data]);

  const paintNode = useCallback(
    (node: GraphNode, ctx: CanvasRenderingContext2D, globalScale: number) => {
      const x = node.x ?? 0;
      const y = node.y ?? 0;
      const r = Math.max(4, Math.sqrt(node.ref_count + 1) * 3);

      const isSelected = node.id === selectedId;
      const isCompare = node.id === compareId;
      const isHit = hasHighlight && highlightIds!.includes(node.id);
      const isDimmed = (hasHighlight && !isHit) || (!isSelected && !isCompare && selectedId !== null);

      // Selection / highlight rings
      if (isSelected) {
        ctx.beginPath();
        ctx.arc(x, y, r + 6, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(99,102,241,0.15)';
        ctx.fill();
      }
      if (isCompare) {
        ctx.beginPath();
        ctx.arc(x, y, r + 6, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(249,115,22,0.15)';
        ctx.fill();
      }
      if (isHit) {
        ctx.beginPath();
        ctx.arc(x, y, r + 4, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(234,179,8,0.15)';
        ctx.fill();
      }

      // Node body — amendment docs are blue-tinted
      ctx.beginPath();
      ctx.arc(x, y, r, 0, 2 * Math.PI);
      if (node.is_amendment && !isDimmed) {
        ctx.fillStyle = '#3b82f6'; // blue for amendment docs
      } else {
        ctx.fillStyle = isDimmed
          ? STATUS_DIM[node.status] ?? '#f3f4f6'
          : STATUS_COLOR[node.status] ?? '#9ca3af';
      }
      ctx.fill();

      // Amendment: dashed outer ring to visually distinguish version-change acts
      if (node.is_amendment && !isDimmed) {
        ctx.save();
        ctx.setLineDash([3 / globalScale, 2 / globalScale]);
        ctx.beginPath();
        ctx.arc(x, y, r + 3, 0, 2 * Math.PI);
        ctx.strokeStyle = 'rgba(59,130,246,0.5)';
        ctx.lineWidth = 1.5 / globalScale;
        ctx.stroke();
        ctx.restore();
      }

      // Issue ring (orange border for non-amendment issues)
      if (node.issue_count > 0 && !isDimmed && !node.is_amendment) {
        ctx.strokeStyle = '#f97316';
        ctx.lineWidth = 1.5 / globalScale;
        ctx.stroke();
      }

      // Selection / compare border
      if (isSelected || isCompare) {
        ctx.strokeStyle = isCompare ? '#f97316' : '#6366f1';
        ctx.lineWidth = 2.5 / globalScale;
        ctx.stroke();
      }

      // Label
      if (r > 6 || isSelected || isHit) {
        const label = node.title.length > 28 ? node.title.slice(0, 28) + '…' : node.title;
        const fontSize = Math.max(10, 12 / globalScale);
        ctx.font = `${fontSize}px Inter, system-ui, sans-serif`;
        ctx.fillStyle = isDimmed ? 'rgba(156,163,175,0.5)' : 'rgba(17,24,39,0.85)';
        ctx.textAlign = 'center';
        ctx.fillText(label, x, y + r + fontSize * 0.9);
      }
    },
    [selectedId, compareId, highlightIds, hasHighlight],
  );

  const nodeLabel = useCallback((node: GraphNode) => {
    const issues = node.issue_count > 0 ? ` | ${node.issue_count} проблем` : '';
    return `${node.title}${issues}`;
  }, []);

  return (
    <ForceGraph2D
      ref={graphRef}
      graphData={data as any}
      nodeId="id"
      nodeLabel={nodeLabel as any}
      nodeCanvasObject={paintNode as any}
      nodeCanvasObjectMode={() => 'replace'}
      linkColor={() => 'rgba(107,114,128,0.2)'}
      linkWidth={1}
      linkDirectionalArrowLength={4}
      linkDirectionalArrowRelPos={1}
      linkDirectionalArrowColor={() => 'rgba(107,114,128,0.4)'}
      onNodeClick={(node: any, event: any) => onNodeClick(node as GraphNode, event)}
      backgroundColor="#f9fafb"
      cooldownTicks={100}
    />
  );
}
