import { useState, useEffect, useRef } from 'react';
import type { CompareResult, GraphNode } from '../types';

interface Props {
  nodeA: GraphNode;
  nodeB: GraphNode;
  onClose: () => void;
}

const ASSESSMENT_LABEL: Record<string, string> = {
  duplicate: 'Дублирование',
  highly_related: 'Тесно связаны',
  related: 'Связаны',
  independent: 'Независимы',
};

const ASSESSMENT_STYLE: Record<string, string> = {
  duplicate: 'text-red-600 bg-red-50 border-red-200',
  highly_related: 'text-orange-600 bg-orange-50 border-orange-200',
  related: 'text-yellow-700 bg-yellow-50 border-yellow-200',
  independent: 'text-emerald-600 bg-emerald-50 border-emerald-200',
};

const SIMILARITY_COLOR = (score: number) => {
  if (score >= 0.8) return 'text-red-500';
  if (score >= 0.6) return 'text-orange-500';
  if (score >= 0.4) return 'text-yellow-600';
  return 'text-emerald-600';
};

export function ComparePanel({ nodeA, nodeB, onClose }: Props) {
  const [result, setResult] = useState<CompareResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fetchedPair = useRef<string | null>(null);
  const pairKey = `${nodeA.id}__${nodeB.id}`;

  useEffect(() => {
    if (fetchedPair.current === pairKey) return;
    fetchedPair.current = pairKey;

    let cancelled = false;
    setLoading(true);
    setError(null);
    setResult(null);

    fetch('/api/compare', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id1: nodeA.id, id2: nodeB.id }),
    })
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
      })
      .then(data => { if (!cancelled) setResult(data); })
      .catch(e => { if (!cancelled) setError(String(e?.message ?? e)); })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [pairKey, nodeA.id, nodeB.id]);

  return (
    <div className="absolute inset-x-4 bottom-4 z-30 max-w-xl mx-auto rounded-xl bg-white border border-gray-200 shadow-lg overflow-hidden">
      <div className="h-0.5 bg-gray-900" />

      <div className="p-8">
        <div className="flex items-center justify-between mb-6">
          <span className="text-xs font-semibold text-gray-900">Сравнение документов</span>
          <button
            onClick={onClose}
            className="w-6 h-6 flex items-center justify-center rounded-md text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors"
          >
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
              <path d="M1 1l8 8M9 1L1 9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
            </svg>
          </button>
        </div>

        {/* Documents */}
        <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2 mb-6">
          {[{ node: nodeA, label: 'А' }, { node: nodeB, label: 'Б' }].map(({ node, label }, idx) => (
            idx === 1 ? (
              <>
                <div key="vs" className="w-7 h-7 rounded-full bg-gray-100 border border-gray-200 flex items-center justify-center shrink-0">
                  <span className="text-[9px] font-bold text-gray-400">VS</span>
                </div>
                <div className="rounded-lg bg-gray-50 border border-gray-100 p-4" key={label}>
                  <p className="text-[9px] text-gray-400 uppercase tracking-wider mb-1">Документ {label}</p>
                  <p className="text-[11px] text-gray-700 leading-snug line-clamp-2 font-medium">{node.title}</p>
                  <div className="flex flex-wrap gap-x-2 mt-1.5 text-[9px] text-gray-400">
                    <span>{node.ref_count} ссылок</span>
                    {node.article_count > 0 && <span>• {node.article_count} статей</span>}
                    {node.is_amendment && <span className="text-blue-500">• акт изм.</span>}
                  </div>
                </div>
              </>
            ) : (
              <div className="rounded-lg bg-gray-50 border border-gray-100 p-4" key={label}>
                <p className="text-[9px] text-gray-400 uppercase tracking-wider mb-1">Документ {label}</p>
                <p className="text-[11px] text-gray-700 leading-snug line-clamp-2 font-medium">{node.title}</p>
                <div className="flex flex-wrap gap-x-2 mt-1.5 text-[9px] text-gray-400">
                  <span>{node.ref_count} ссылок</span>
                  {node.article_count > 0 && <span>• {node.article_count} статей</span>}
                  {node.is_amendment && <span className="text-blue-500">• акт изм.</span>}
                </div>
              </div>
            )
          ))}
        </div>

        {loading && (
          <div className="flex items-center justify-center gap-2 py-6 text-xs text-gray-400">
            <div className="w-4 h-4 border border-gray-400 border-t-transparent rounded-full animate-spin" />
            Семантический анализ…
          </div>
        )}

        {error && (
          <div className="rounded-lg border border-red-100 bg-red-50 px-5 py-4 text-[11px] text-red-600">
            Ошибка: {error}
          </div>
        )}

        {result && !loading && (
          <div className="space-y-4">
            <div className="flex items-stretch gap-2">
              <div className="flex-1 rounded-lg bg-gray-50 border border-gray-100 p-4 text-center">
                <div className={`text-2xl font-bold font-mono leading-none ${SIMILARITY_COLOR(result.similarity)}`}>
                  {Math.round(result.similarity * 100)}%
                </div>
                <div className="text-[9px] text-gray-400 mt-1">Семантическое сходство</div>
              </div>

              <div className="flex-1 rounded-lg bg-gray-50 border border-gray-100 p-4 flex flex-col items-center justify-center gap-1.5">
                <span className={`inline-flex items-center px-3 py-1 rounded-full text-[10px] font-semibold border ${ASSESSMENT_STYLE[result.assessment]}`}>
                  {ASSESSMENT_LABEL[result.assessment]}
                </span>
                {result.shared_issues > 0 && (
                  <div className="text-[9px] text-orange-500">{result.shared_issues} общих проблем</div>
                )}
              </div>

              <div className="flex-1 rounded-lg bg-gray-50 border border-gray-100 p-4 text-center">
                <div className="text-xl font-bold font-mono text-gray-700">
                  {Math.abs(nodeA.ref_count - nodeB.ref_count)}
                </div>
                <div className="text-[9px] text-gray-400 mt-1">Разница ссылок</div>
              </div>
            </div>

            <p className="text-[11px] text-gray-500 leading-relaxed">{result.explanation}</p>

            {/* LLM Review */}
            <div className={`rounded-lg border p-5 space-y-2 ${result.llm_ready ? 'border-indigo-100 bg-indigo-50/40' : 'border-gray-100 bg-gray-50'}`}>
              <span className={`text-[9px] font-bold uppercase tracking-wider ${result.llm_ready ? 'text-indigo-600' : 'text-gray-400'}`}>
                {result.llm_ready ? '✦ AI-анализ (Qwen2)' : '⏳ AI-анализ — модель загружается'}
              </span>
              <p className={`text-[11px] leading-relaxed ${result.llm_ready ? 'text-indigo-900' : 'text-gray-400 italic'}`}>
                {result.llm_review}
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
