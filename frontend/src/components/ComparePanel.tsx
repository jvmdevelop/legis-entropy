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
    <div className="flex-1 rounded-lg border border-gray-100 bg-gray-50 p-5 min-w-0">
      <p className="text-[9px] font-bold uppercase tracking-widest text-gray-400 mb-2">
        Документ {label}
      </p>
      <p className="text-[13px] text-gray-800 leading-snug font-semibold line-clamp-3 mb-3">
        {node.title}
      </p>
      <div className="flex flex-wrap gap-2">
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-white border border-gray-200 text-[10px] text-gray-500">
          {node.ref_count} ссылок
        </span>
        {node.article_count > 0 && (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-white border border-gray-200 text-[10px] text-gray-500">
            {node.article_count} статей
          </span>
        )}
        {node.is_amendment && (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-gray-100 border border-gray-300 text-[10px] text-gray-700 font-medium">
            ✎ Акт изм.
          </span>
        )}
      </div>
    </div>
  );
}

export function ComparePanel({ nodeA, nodeB, onClose }: ComparePanelProps) {
  const { result, loading, error } = useCompare(nodeA.id, nodeB.id);

  const simPct = result ? Math.round(result.similarity * 100) : null;
  const simColor = result ? similarityColor(result.similarity) : '#9ca3af';
  const circumference = 2 * Math.PI * 24;

  return (
    <div
      className="absolute z-20 rounded-lg bg-white border border-gray-200 shadow-xl flex flex-col"
      style={{
        left: 16,
        right: 'calc(20rem + 24px)',
        top: 'calc(1rem + 3rem)',
        maxHeight: 'calc(100vh - 5rem)',
      }}
    >
      <div className="h-0.5 bg-gray-800 rounded-t-lg shrink-0" />

      <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 shrink-0">
        <div className="flex items-center gap-2">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M2 7h10M7 2v10" stroke="#111" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
          <span className="text-xs font-bold text-gray-900 tracking-tight">Сравнение документов</span>
        </div>
        <button
          onClick={onClose}
          className="w-7 h-7 flex items-center justify-center rounded-lg text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors"
        >
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
            <path d="M1 1l8 8M9 1L1 9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-5">
        <div className="flex items-stretch gap-3">
          <DocCard node={nodeA} label="А" />
          <div className="flex flex-col items-center justify-center gap-2 shrink-0 w-10">
            <div className="w-px flex-1 bg-gray-200" />
            <div className="w-8 h-8 rounded-full bg-gray-100 border border-gray-200 flex items-center justify-center">
              <span className="text-[9px] font-bold text-gray-400">VS</span>
            </div>
            <div className="w-px flex-1 bg-gray-200" />
          </div>
          <DocCard node={nodeB} label="Б" />
        </div>

        {loading && (
          <div className="flex items-center justify-center gap-2.5 py-8 text-xs text-gray-400">
            <div className="w-4 h-4 border border-gray-300 border-t-gray-600 rounded-full animate-spin" />
            <span>Семантический анализ…</span>
          </div>
        )}

        {error && (
          <div className="rounded-lg border border-gray-300 bg-gray-100 px-5 py-4 text-[11px] text-gray-700">
            Ошибка: {error}
          </div>
        )}

        {result && !loading && (
          <>
            <div className="grid grid-cols-3 gap-3">
              {/* Similarity gauge */}
              <div className="rounded-lg border border-gray-100 bg-gray-50 p-5 flex flex-col items-center justify-center gap-2">
                <svg width="60" height="60" viewBox="0 0 60 60">
                  <circle cx="30" cy="30" r="24" fill="none" stroke="#f3f4f6" strokeWidth="6" />
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
                <span className="text-[10px] text-gray-400 text-center">Семантическое сходство</span>
              </div>

              {/* Assessment */}
              <div className="rounded-lg border border-gray-100 bg-gray-50 p-5 flex flex-col items-center justify-center gap-2">
                <span className={`inline-flex items-center px-4 py-2 rounded-full text-[11px] font-semibold border ${ASSESSMENT_STYLE[result.assessment]}`}>
                  {ASSESSMENT_LABEL[result.assessment]}
                </span>
                {result.shared_issues > 0 && (
                  <span className="text-[10px] text-gray-700 font-medium">
                    {result.shared_issues} общих проблем
                  </span>
                )}
              </div>

              {/* Ref diff */}
              <div className="rounded-lg border border-gray-100 bg-gray-50 p-5 flex flex-col items-center justify-center gap-2">
                <span className="text-3xl font-bold font-mono text-gray-700">
                  {Math.abs(nodeA.ref_count - nodeB.ref_count)}
                </span>
                <span className="text-[10px] text-gray-400 text-center">Разница в ссылках</span>
              </div>
            </div>

            <p className="text-[12px] text-gray-600 leading-relaxed px-1">
              {result.explanation}
            </p>

            <div className={`rounded-lg border p-5 space-y-2 ${result.llm_ready ? 'border-gray-300 bg-gray-100' : 'border-gray-100 bg-gray-50'}`}>
              <span className={`text-[9px] font-bold uppercase tracking-widest ${result.llm_ready ? 'text-gray-700' : 'text-gray-400'}`}>
                {result.llm_ready ? '✦ AI-анализ (Qwen2)' : '⏳ AI-анализ — модель загружается'}
              </span>
              <p className={`text-[12px] leading-relaxed ${result.llm_ready ? 'text-gray-700' : 'text-gray-400 italic'}`}>
                {result.llm_review}
              </p>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
