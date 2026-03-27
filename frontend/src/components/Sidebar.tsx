import type { CorpusStats, GraphNode, Issue } from '../types';

interface SidebarProps {
  node: GraphNode | null;
  issues: Issue[];
  stats: CorpusStats | null;
  onClose: () => void;
  onCompareSelect: (node: GraphNode) => void;
  compareNode: GraphNode | null;
}

const STATUS_LABEL: Record<string, string> = {
  active: 'Действующий',
  outdated: 'Утратил силу',
  unknown: 'Неизвестен',
};

const STATUS_COLOR: Record<string, string> = {
  active: 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30',
  outdated: 'bg-red-500/20 text-red-300 border border-red-500/30',
  unknown: 'bg-slate-500/20 text-slate-300 border border-slate-500/30',
};

const ISSUE_KIND_LABEL: Record<string, string> = {
  duplication: 'Дублирование',
  contradiction: 'Противоречие',
  outdated_reference: 'Устаревшая ссылка',
  circular_reference: 'Цикл',
};

const SEVERITY_COLOR: Record<string, string> = {
  high: 'text-red-400',
  medium: 'text-orange-400',
  low: 'text-yellow-400',
};

export function Sidebar({ node, issues, stats, onClose, onCompareSelect, compareNode }: SidebarProps) {
  const nodeIssues = node
    ? issues.filter(i => i.document_ids.includes(node.id))
    : [];

  return (
    <div className="absolute top-0 right-0 h-full w-80 bg-slate-900/95 backdrop-blur border-l border-slate-700 flex flex-col z-10">
      {/* Header */}
      <div className="px-4 py-3 border-b border-slate-700 flex items-center justify-between">
        <span className="text-sm font-semibold text-slate-200">legis-entropy</span>
        {stats && (
          <div className="flex items-center gap-3 text-xs text-slate-400">
            <span>{stats.total_documents} НПА</span>
            <span>{stats.total_links} связей</span>
          </div>
        )}
      </div>

      <div className="flex-1 overflow-y-auto">
        {/* Node details */}
        {node ? (
          <div className="p-4 space-y-4">
            <div className="flex items-start justify-between gap-2">
              <h2 className="text-sm font-medium text-slate-100 leading-snug">{node.title}</h2>
              <button onClick={onClose} className="shrink-0 text-slate-400 hover:text-slate-200 text-lg leading-none">×</button>
            </div>

            <div className="space-y-2 text-xs">
              <div className="flex items-center gap-2">
                <span className="text-slate-400 w-20 shrink-0">Статус</span>
                <span className={`px-2 py-0.5 rounded text-xs font-medium ${STATUS_COLOR[node.status]}`}>
                  {STATUS_LABEL[node.status]}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-slate-400 w-20 shrink-0">ID</span>
                <code className="text-slate-300 bg-slate-800 px-1.5 py-0.5 rounded font-mono">{node.id}</code>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-slate-400 w-20 shrink-0">Ссылки</span>
                <span className="text-slate-300">{node.ref_count} документов</span>
              </div>
            </div>

            {/* Node issues */}
            {nodeIssues.length > 0 && (
              <div className="space-y-1.5">
                <p className="text-xs text-slate-400 font-medium">Проблемы ({nodeIssues.length})</p>
                {nodeIssues.map((issue, i) => (
                  <div key={i} className="bg-slate-800 rounded p-2 text-xs space-y-0.5">
                    <div className="flex items-center gap-2">
                      <span className={`font-medium ${SEVERITY_COLOR[issue.severity]}`}>
                        {ISSUE_KIND_LABEL[issue.kind]}
                      </span>
                    </div>
                    <p className="text-slate-400 leading-snug">{issue.explanation}</p>
                  </div>
                ))}
              </div>
            )}

            <div className="flex gap-2">
              <a
                href={node.url}
                target="_blank"
                rel="noopener noreferrer"
                className="flex-1 text-center text-xs py-2 px-3 rounded bg-violet-600 hover:bg-violet-500 text-white transition-colors"
              >
                Открыть →
              </a>
              <button
                onClick={() => onCompareSelect(node)}
                className={`text-xs py-2 px-3 rounded border transition-colors ${
                  compareNode?.id === node.id
                    ? 'bg-violet-500/20 border-violet-500 text-violet-300'
                    : 'border-slate-600 text-slate-400 hover:border-slate-500'
                }`}
                title="Выбрать для сравнения"
              >
                {compareNode?.id === node.id ? 'Выбран' : 'Сравнить'}
              </button>
            </div>
          </div>
        ) : (
          <div className="p-4 text-slate-400 text-sm text-center mt-6 space-y-2">
            <div className="text-2xl">⚖️</div>
            <p className="text-xs">Нажмите на узел, чтобы увидеть детали НПА</p>
            <p className="text-xs text-slate-500">Ctrl+клик — выбрать два для сравнения</p>
          </div>
        )}

        {/* Corpus stats */}
        {stats && (
          <div className="px-4 pb-4 space-y-3 border-t border-slate-700/50 pt-4">
            <p className="text-xs text-slate-400 font-medium uppercase tracking-wider">Состояние корпуса</p>
            <div className="grid grid-cols-2 gap-2">
              {[
                { label: 'Действующих', value: stats.active_count, color: 'text-emerald-400' },
                { label: 'Утративших', value: stats.outdated_count, color: 'text-red-400' },
                { label: 'С проблемами', value: stats.with_issues, color: 'text-orange-400' },
                { label: 'Ср. ссылок', value: stats.avg_ref_count.toFixed(1), color: 'text-slate-300' },
              ].map(({ label, value, color }) => (
                <div key={label} className="bg-slate-800 rounded p-2">
                  <div className={`text-sm font-mono font-semibold ${color}`}>{value}</div>
                  <div className="text-xs text-slate-500">{label}</div>
                </div>
              ))}
            </div>

            {stats.most_problematic.length > 0 && (
              <div className="space-y-1">
                <p className="text-xs text-slate-500">Наиболее проблемные</p>
                {stats.most_problematic.slice(0, 3).map(n => (
                  <div key={n.id} className="flex items-center justify-between text-xs">
                    <span className="text-slate-400 truncate flex-1 mr-2">{n.title}</span>
                    <span className="text-orange-400 font-mono shrink-0">{n.issue_count}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Legend */}
      <div className="px-4 py-3 border-t border-slate-700 space-y-2">
        <p className="text-xs text-slate-400 font-medium uppercase tracking-wider">Легенда</p>
        <div className="space-y-1">
          {[
            { color: 'bg-emerald-400', label: 'Действующий' },
            { color: 'bg-red-400', label: 'Утратил силу' },
            { color: 'bg-slate-400', label: 'Статус неизвестен' },
          ].map(({ color, label }) => (
            <div key={label} className="flex items-center gap-2 text-xs text-slate-300">
              <span className={`w-2.5 h-2.5 rounded-full ${color}`} />
              {label}
            </div>
          ))}
        </div>
        <p className="text-xs text-slate-500 pt-1">Размер узла — кол-во ссылок</p>
      </div>
    </div>
  );
}
