import type { GraphNode } from '../types';

interface LegendPanelProps {
  selectedNode: GraphNode | null;
  compareNode: GraphNode | null;
}

const LEGEND_ITEMS = [
  { color: '#000000', label: 'Действующий' },
  { color: '#666666', label: 'Утратил силу' },
  { color: '#999999', label: 'Неизвестен' },
  { color: '#333333', label: 'Акт изменений' },
];

export function LegendPanel({ selectedNode, compareNode }: LegendPanelProps) {
  return (
    <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-10 pointer-events-none">
      <div className="flex items-center gap-4 px-6 py-3.5 rounded-lg bg-white/90 backdrop-blur-md border border-white/20 elevation-2 pointer-events-auto">
        {LEGEND_ITEMS.map(({ color, label }) => (
          <div key={label} className="flex items-center gap-2.5 group">
            <span className="w-3 h-3 rounded-full shrink-0 ring-2 ring-white group-hover:ring-gray-200 transition-all duration-300 elevation-1" style={{ background: color }} />
            <span className="text-[10px] text-gray-600 whitespace-nowrap group-hover:text-gray-900 transition-colors duration-300">{label}</span>
          </div>
        ))}
        <div className="w-px h-5 bg-gradient-to-b from-transparent via-gray-300 to-transparent" />
        <span className="text-[10px] text-gray-500 whitespace-nowrap">Размер — ссылки</span>
        {selectedNode && !compareNode && (
          <>
            <div className="w-px h-5 bg-gradient-to-b from-transparent via-gray-300 to-transparent" />
            <span className="text-[10px] text-gray-500 whitespace-nowrap">Ctrl+клик — сравнить</span>
          </>
        )}
      </div>
    </div>
  );
}
