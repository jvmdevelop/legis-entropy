import { useRef, useCallback, useEffect } from 'react';
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
  active: '#34d399',
  outdated: '#f87171',
  unknown: '#94a3b8',
};

const STATUS_DIM: Record<string, string> = {
  active: '#065f46',
  outdated: '#7f1d1d',
  unknown: '#1e293b',
};

export function Graph({ data, onNodeClick, selectedId, compareId, highlightIds }: GraphProps) {
  const graphRef = useRef<any>(null);
  const hasHighlight = highlightIds && highlightIds.length > 0;

  useEffect(() => {
    if (graphRef.current) setTimeout(() => graphRef.current?.zoomToFit(400, 60), 500);
  }, [data]);

  const paintNode = useCallback(
    (node: GraphNode, ctx: CanvasRenderingContext2D, globalScale: number) => {
      const x = node.x ?? 0;
      const y = node.y ?? 0;
      const r = Math.max(4, Math.sqrt(node.ref_count + 1) * 3);

      const isSelected = node.id === selectedId;
      const isCompare = node.id === compareId;
      const isHit = hasHighlight && highlightIds!.includes(node.id);
      const isDimmed = (hasHighlight && !isHit) || (!isSelected && !isCompare && (selectedId !== null));

      // Glow rings
      if (isSelected) {
        ctx.beginPath();
        ctx.arc(x, y, r + 5, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(139,92,246,0.25)';
        ctx.fill();
      }
      if (isCompare) {
        ctx.beginPath();
        ctx.arc(x, y, r + 5, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(251,146,60,0.25)';
        ctx.fill();
      }
      if (isHit) {
        ctx.beginPath();
        ctx.arc(x, y, r + 4, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(250,204,21,0.2)';
        ctx.fill();
      }

      // Node body
      ctx.beginPath();
      ctx.arc(x, y, r, 0, 2 * Math.PI);
      ctx.fillStyle = isDimmed
        ? STATUS_DIM[node.status] ?? '#1e293b'
        : STATUS_COLOR[node.status] ?? '#94a3b8';
      ctx.fill();

      // Issue ring
      if (node.issue_count > 0 && !isDimmed) {
        ctx.strokeStyle = '#fb923c';
        ctx.lineWidth = 1.5 / globalScale;
        ctx.stroke();
      }

      if (isSelected || isCompare) {
        ctx.strokeStyle = isCompare ? '#fb923c' : '#a78bfa';
        ctx.lineWidth = 2 / globalScale;
        ctx.stroke();
      }

      // Label
      if (r > 6 || isSelected || isHit) {
        const label = node.title.length > 28 ? node.title.slice(0, 28) + '…' : node.title;
        const fontSize = Math.max(10, 12 / globalScale);
        ctx.font = `${fontSize}px system-ui`;
        ctx.fillStyle = isDimmed ? 'rgba(148,163,184,0.3)' : 'rgba(226,232,240,0.9)';
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
      linkColor={() => 'rgba(148,163,184,0.15)'}
      linkWidth={1}
      linkDirectionalArrowLength={4}
      linkDirectionalArrowRelPos={1}
      onNodeClick={(node: any, event: any) => onNodeClick(node as GraphNode, event)}
      backgroundColor="#0f1117"
      cooldownTicks={100}
    />
  );
}
