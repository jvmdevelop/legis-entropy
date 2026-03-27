import { useState } from 'react';
import { Graph } from './components/Graph';
import { Sidebar } from './components/Sidebar';
import { SearchBar } from './components/SearchBar';
import { AnalysisProgress } from './components/AnalysisProgress';
import { ComparePanel } from './components/ComparePanel';
import { useGraphData } from './hooks/useGraphData';
import { useAnalysisStream } from './hooks/useAnalysisStream';
import type { GraphNode } from './types';

export default function App() {
  const { data, loading, error, refetch } = useGraphData();
  const { state: stream, start: startStream, stop: stopStream } = useAnalysisStream();

  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [compareNode, setCompareNode] = useState<GraphNode | null>(null);
  const [searchHits, setSearchHits] = useState<string[]>([]);

  // Use streamed graph if available, otherwise fall back to direct fetch
  const activeData = stream.graph ?? data;
  const activeStats = stream.stats ?? null;

  const handleNodeClick = (node: GraphNode, event?: MouseEvent) => {
    if (event?.ctrlKey || event?.metaKey) {
      // Ctrl+click = select for comparison
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
      <div className="h-full flex items-center justify-center text-slate-400">
        <div className="text-center space-y-3">
          <div className="text-4xl">⚠️</div>
          <p className="text-sm">Не удалось загрузить граф</p>
          <p className="text-xs text-slate-500">{error}</p>
          <button onClick={refetch} className="px-4 py-2 text-xs bg-violet-600 hover:bg-violet-500 text-white rounded">
            Повторить
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="relative h-full w-full">
      {/* Initial loading */}
      {loading && !activeData && (
        <div className="absolute inset-0 flex items-center justify-center z-20 bg-slate-950/80 backdrop-blur-sm">
          <div className="text-center space-y-3">
            <div className="w-8 h-8 border-2 border-violet-500 border-t-transparent rounded-full animate-spin mx-auto" />
            <p className="text-sm text-slate-300">Загрузка графа НПА…</p>
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

      {/* SSE progress overlay */}
      <AnalysisProgress state={stream} onStop={stopStream} />

      {/* Document comparison panel */}
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
      <div className="absolute top-4 left-4 flex items-center gap-2 z-10">
        <button
          onClick={startStream}
          disabled={stream.running}
          className="px-3 py-1.5 text-xs bg-slate-800 hover:bg-slate-700 disabled:opacity-50 border border-slate-600 text-slate-300 rounded transition-colors"
        >
          {stream.running ? 'Анализ...' : 'Обновить'}
        </button>
        <SearchBar onResults={setSearchHits} />
        {searchHits.length > 0 && (
          <span className="text-xs text-violet-400">{searchHits.length} совпадений</span>
        )}
      </div>
    </div>
  );
}
