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

export default function App() {
  const { data, loading, error, refetch } = useGraphData();
  const { state: stream, start: startStream, stop: stopStream } = useAnalysisStream();

  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [compareNode, setCompareNode] = useState<GraphNode | null>(null);
  const [searchHits, setSearchHits] = useState<string[]>([]);

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
        <div className="text-center space-y-4 p-16 rounded-xl border border-gray-200">
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
            className="px-8 py-4 text-xs font-medium bg-gray-900 hover:bg-gray-700 text-white rounded-lg transition-colors"
          >
            Повторить
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="relative h-full w-full bg-gray-50">
      {/* Loading overlay */}
      {loading && !activeData && (
        <div className="absolute inset-0 flex items-center justify-center z-20 bg-white/90 backdrop-blur-sm">
          <div className="text-center space-y-4">
            <Logo size={32} className="mx-auto" />
            <div className="space-y-2">
              <div className="w-5 h-5 border-2 border-gray-900 border-t-transparent rounded-full animate-spin mx-auto" />
              <p className="text-xs text-gray-400">Загрузка графа НПА…</p>
            </div>
          </div>
        </div>
      )}

      {activeData && (
        <Graph
          data={activeData}
          onNodeClick={handleNodeClick}
          selectedId={selectedNode?.id ?? null}
          highlightIds={searchHits.length > 0 ? searchHits : undefined}
          compareId={compareNode?.id ?? null}
        />
      )}

      <AnalysisProgress state={stream} onStop={stopStream} />

      {compareNode && selectedNode && compareNode.id !== selectedNode.id && (
        <ComparePanel
          nodeA={compareNode}
          nodeB={selectedNode}
          onClose={() => setCompareNode(null)}
        />
      )}

      <Sidebar
        node={selectedNode}
        issues={activeData?.issues ?? []}
        stats={activeStats}
        onClose={() => setSelectedNode(null)}
        onCompareSelect={handleCompareSelect}
        compareNode={compareNode}
      />

      {/* Top bar */}
      <div className="absolute top-3 left-3 flex items-center gap-2 z-10">
        <div className="flex items-center gap-2.5 px-6 py-4 rounded-lg bg-white border border-gray-200 shadow-sm">
          <Logo size={18} />
          <div className="w-px h-4 bg-gray-200" />
          <span className="text-xs font-medium text-gray-500">Граф НПА</span>
        </div>

        <button
          onClick={startStream}
          disabled={stream.running}
          className="flex items-center gap-1.5 px-6 py-4 text-xs font-medium rounded-lg bg-white border border-gray-200 shadow-sm text-gray-600 hover:text-gray-900 hover:border-gray-300 disabled:opacity-40 transition-all"
        >
          {stream.running ? (
            <div className="w-3 h-3 border border-gray-400 border-t-transparent rounded-full animate-spin" />
          ) : (
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
              <path d="M10.5 6A4.5 4.5 0 1 1 7.5 1.9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
              <path d="M7.5 1v2.5H10" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          )}
          {stream.running ? 'Анализ…' : 'Анализ'}
        </button>

        <SearchBar onResults={setSearchHits} />

        {searchHits.length > 0 && (
          <span className="px-4 py-2 text-[10px] font-semibold rounded-full bg-gray-900 text-white">
            {searchHits.length}
          </span>
        )}
      </div>

      {/* Ctrl hint — показываем когда нода выбрана но compareNode не выбран */}
      {selectedNode && !compareNode && (
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-10 px-4 py-2 rounded-full bg-white border border-gray-200 shadow-sm text-[10px] text-gray-400 pointer-events-none">
          Ctrl+клик по другому узлу для сравнения
        </div>
      )}
    </div>
  );
}
