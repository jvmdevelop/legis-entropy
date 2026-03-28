import { useState } from 'react';
import type { CorpusStats, GraphNode, Issue } from '../types';
import { Sidebar } from './Sidebar';

interface InspectorPanelProps {
  node: GraphNode | null;
  issues: Issue[];
  stats: CorpusStats | null;
  onNodeClose: () => void;
  onCompareSelect: (node: GraphNode) => void;
  compareNode: GraphNode | null;
}

export function InspectorPanel({
  node,
  issues,
  stats,
  onNodeClose,
  onCompareSelect,
  compareNode,
}: InspectorPanelProps) {
  const [open, setOpen] = useState(false);

  return (
    <div className="absolute top-4 right-4 z-20">
      <div className="flex items-center h-8 rounded-lg bg-white/90 backdrop-blur-md border border-white/20 elevation-2 overflow-hidden">
        <button
          onClick={() => setOpen(o => !o)}
          className="h-full flex items-center gap-2 text-[11px] font-medium text-gray-600 hover:text-gray-900 hover:bg-gradient-to-r hover:from-gray-50 hover:to-slate-50 transition-all duration-300 relative group ripple"
          style={{ paddingLeft: '1.5rem', paddingRight: '1.5rem' }}
        >
          <svg
            width="13"
            height="13"
            viewBox="0 0 12 12"
            fill="none"
            className="transition-transform duration-300"
            style={{ transform: open ? 'rotate(180deg)' : 'none' }}
          >
            <rect x="1" y="1" width="4" height="10" rx="1" stroke="currentColor" strokeWidth="1.3" />
            <rect x="7" y="1" width="4" height="4" rx="1" stroke="currentColor" strokeWidth="1.3" />
            <rect x="7" y="7" width="4" height="4" rx="1" stroke="currentColor" strokeWidth="1.3" />
          </svg>
          <span>Инспектор</span>
          {node && <span className="w-1.5 h-1.5 rounded-full bg-gray-800 ml-0.5" />}
        </button>
      </div>

      {open && (
        <div className="absolute top-12 right-0 w-80 max-h-[calc(100vh-5rem)] overflow-y-auto rounded-lg bg-white/95 backdrop-blur-md border border-white/20 elevation-3">
          <Sidebar
            node={node}
            issues={issues}
            stats={stats}
            onClose={onNodeClose}
            onCompareSelect={onCompareSelect}
            compareNode={compareNode}
          />
        </div>
      )}
    </div>
  );
}
