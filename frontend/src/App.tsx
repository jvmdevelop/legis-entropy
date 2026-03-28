import { useState, useRef, useEffect } from 'react';
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

      <Sidebar
        node={selectedNode}
        issues={activeData?.issues ?? []}
        stats={activeStats}
        onClose={() => setSelectedNode(null)}
        onCompareSelect={handleCompareSelect}
        compareNode={compareNode}
      />

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
