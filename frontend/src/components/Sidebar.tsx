import { useState } from 'react';
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

const STATUS_DOT: Record<string, string> = {
  active: 'bg-gray-800',
  outdated: 'bg-gray-600',
  unknown: 'bg-gray-400',
};

const STATUS_STYLE: Record<string, string> = {
  active: 'text-gray-800 bg-gray-100 border-gray-300',
  outdated: 'text-gray-700 bg-gray-100 border-gray-300',
  unknown: 'text-gray-500 bg-gray-100 border-gray-200',
};

const ISSUE_KIND_LABEL: Record<string, string> = {
  duplication: 'Дублирование',
  contradiction: 'Противоречие',
  outdated_reference: 'Устаревшая ссылка',
  circular_reference: 'Циклические ссылки',
  amendment: 'Акт внесения изменений',
};

const ISSUE_KIND_ICON: Record<string, string> = {
  duplication: '⊙',
  contradiction: '⚡',
  outdated_reference: '🔗',
  circular_reference: '↺',
  amendment: '✎',
};

const SEVERITY_STYLE: Record<string, string> = {
  high: 'text-gray-800 bg-gray-100 border-gray-300',
  medium: 'text-gray-700 bg-gray-100 border-gray-300',
  low: 'text-gray-600 bg-gray-100 border-gray-200',
};

const SEVERITY_LABEL: Record<string, string> = {
  high: 'Высокий',
  medium: 'Средний',
  low: 'Низкий',
};


const EXPLANATION_LIMIT = 160;

function IssueCard({ issue }: { issue: Issue; index: number }) {
  const [expanded, setExpanded] = useState(false);
  const long = issue.explanation.length > EXPLANATION_LIMIT;
  const text = !expanded && long
    ? issue.explanation.slice(0, EXPLANATION_LIMIT) + '…'
    : issue.explanation;

  return (
    <div className={`rounded-lg border p-6 space-y-4 transition-all duration-300 hover:elevation-3 ${
      issue.kind === 'amendment' ? 'border-gray-300 bg-gradient-to-br from-gray-50 to-slate-50' : 'border-gray-200 bg-white'
    } elevation-2`}>
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3 min-w-0 flex-1">
          <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-gray-100 to-slate-100 border border-gray-300 flex items-center justify-center shrink-0 mt-0.5 elevation-1">
            <span className="text-base leading-none text-gray-700">{ISSUE_KIND_ICON[issue.kind] ?? '⚠'}</span>
          </div>
          <div className="min-w-0 flex-1">
            <h3 className="text-[12px] font-bold text-gray-900 leading-tight mb-1">
              {ISSUE_KIND_LABEL[issue.kind] ?? issue.kind}
            </h3>
          </div>
        </div>
        <span className={`text-[9px] font-bold px-3 py-1.5 rounded-full border shrink-0 elevation-1 ${SEVERITY_STYLE[issue.severity]}`}>
          {SEVERITY_LABEL[issue.severity]}
        </span>
      </div>
      
      <div className="pl-13 space-y-3">
        <p className="text-[11px] text-gray-600 leading-relaxed">{text}</p>
        
        <div className="flex items-center justify-between pt-3 border-t border-gray-100">
          {long && (
            <button
              onClick={() => setExpanded(e => !e)}
              className="text-[9px] font-medium text-gray-600 hover:text-gray-800 transition-colors flex items-center gap-1.5 ripple"
            >
              <span className="text-xs">{expanded ? '↑' : '↓'}</span>
              {expanded ? 'Свернуть' : 'Подробнее'}
            </button>
          )}
          <div className="text-[9px] text-gray-400 ml-auto">
            {issue.document_ids.length} {issue.document_ids.length === 1 ? 'документ' : issue.document_ids.length < 5 ? 'документа' : 'документов'}
          </div>
        </div>
      </div>
    </div>
  );
}

export function Sidebar({ node, issues, stats, onClose, onCompareSelect, compareNode }: SidebarProps) {
  const nodeIssues = node
    ? issues.filter(i => i.document_ids.includes(node.id))
    : [];

  return (
    <div className="flex flex-col">

      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-2 h-4 rounded-full bg-gradient-to-b from-gray-700 to-gray-800 elevation-1" />
          <span className="text-xs font-bold text-gray-900 tracking-tight">Инспектор</span>
        </div>
        {stats && (
          <span className="text-[10px] text-gray-400 tabular-nums">
            {stats.total_documents} НПА
          </span>
        )}
      </div>

      <div>
        {node ? (
          <div className="p-8 space-y-6">
            {/* Title + close */}
            <div className="flex items-start justify-between gap-2">
              <h2 className="text-sm font-medium text-gray-900 leading-snug">{node.title}</h2>
              <button
                onClick={onClose}
                className="shrink-0 w-5 h-5 flex items-center justify-center rounded-md text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors"
              >
                <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                  <path d="M1 1l8 8M9 1L1 9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
              </button>
            </div>

            {/* Badges row */}
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className={`inline-flex items-center gap-1.5 px-4 py-1 rounded-full text-[10px] font-medium border ${STATUS_STYLE[node.status]}`}>
                <span className={`w-1.5 h-1.5 rounded-full ${STATUS_DOT[node.status]}`} />
                {STATUS_LABEL[node.status]}
              </span>
              {node.is_amendment && (
                <span className="inline-flex items-center gap-1 px-4 py-1 rounded-full text-[10px] font-medium text-gray-700 bg-gray-100 border border-gray-300">
                  ✎ Акт изменений
                </span>
              )}
              {node.issue_count > 0 && (
                <span className="inline-flex items-center gap-1 px-4 py-1 rounded-full text-[10px] font-medium text-gray-700 bg-gray-100 border border-gray-300">
                  ⚠ {node.issue_count} {node.issue_count === 1 ? 'проблема' : 'проблем'}
                </span>
              )}
            </div>

            {/* Metadata table */}
            <div className="rounded-lg border border-gray-100 divide-y divide-gray-100 bg-gray-50/50">
              <div className="flex items-center justify-between px-6 py-4">
                <span className="text-[10px] text-gray-400">ID документа</span>
                <code className="text-[10px] text-gray-600 font-mono bg-gray-100 px-3 py-1 rounded">{node.id}</code>
              </div>
              <div className="flex items-center justify-between px-6 py-4">
                <span className="text-[10px] text-gray-400">Ссылки на НПА</span>
                <span className="text-[10px] text-gray-700 font-medium">{node.ref_count} <span className="font-normal text-gray-400">документов</span></span>
              </div>
              {node.article_count > 0 && (
                <div className="flex items-center justify-between px-6 py-4">
                  <span className="text-[10px] text-gray-400">Ключевые нормы</span>
                  <span className="text-[10px] text-gray-700 font-medium">{node.article_count} <span className="font-normal text-gray-400">статей</span></span>
                </div>
              )}
              <div className="flex items-center justify-between px-6 py-4">
                <span className="text-[10px] text-gray-400">Тип документа</span>
                <span className="text-[10px] text-gray-600">
                  {node.is_amendment ? 'Акт внесения изменений' : node.article_count > 10 ? 'Кодекс / закон' : 'НПА'}
                </span>
              </div>
            </div>

            {/* Issues */}
            {nodeIssues.length > 0 && (
              <div className="space-y-4">
                <div className="flex items-center gap-2 mb-4">
                  <div className="w-1.5 h-4 bg-gradient-to-b from-gray-500 to-gray-600 rounded-full elevation-1" />
                  <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">
                    Почему помечен ({nodeIssues.length})
                  </p>
                </div>
                {nodeIssues.map((issue, i) => (
                  <IssueCard key={i} issue={issue} index={i} />
                ))}
              </div>
            )}

            {/* Actions */}
            <div className="flex gap-2 pt-2">
              <a
                href={node.url}
                target="_blank"
                rel="noopener noreferrer"
                className="flex-1 flex items-center justify-center gap-1.5 text-[11px] font-medium py-4 px-6 rounded-lg bg-gray-900 hover:bg-gray-700 text-white transition-colors"
              >
                Открыть
                <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                  <path d="M2 8L8 2M8 2H4M8 2v4" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </a>
              <button
                onClick={() => onCompareSelect(node)}
                className={`flex items-center gap-1.5 text-[11px] font-medium py-4 px-6 rounded-lg border transition-all ${
                  compareNode?.id === node.id
                    ? 'bg-gray-900 border-gray-900 text-white'
                    : 'border-gray-200 text-gray-600 hover:border-gray-300 hover:text-gray-900'
                }`}
              >
                {compareNode?.id === node.id ? (
                  <>
                    <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                      <path d="M1.5 5l2.5 2.5 4.5-5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                    Выбран
                  </>
                ) : 'Сравнить'}
              </button>
            </div>
          </div>
        ) : (
          /* Issues overview */
          <div className="p-8 space-y-6">
            <div>
              <p className="text-xs font-medium text-gray-500 mb-0.5">Выберите узел на графе</p>
              <p className="text-[10px] text-gray-400">Ctrl+клик — сравнить два документа</p>
            </div>

            {issues.length > 0 ? (
              <div className="space-y-4">
                <div className="flex items-center gap-2 mb-4">
                  <div className="w-1.5 h-4 bg-gradient-to-b from-gray-500 to-gray-600 rounded-full elevation-1" />
                  <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">
                    Обнаруженные проблемы ({issues.length})
                  </p>
                </div>
                {issues.map((issue, i) => (
                  <IssueCard key={i} issue={issue} index={i} />
                ))}
              </div>
            ) : (
              <div className="rounded-lg border border-gray-300 bg-gradient-to-br from-gray-50 to-slate-50 p-6 elevation-2">
                <p className="text-[11px] font-bold text-gray-800">Проблем не обнаружено</p>
                <p className="text-[10px] text-gray-600 mt-1">Корпус документов согласован</p>
              </div>
            )}
          </div>
        )}

        {/* Corpus stats */}
        {stats && (
          <div className="px-8 pb-8 space-y-4 border-t border-gray-100 pt-8">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-1.5 h-4 bg-gradient-to-b from-gray-500 to-gray-600 rounded-full elevation-1" />
              <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Состояние корпуса</p>
            </div>

            <div className="grid grid-cols-2 gap-3">
              {[
                { label: 'Действующих', value: stats.active_count, colorClass: 'text-gray-800', accentColor: '#000000' },
                { label: 'Утративших', value: stats.outdated_count, colorClass: 'text-gray-600', accentColor: '#666666' },
                { label: 'С проблемами', value: stats.with_issues, colorClass: 'text-gray-700', accentColor: '#333333' },
                { label: 'Ср. ссылок', value: stats.avg_ref_count.toFixed(1), colorClass: 'text-gray-700', accentColor: '#999999' },
              ].map(({ label, value, colorClass, accentColor }) => (
                <div key={label} className="rounded-lg border border-gray-100 bg-gray-50/50 p-4 overflow-hidden relative">
                  <div className="absolute top-0 left-0 right-0 h-0.5" style={{ background: accentColor }} />
                  <div className={`text-lg font-bold font-mono leading-none ${colorClass}`}>{value}</div>
                  <div className="text-[10px] text-gray-400 mt-1">{label}</div>
                </div>
              ))}
            </div>

            {stats.most_problematic.length > 0 && (
              <div className="space-y-1">
                <p className="text-[10px] text-gray-400">Наиболее проблемные</p>
                {stats.most_problematic.slice(0, 3).map(n => (
                  <div key={n.id} className="flex items-center justify-between gap-2 py-3 border-b border-gray-100 last:border-0">
                    <span className="text-[10px] text-gray-600 truncate flex-1">{n.title}</span>
                    <span className="text-[10px] text-gray-600 font-semibold font-mono shrink-0">{n.issue_count}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

    </div>
  );
}
