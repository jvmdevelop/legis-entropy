import type { GraphNode } from '../types';

interface LegendPanelProps {
  selectedNode: GraphNode | null;
  compareNode: GraphNode | null;
}

const LEGEND_ITEMS = [
  { colorClass: 'bg-status-active', label: 'Действующий' },
  { colorClass: 'bg-status-inactive', label: 'Утратил силу' },
  { colorClass: 'bg-status-unknown', label: 'Неизвестен' },
  { colorClass: 'bg-status-amendment', label: 'Акт изменений' },
];

export function LegendPanel({ selectedNode, compareNode }: LegendPanelProps) {
  return (
    <div className="absolute bottom-6 left-1/2 -translate-x-1/2 z-10 pointer-events-none">
      <div className="flex items-center gap-1 rounded-full border border-border bg-card/90 backdrop-blur-sm px-4 py-2 shadow-lg pointer-events-auto">
        {LEGEND_ITEMS.map((item, index) => (
          <div key={item.label} className="flex items-center gap-2">
            <div className="flex items-center gap-2 px-2 py-1 rounded-full hover:bg-secondary/50 transition-colors">
              <span className={`h-3 w-3 rounded-full shrink-0 ${item.colorClass}`} />
              <span className="text-xs text-foreground">{item.label}</span>
            </div>
            {index < LEGEND_ITEMS.length - 1 && (
              <div className="h-4 w-px bg-border" />
            )}
          </div>
        ))}
        <div className="h-4 w-px bg-border mx-1" />
        <span className="text-xs text-muted-foreground px-2">Размер — ссылки</span>
        {selectedNode && !compareNode && (
          <>
            <div className="h-4 w-px bg-border mx-1" />
            <span className="text-xs text-muted-foreground px-2">Ctrl+клик — сравнить</span>
          </>
        )}
      </div>
    </div>
  );
}
