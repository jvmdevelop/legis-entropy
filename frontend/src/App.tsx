import { useState, useRef, useEffect } from 'react';
import { Graph } from './components/Graph';
import { Sidebar } from './components/Sidebar';
import { SearchBar } from './components/SearchBar';
import { AnalysisProgress } from './components/AnalysisProgress';
import { ComparePanel } from './components/ComparePanel';
import { Logo } from './components/Logo';
import { useGraphData } from './hooks/useGraphData';
import { useAnalysisStream } from './hooks/useAnalysisStream';
import type { GraphNode } from './types';

const LEGEND_ITEMS = [
  { color: '#10b981', label: 'Действующий' },
  { color: '#ef4444', label: 'Утратил силу' },
  { color: '#9ca3af', label: 'Неизвестен' },
  { color: '#3b82f6', label: 'Акт изменений' },
];

export default function App() {
  const { data, loading, error, refetch } = useGraphData();
  const { state: stream, start: startStream, stop: stopStream } = useAnalysisStream();

  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [compareNode, setCompareNode] = useState<GraphNode | null>(null);
  const [searchHits, setSearchHits] = useState<string[]>([]);
  const [corpusReview, setCorpusReview] = useState<{ review: string; llm_ready: boolean } | null>(null);
  const [corpusReviewLoading, setCorpusReviewLoading] = useState(false);

  const fetchCorpusReview = async () => {
    setCorpusReviewLoading(true);
    try {
      const res = await fetch('/api/corpus-review');
      if (res.ok) setCorpusReview(await res.json());
    } finally {
      setCorpusReviewLoading(false);
    }
  };

  // Track Ctrl/Meta key globally — canvas events sometimes lose ctrlKey
  const ctrlHeld = useRef(false);
  useEffect(() => {
    const down = (e: KeyboardEvent) => { if (e.key === 'Control' || e.key === 'Meta') ctrlHeld.current = true; };
    const up = (e: KeyboardEvent) => { if (e.key === 'Control' || e.key === 'Meta') ctrlHeld.current = false; };
    window.addEventListener('keydown', down);
    window.addEventListener('keyup', up);
    return () => { window.removeEventListener('keydown', down); window.removeEventListener('keyup', up); };
  }, []);

  const activeData = stream.graph ?? data;
  const activeStats = stream.stats ?? null;

  const handleNodeClick = (node: GraphNode, event?: MouseEvent) => {
    const isCtrl = event?.ctrlKey || event?.metaKey || ctrlHeld.current;
    if (isCtrl) {
      setCompareNode(prev => prev?.id === node.id ? null : node);
      return;
    }
    setSelectedNode(prev => prev?.id === node.id ? null : node);
  };

  const handleCompareSelect = (node: GraphNode) => {
    setCompareNode(prev => prev?.id === node.id ? null : node);
  };

  if (error && !activeData) {
    return (
      <div className="h-full flex items-center justify-center bg-white">
        <div className="text-center space-y-4 p-16 rounded-2xl border border-gray-200 shadow-sm">
          <div className="w-10 h-10 rounded-full bg-red-50 border border-red-100 flex items-center justify-center mx-auto">
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
              <path d="M9 6v3m0 3h.01M17 9A8 8 0 111 9a8 8 0 0116 0z" stroke="#ef4444" strokeWidth="1.5" strokeLinecap="round"/>
            </svg>
          </div>
          <div>
            <p className="text-sm font-medium text-gray-900">Не удалось загрузить граф</p>
            <p className="text-xs text-gray-400 mt-1">{error}</p>
          </div>
          <button
            onClick={refetch}
            className="px-8 py-3 text-xs font-medium bg-gray-900 hover:bg-gray-700 text-white rounded-xl transition-colors"
          >
            Повторить
          </button>
        </div>
      </div>
    );
  }

  const showCompare = !!(compareNode && selectedNode && compareNode.id !== selectedNode.id);

  return (
    <div className="relative h-full w-full bg-slate-50">

      {/* Loading overlay */}
      {loading && !activeData && (
        <div className="absolute inset-0 flex items-center justify-center z-20 bg-white/90 backdrop-blur-sm">
          <div className="text-center space-y-4">
            <Logo size={32} className="mx-auto" />
            <div className="space-y-2">
              <div className="w-5 h-5 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin mx-auto" />
              <p className="text-xs text-gray-400">Загрузка графа НПА…</p>
            </div>
          </div>
        </div>
      )}

      {/* Graph canvas */}
      {activeData && (
        <Graph
          data={activeData}
          onNodeClick={handleNodeClick}
          selectedId={selectedNode?.id ?? null}
          highlightIds={searchHits.length > 0 ? searchHits : undefined}
          compareId={compareNode?.id ?? null}
        />
      )}

      {/* Analysis progress */}
      <AnalysisProgress state={stream} onStop={stopStream} />

      {/* Sidebar */}
      <Sidebar
        node={selectedNode}
        issues={activeData?.issues ?? []}
        stats={activeStats}
        onClose={() => setSelectedNode(null)}
        onCompareSelect={handleCompareSelect}
        compareNode={compareNode}
      />

      {/* ── Top-left brand bar ── */}
      <div className="absolute top-3 left-3 z-10">
        <div className="flex items-center h-10 rounded-xl bg-white border border-gray-200 shadow-sm overflow-hidden divide-x divide-gray-100">
          {/* Brand */}
          <div className="flex items-center gap-2 px-4 h-full">
            <Logo size={17} />
            <span className="text-[11px] font-bold text-gray-900 tracking-tight">Граф НПА</span>
          </div>

          {/* Анализ */}
          <button
            onClick={startStream}
            disabled={stream.running}
            className="h-full flex items-center gap-1.5 px-4 text-[11px] font-medium text-gray-600 hover:text-gray-900 hover:bg-gray-50 disabled:opacity-40 transition-colors"
          >
            {stream.running ? (
              <div className="w-3 h-3 border border-gray-400 border-t-transparent rounded-full animate-spin" />
            ) : (
              <svg width="11" height="11" viewBox="0 0 12 12" fill="none">
                <path d="M10.5 6A4.5 4.5 0 1 1 7.5 1.9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
                <path d="M7.5 1v2.5H10" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            )}
            {stream.running ? 'Анализ…' : 'Анализ'}
          </button>

          {/* AI обзор */}
          <button
            onClick={fetchCorpusReview}
            disabled={corpusReviewLoading}
            className="h-full flex items-center gap-1.5 px-4 text-[11px] font-medium text-violet-600 hover:text-violet-800 hover:bg-violet-50 disabled:opacity-40 transition-colors"
          >
            {corpusReviewLoading ? (
              <div className="w-3 h-3 border border-violet-400 border-t-transparent rounded-full animate-spin" />
            ) : (
              <span className="text-sm leading-none">✦</span>
            )}
            {corpusReviewLoading ? 'AI анализ…' : 'AI обзор'}
          </button>
        </div>
      </div>

      {/* ── Search bar — top center ── */}
      <div className="absolute top-3 left-1/2 -translate-x-1/2 z-20">
        <div className="flex items-center h-10 rounded-xl bg-white border border-gray-200 shadow-sm">
          <SearchBar onResults={setSearchHits} embedded />
          {searchHits.length > 0 && (
            <span className="mr-3 px-2.5 py-0.5 text-[10px] font-bold rounded-full bg-gray-900 text-white tabular-nums">
              {searchHits.length}
            </span>
          )}
        </div>
      </div>

      {/* ── Corpus AI review panel — below topbar ── */}
      {corpusReview && !showCompare && (
        <div className="absolute top-[52px] left-3 z-20 w-80 rounded-2xl bg-white border border-violet-200 shadow-lg overflow-hidden">
          <div className="h-0.5 bg-violet-500" />
          <div className="p-5 space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="text-[9px] font-bold uppercase tracking-widest text-violet-600">
                {corpusReview.llm_ready ? '✦ AI-обзор корпуса (Qwen2)' : '⏳ AI-обзор'}
              </span>
              <button onClick={() => setCorpusReview(null)} className="text-gray-400 hover:text-gray-600 transition-colors">
                <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                  <path d="M1 1l8 8M9 1L1 9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
              </button>
            </div>
            <p className="text-[11px] text-violet-900 leading-relaxed">{corpusReview.review}</p>
          </div>
        </div>
      )}

      {/* ── Compare panel — center canvas area ── */}
      {showCompare && (
        <ComparePanel
          nodeA={compareNode!}
          nodeB={selectedNode!}
          onClose={() => setCompareNode(null)}
        />
      )}

      {/* ── Legend — bottom center ── */}
      <div className="absolute bottom-3 left-1/2 -translate-x-1/2 z-10 pointer-events-none">
        <div className="flex items-center gap-4 px-5 py-2.5 rounded-xl bg-white/90 backdrop-blur-md border border-gray-200 shadow-md pointer-events-auto">
          {LEGEND_ITEMS.map(({ color, label }) => (
            <div key={label} className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full shrink-0" style={{ background: color }} />
              <span className="text-[10px] text-gray-500 whitespace-nowrap">{label}</span>
            </div>
          ))}
          <div className="w-px h-3 bg-gray-200" />
          <span className="text-[10px] text-gray-400 whitespace-nowrap">Размер — ссылки</span>
          {selectedNode && !compareNode && (
            <>
              <div className="w-px h-3 bg-gray-200" />
              <span className="text-[10px] text-gray-400 whitespace-nowrap">Ctrl+клик — сравнить</span>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
