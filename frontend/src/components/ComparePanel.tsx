import { useState } from 'react';
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
  const [fetched, setFetched] = useState(false);

  const runCompare = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/compare', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id1: nodeA.id, id2: nodeB.id }),
      });
      setResult(await res.json());
      setFetched(true);
    } finally {
      setLoading(false);
    }
  };

  if (!fetched) runCompare();

  return (
    <div className="absolute inset-x-4 bottom-4 z-30 max-w-lg mx-auto rounded-xl bg-white border border-gray-200 shadow-lg overflow-hidden">
      {/* Top accent */}
      <div className="h-0.5 bg-gray-900" />

      <div className="p-8">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
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

        {loading && (
          <div className="flex items-center justify-center gap-2 py-8 text-xs text-gray-400">
            <div className="w-4 h-4 border border-gray-400 border-t-transparent rounded-full animate-spin" />
            Анализируем…
          </div>
        )}

        {result && (
          <div className="space-y-6">
            {/* Documents side by side */}
            <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
              <div className="rounded-lg bg-gray-50 border border-gray-100 p-5">
                <p className="text-[9px] text-gray-400 uppercase tracking-wider mb-1">А</p>
                <p className="text-[11px] text-gray-700 leading-snug line-clamp-2">{nodeA.title}</p>
              </div>

              <div className="w-7 h-7 rounded-full bg-gray-100 border border-gray-200 flex items-center justify-center shrink-0">
                <span className="text-[9px] font-bold text-gray-400">VS</span>
              </div>

              <div className="rounded-lg bg-gray-50 border border-gray-100 p-5">
                <p className="text-[9px] text-gray-400 uppercase tracking-wider mb-1">Б</p>
                <p className="text-[11px] text-gray-700 leading-snug line-clamp-2">{nodeB.title}</p>
              </div>
            </div>

            {/* Metrics */}
            <div className="flex items-center gap-2">
              <div className="flex-1 rounded-lg bg-gray-50 border border-gray-100 p-6 text-center">
                <div className={`text-2xl font-bold font-mono leading-none ${SIMILARITY_COLOR(result.similarity)}`}>
                  {Math.round(result.similarity * 100)}%
                </div>
                <div className="text-[9px] text-gray-400 mt-1">схожесть</div>
              </div>

              <div className="flex-1 rounded-lg bg-gray-50 border border-gray-100 p-6 text-center">
                <span className={`inline-flex items-center px-4 py-1 rounded-full text-[10px] font-semibold border ${ASSESSMENT_STYLE[result.assessment]}`}>
                  {ASSESSMENT_LABEL[result.assessment]}
                </span>
                {result.shared_issues > 0 && (
                  <div className="text-[9px] text-orange-500 mt-1.5">{result.shared_issues} общих проблем</div>
                )}
              </div>
            </div>

            <p className="text-[11px] text-gray-500 leading-relaxed">{result.explanation}</p>
          </div>
        )}
      </div>
    </div>
  );
}
