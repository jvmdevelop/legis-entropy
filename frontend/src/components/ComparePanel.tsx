import { X, AlertCircle } from 'lucide-react';
import type { GraphNode } from '../types';
import { useCompare } from '../hooks/useCompare';
import { ASSESSMENT_LABEL, ASSESSMENT_STYLE, similarityColor } from '../constants';

interface ComparePanelProps {
  nodeA: GraphNode;
  nodeB: GraphNode;
  onClose: () => void;
}

function DocCard({ node, label }: { node: GraphNode; label: string }) {
  return (
    <div className="flex-1 space-y-2 min-w-0">
      <span className="text-xs font-medium uppercase tracking-wider text-primary">
        Документ {label}
      </span>
      <h3 className="text-sm font-semibold text-foreground leading-tight line-clamp-3">
        {node.title}
      </h3>
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span>{node.ref_count} ссылок</span>
        {node.article_count > 0 && <span>{node.article_count} статей</span>}
        {node.is_amendment && <span className="text-status-amendment">Акт изм.</span>}
      </div>
    </div>
  );
}

export function ComparePanel({ nodeA, nodeB, onClose }: ComparePanelProps) {
  const { result, loading, error } = useCompare(nodeA.id, nodeB.id);

  const simPct = result ? Math.round(result.similarity * 100) : null;
  const simColor = result ? similarityColor(result.similarity) : 'var(--muted-foreground)';
  const circumference = 2 * Math.PI * 24;

  return (
    <div className="absolute left-4 top-4 z-10 w-[600px] max-h-[calc(100vh-6rem)] flex flex-col rounded-lg border border-border bg-card/95 backdrop-blur-sm shadow-xl overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border px-4 py-3 shrink-0">
        <span className="text-sm font-medium text-foreground">+ Сравнение документов</span>
        <button
          onClick={onClose}
          className="h-7 w-7 flex items-center justify-center rounded-md text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* Doc cards */}
        <div className="grid grid-cols-[1fr_auto_1fr] gap-4">
          <DocCard node={nodeA} label="A" />
          <div className="flex items-center justify-center">
            <span className="px-2 py-0.5 rounded-full border border-border bg-secondary text-xs text-muted-foreground">vs</span>
          </div>
          <DocCard node={nodeB} label="Б" />
        </div>

        {loading && (
          <div className="flex items-center justify-center gap-2.5 py-8 text-sm text-muted-foreground">
            <div className="w-4 h-4 border border-border border-t-primary rounded-full animate-spin" />
            Семантический анализ…
          </div>
        )}

        {error && (
          <div className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
            <AlertCircle className="h-4 w-4 shrink-0" />
            Ошибка: {error}
          </div>
        )}

        {result && !loading && (
          <>
            <div className="grid grid-cols-3 gap-3">
              {/* Similarity gauge */}
              <div className="rounded-lg border border-border bg-secondary/50 p-4 flex flex-col items-center justify-center gap-2">
                <svg width="60" height="60" viewBox="0 0 60 60">
                  <circle cx="30" cy="30" r="24" fill="none" stroke="var(--border)" strokeWidth="6" />
                  <circle
                    cx="30" cy="30" r="24"
                    fill="none"
                    stroke={simColor}
                    strokeWidth="6"
                    strokeLinecap="round"
                    strokeDasharray={circumference}
                    strokeDashoffset={circumference * (1 - result.similarity)}
                    transform="rotate(-90 30 30)"
                    style={{ transition: 'stroke-dashoffset 0.6s ease' }}
                  />
                  <text x="30" y="34" textAnchor="middle" fontSize="13" fontWeight="700" fill={simColor}>
                    {simPct}%
                  </text>
                </svg>
                <span className="text-xs text-muted-foreground text-center">Семантическое сходство</span>
              </div>

              {/* Assessment */}
              <div className="rounded-lg border border-border bg-secondary/50 p-4 flex flex-col items-center justify-center gap-2">
                <span className={`inline-flex items-center px-3 py-1.5 rounded-full text-xs font-semibold border ${ASSESSMENT_STYLE[result.assessment]}`}>
                  {ASSESSMENT_LABEL[result.assessment]}
                </span>
                {result.shared_issues > 0 && (
                  <span className="text-xs text-muted-foreground">
                    {result.shared_issues} общих проблем
                  </span>
                )}
              </div>

              {/* Ref diff */}
              <div className="rounded-lg border border-border bg-secondary/50 p-4 flex flex-col items-center justify-center gap-2">
                <span className="text-3xl font-bold font-mono text-foreground">
                  {Math.abs(nodeA.ref_count - nodeB.ref_count)}
                </span>
                <span className="text-xs text-muted-foreground text-center">Разница в ссылках</span>
              </div>
            </div>

            <p className="text-sm text-muted-foreground leading-relaxed px-1">
              {result.explanation}
            </p>

            <div className={`rounded-lg border p-4 space-y-2 ${
              result.llm_ready ? 'border-primary/30 bg-primary/5' : 'border-border bg-secondary/30'
            }`}>
              <span className={`text-xs font-bold uppercase tracking-wider ${
                result.llm_ready ? 'text-primary' : 'text-muted-foreground'
              }`}>
                {result.llm_ready ? '✦ AI-анализ (Qwen2)' : '⏳ AI-анализ — модель загружается'}
              </span>
              <p className={`text-sm leading-relaxed ${
                result.llm_ready ? 'text-foreground' : 'text-muted-foreground italic'
              }`}>
                {result.llm_review}
              </p>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
