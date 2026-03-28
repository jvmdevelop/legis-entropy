import { useState } from 'react';
import { X, ExternalLink, GitCompare, AlertTriangle, ChevronDown, ChevronUp } from 'lucide-react';
import type { CorpusStats, GraphNode, Issue } from '../types';
import {
  ISSUE_KIND_ICON, ISSUE_KIND_LABEL,
  SEVERITY_LABEL, SEVERITY_STYLE,
  STATUS_DOT, STATUS_LABEL, STATUS_STYLE,
} from '../constants';

interface SidebarProps {
  node: GraphNode | null;
  issues: Issue[];
  stats: CorpusStats | null;
  onClose: () => void;
  onCompareSelect: (node: GraphNode) => void;
  compareNode: GraphNode | null;
  onPanelClose: () => void;
}

const EXPLANATION_LIMIT = 160;

function IssueCard({ issue }: { issue: Issue }) {
  const [expanded, setExpanded] = useState(false);
  const long = issue.explanation.length > EXPLANATION_LIMIT;
  const text = !expanded && long
    ? issue.explanation.slice(0, EXPLANATION_LIMIT) + '…'
    : issue.explanation;

  return (
    <div className="rounded-lg border border-border bg-secondary/50 p-4 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0 flex-1">
          <div className="rounded-full bg-muted p-2 shrink-0">
            <span className="text-sm leading-none">{ISSUE_KIND_ICON[issue.kind] ?? '⚠'}</span>
          </div>
          <div className="min-w-0">
            <h4 className="text-sm font-medium text-foreground">
              {ISSUE_KIND_LABEL[issue.kind] ?? issue.kind}
            </h4>
            <span className={`inline-flex items-center mt-1 px-2 py-0.5 rounded-full text-xs border ${SEVERITY_STYLE[issue.severity]}`}>
              {SEVERITY_LABEL[issue.severity]}
            </span>
          </div>
        </div>
      </div>

      <p className="text-sm text-muted-foreground leading-relaxed">{text}</p>

      <div className="flex items-center justify-between">
        {long && (
          <button
            onClick={() => setExpanded(e => !e)}
            className="flex items-center gap-1 text-xs text-primary hover:text-primary/80 transition-colors"
          >
            {expanded ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
            {expanded ? 'Свернуть' : 'Подробнее'}
          </button>
        )}
        <span className="text-xs text-muted-foreground ml-auto">
          {issue.document_ids.length}{' '}
          {issue.document_ids.length === 1 ? 'документ' : issue.document_ids.length < 5 ? 'документа' : 'документов'}
        </span>
      </div>
    </div>
  );
}

function SectionHeader({ label }: { label: string }) {
  return (
    <h3 className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-3">
      {label}
    </h3>
  );
}

export function Sidebar({ node, issues, stats, onClose, onCompareSelect, compareNode, onPanelClose }: SidebarProps) {
  const nodeIssues = node ? issues.filter(i => i.document_ids.includes(node.id)) : [];

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border px-4 py-3 shrink-0">
        <div className="flex items-center gap-2">
          <div className={`h-3 w-3 rounded-full ${node ? 'bg-primary' : 'bg-muted'}`} />
          <span className="font-medium text-foreground text-sm">Инспектор</span>
          {stats && (
            <span className="text-xs text-muted-foreground tabular-nums">— {stats.total_documents} НПА</span>
          )}
        </div>
        <button
          onClick={onPanelClose}
          className="h-7 w-7 flex items-center justify-center rounded-md text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto">
        {node ? (
          <div className="p-4 space-y-5">
            {/* Title */}
            <div className="flex items-start justify-between gap-2">
              <h2 className="text-base font-semibold text-foreground leading-snug">{node.title}</h2>
              <button
                onClick={onClose}
                className="shrink-0 h-6 w-6 flex items-center justify-center rounded-md text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>

            {/* Badges */}
            <div className="flex items-center gap-2 flex-wrap">
              <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs border ${STATUS_STYLE[node.status]}`}>
                <span className={`w-1.5 h-1.5 rounded-full ${STATUS_DOT[node.status]}`} />
                {STATUS_LABEL[node.status]}
              </span>
              {node.is_amendment && (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs bg-status-amendment/20 text-status-amendment border border-status-amendment/30">
                  ✎ Акт изменений
                </span>
              )}
              {node.issue_count > 0 && (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs bg-destructive/20 text-destructive border border-destructive/30">
                  <AlertTriangle className="h-3 w-3" />
                  {node.issue_count} {node.issue_count === 1 ? 'проблема' : 'проблем'}
                </span>
              )}
            </div>

            {/* Separator */}
            <div className="border-t border-border" />

            {/* Metadata */}
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span className="text-muted-foreground text-xs">ID документа</span>
                <p className="font-mono text-foreground text-xs mt-0.5">{node.id}</p>
              </div>
              <div>
                <span className="text-muted-foreground text-xs">Ссылки на НПА</span>
                <p className="font-medium text-foreground text-xs mt-0.5">{node.ref_count} документов</p>
              </div>
              {node.article_count > 0 && (
                <div>
                  <span className="text-muted-foreground text-xs">Ключевые нормы</span>
                  <p className="font-medium text-foreground text-xs mt-0.5">{node.article_count} статей</p>
                </div>
              )}
              <div>
                <span className="text-muted-foreground text-xs">Тип документа</span>
                <p className="text-foreground text-xs mt-0.5">
                  {node.is_amendment ? 'Акт изменений' : node.article_count > 10 ? 'Кодекс / закон' : 'НПА'}
                </p>
              </div>
            </div>

            {/* Issues */}
            {nodeIssues.length > 0 && (
              <div className="space-y-3">
                <div className="border-t border-border pt-4">
                  <SectionHeader label={`Почему помечен (${nodeIssues.length})`} />
                </div>
                {nodeIssues.map((issue, i) => <IssueCard key={i} issue={issue} />)}
              </div>
            )}
          </div>
        ) : (
          <div className="p-4 space-y-5">
            <div>
              <p className="text-sm font-medium text-foreground mb-1">Выберите документ на графе</p>
              <p className="text-xs text-muted-foreground">Ctrl+клик — сравнить два документа</p>
            </div>

            {issues.length > 0 ? (
              <div className="space-y-3">
                <div className="border-t border-border pt-4">
                  <SectionHeader label={`Обнаруженные проблемы (${issues.length})`} />
                </div>
                {issues.map((issue, i) => <IssueCard key={i} issue={issue} />)}
              </div>
            ) : (
              <div className="rounded-lg border border-border bg-secondary/50 p-4">
                <p className="text-sm font-medium text-foreground">Проблем не обнаружено</p>
                <p className="text-xs text-muted-foreground mt-1">Корпус документов согласован</p>
              </div>
            )}
          </div>
        )}

        {/* Corpus stats */}
        {stats && (
          <div className="px-4 pb-4 space-y-3 border-t border-border pt-4">
            <SectionHeader label="Состояние корпуса" />

            <div className="grid grid-cols-2 gap-2">
              {[
                { label: 'Действующих', value: stats.active_count, color: 'var(--status-active)' },
                { label: 'Утративших', value: stats.outdated_count, color: 'var(--status-inactive)' },
                { label: 'С проблемами', value: stats.with_issues, color: 'var(--destructive)' },
                { label: 'Ср. ссылок', value: stats.avg_ref_count.toFixed(1), color: 'var(--primary)' },
              ].map(({ label, value, color }) => (
                <div key={label} className="rounded-lg border border-border bg-secondary/50 p-3 overflow-hidden relative">
                  <div className="absolute top-0 left-0 right-0 h-0.5" style={{ background: color }} />
                  <div className="text-lg font-bold font-mono text-foreground leading-none">{value}</div>
                  <div className="text-xs text-muted-foreground mt-1">{label}</div>
                </div>
              ))}
            </div>

            {stats.most_problematic.length > 0 && (
              <div className="space-y-1">
                <p className="text-xs text-muted-foreground mb-2">Наиболее проблемные</p>
                {stats.most_problematic.slice(0, 3).map(n => (
                  <div key={n.id} className="flex items-center justify-between gap-2 py-2 border-b border-border last:border-0">
                    <span className="text-xs text-muted-foreground truncate flex-1">{n.title}</span>
                    <span className="text-xs text-destructive font-semibold font-mono shrink-0">{n.issue_count}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Actions footer — only shown when a node is selected */}
      {node && (
        <div className="border-t border-border p-4 shrink-0">
          <div className="flex gap-2">
            <a
              href={node.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex-1 flex items-center justify-center gap-2 text-sm font-medium py-2 px-4 rounded-md bg-primary hover:bg-primary/90 text-primary-foreground transition-colors"
            >
              Открыть
              <ExternalLink className="h-3.5 w-3.5" />
            </a>
            <button
              onClick={() => onCompareSelect(node)}
              className={`flex-1 flex items-center justify-center gap-2 text-sm font-medium py-2 px-4 rounded-md border transition-colors ${
                compareNode?.id === node.id
                  ? 'bg-primary border-primary text-primary-foreground'
                  : 'border-border text-muted-foreground hover:text-foreground hover:bg-secondary'
              }`}
            >
              <GitCompare className="h-3.5 w-3.5" />
              {compareNode?.id === node.id ? 'Выбран' : 'Сравнить'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
