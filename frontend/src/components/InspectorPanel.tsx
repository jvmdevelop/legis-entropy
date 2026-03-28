import type { CorpusStats, GraphNode, Issue } from '../types';
import { Sidebar } from './Sidebar';

interface InspectorPanelProps {
  node: GraphNode | null;
  issues: Issue[];
  stats: CorpusStats | null;
  onNodeClose: () => void;
  onCompareSelect: (node: GraphNode) => void;
  compareNode: GraphNode | null;
  onClose: () => void;
}

export function InspectorPanel({
  node,
  issues,
  stats,
  onNodeClose,
  onCompareSelect,
  compareNode,
  onClose,
}: InspectorPanelProps) {
  return (
    <aside className="w-[380px] shrink-0 border-l border-border bg-card flex flex-col overflow-hidden">
      <Sidebar
        node={node}
        issues={issues}
        stats={stats}
        onClose={onNodeClose}
        onCompareSelect={onCompareSelect}
        compareNode={compareNode}
        onPanelClose={onClose}
      />
    </aside>
  );
}
