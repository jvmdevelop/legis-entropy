import { useState, useRef, useEffect, useCallback } from 'react';
import { Graph } from './components/Graph';
import { Sidebar } from './components/Sidebar';
import { SearchPanel } from './components/SearchPanel';
import { AnalysisProgress } from './components/AnalysisProgress';
import { ComparePanel } from './components/ComparePanel';
import { NavigationBar } from './components/NavigationBar';
import { CorpusReviewPanel } from './components/CorpusReviewPanel';
import { LegendPanel } from './components/LegendPanel';
import { LoadingOverlay } from './components/LoadingOverlay';
import { ErrorState } from './components/ErrorState';
import { useGraphData } from './hooks/useGraphData';
import { useAnalysisStream } from './hooks/useAnalysisStream';
import type { GraphNode } from './types';

export default function App() {
  const { data, loading, error, refetch } = useGraphData();
  const { state: stream, start: startStream, stop: stopStream } = useAnalysisStream();

  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [compareNode, setCompareNode] = useState<GraphNode | null>(null);
  const [searchHits, setSearchHits] = useState<string[]>([]);
  const [sidebarOpen, setSidebarOpen] = useState(false);
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

  const handleNodeClick = useCallback((node: GraphNode, event?: MouseEvent) => {
    const isCtrl = event?.ctrlKey || event?.metaKey || ctrlHeld.current;
    if (isCtrl) {
      setCompareNode(prev => prev?.id === node.id ? null : node);
      return;
    }
    setSelectedNode(prev => prev?.id === node.id ? null : node);
  }, []);

  const handleCompareSelect = (node: GraphNode) => {
    setCompareNode(prev => prev?.id === node.id ? null : node);
  };

  if (error && !activeData) {
    return <ErrorState error={error} onRetry={refetch} />;
  }

  const showCompare = !!(compareNode && selectedNode && compareNode.id !== selectedNode.id);

  return (
    <div className="relative h-full w-full bg-gradient-to-br from-slate-50 via-white to-slate-100">

      {loading && !activeData && <LoadingOverlay />}

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

      {/* Inspector button — same level as search, top-right */}
      <div className="absolute top-4 right-4 z-20">
        <div className="flex items-center h-12 rounded-lg bg-white/90 backdrop-blur-md border border-white/20 elevation-2 overflow-hidden">
          <button
            onClick={() => setSidebarOpen(o => !o)}
            className="h-full flex items-center gap-2 px-5 text-[11px] font-medium text-gray-600 hover:text-gray-900 hover:bg-gradient-to-r hover:from-gray-50 hover:to-slate-50 transition-all duration-300 relative group ripple"
          >
            <svg width="13" height="13" viewBox="0 0 12 12" fill="none" className="transition-transform duration-300" style={{ transform: sidebarOpen ? 'rotate(180deg)' : 'none' }}>
              <rect x="1" y="1" width="4" height="10" rx="1" stroke="currentColor" strokeWidth="1.3"/>
              <rect x="7" y="1" width="4" height="4" rx="1" stroke="currentColor" strokeWidth="1.3"/>
              <rect x="7" y="7" width="4" height="4" rx="1" stroke="currentColor" strokeWidth="1.3"/>
            </svg>
            <span>Инспектор</span>
            {selectedNode && <span className="w-1.5 h-1.5 rounded-full bg-gray-800 ml-0.5" />}
          </button>
        </div>

        {sidebarOpen && (
          <div className="absolute top-14 right-0 w-80 max-h-[calc(100vh-5rem)] overflow-y-auto rounded-lg bg-white/95 backdrop-blur-md border border-white/20 elevation-3">
            <Sidebar
              node={selectedNode}
              issues={activeData?.issues ?? []}
              stats={activeStats}
              onClose={() => setSelectedNode(null)}
              onCompareSelect={handleCompareSelect}
              compareNode={compareNode}
            />
          </div>
        )}
      </div>

      <NavigationBar
        onStartAnalysis={startStream}
        onFetchCorpusReview={fetchCorpusReview}
        corpusReviewLoading={corpusReviewLoading}
        stream={stream}
      />

      <SearchPanel searchHits={searchHits} onSearchResults={setSearchHits} />

      <CorpusReviewPanel corpusReview={corpusReview} onClose={() => setCorpusReview(null)} />

      {showCompare && (
        <ComparePanel
          nodeA={compareNode!}
          nodeB={selectedNode!}
          onClose={() => setCompareNode(null)}
        />
      )}

      <LegendPanel selectedNode={selectedNode} compareNode={compareNode} />
    </div>
  );
}
