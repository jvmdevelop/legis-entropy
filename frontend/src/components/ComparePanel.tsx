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

const ASSESSMENT_COLOR: Record<string, string> = {
  duplicate: 'text-red-300 bg-red-500/20 border-red-500/30',
  highly_related: 'text-orange-300 bg-orange-500/20 border-orange-500/30',
  related: 'text-yellow-300 bg-yellow-500/20 border-yellow-500/30',
  independent: 'text-emerald-300 bg-emerald-500/20 border-emerald-500/30',
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
    <div className="absolute inset-x-4 bottom-4 z-30 bg-slate-900 border border-slate-700 rounded-lg p-4 shadow-xl">
      <div className="flex items-center justify-between mb-3">
        <span className="text-xs font-semibold text-slate-200">Сравнение документов</span>
        <button onClick={onClose} className="text-slate-400 hover:text-slate-200">×</button>
      </div>

      {loading && (
        <div className="flex items-center gap-2 text-xs text-slate-400">
          <div className="w-3 h-3 border border-violet-400 border-t-transparent rounded-full animate-spin" />
          Анализ...
        </div>
      )}

      {result && (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-2 text-xs">
            <div className="bg-slate-800 rounded p-2 truncate text-slate-300">{nodeA.title}</div>
            <div className="bg-slate-800 rounded p-2 truncate text-slate-300">{nodeB.title}</div>
          </div>

          <div className="flex items-center gap-3">
            <span className={`px-2 py-0.5 rounded text-xs font-medium border ${ASSESSMENT_COLOR[result.assessment]}`}>
              {ASSESSMENT_LABEL[result.assessment]}
            </span>
            <span className="text-xs text-slate-400">
              Схожесть: <span className="text-slate-200 font-mono">{Math.round(result.similarity * 100)}%</span>
            </span>
            {result.shared_issues > 0 && (
              <span className="text-xs text-orange-400">
                {result.shared_issues} общих проблем
              </span>
            )}
          </div>

          <p className="text-xs text-slate-400 leading-relaxed">{result.explanation}</p>
        </div>
      )}
    </div>
  );
}
