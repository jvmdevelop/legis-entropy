import { useState, useRef, useEffect, useCallback } from 'react';
import { Graph } from './components/Graph';
import { InspectorPanel } from './components/InspectorPanel';
import { AnalysisProgress } from './components/AnalysisProgress';
import { ComparePanel } from './components/ComparePanel';
import { NavigationBar } from './components/NavigationBar';
import { CorpusReviewPanel } from './components/CorpusReviewPanel';
import { LegendPanel } from './components/LegendPanel';
import { LoadingOverlay } from './components/LoadingOverlay';
import { ErrorState } from './components/ErrorState';
import { useGraphData } from './hooks/useGraphData';
import { useAnalysisStream } from './hooks/useAnalysisStream';
import { useCorpusReview } from './hooks/useCorpusReview';
import type { GraphNode } from './types';

export default function App() {
  const { data, loading, error, refetch } = useGraphData();
  const { state: stream, start: startStream, stop: stopStream } = useAnalysisStream();
  const { data: corpusReview, loading: corpusReviewLoading, load: fetchCorpusReview, dismiss: closeCorpusReview } = useCorpusReview();

  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [compareNode, setCompareNode] = useState<GraphNode | null>(null);
  const [searchHits, setSearchHits] = useState<string[]>([]);
  const [inspectorOpen, setInspectorOpen] = useState(true);

  const ctrlHeld = useRef(false);
  useEffect(() => {
    const down = (e: KeyboardEvent) => { if (e.key === 'Control' || e.key === 'Meta') ctrlHeld.current = true; };
    const up = (e: KeyboardEvent) => { if (e.key === 'Control' || e.key === 'Meta') ctrlHeld.current = false; };
    window.addEventListener('keydown', down);
    window.addEventListener('keyup', up);
    return () => { window.removeEventListener('keydown', down); window.removeEventListener('keyup', up); };
  }, []);

  const handleNodeClick = useCallback((node: GraphNode, event?: MouseEvent) => {
    const isCtrl = event?.ctrlKey || event?.metaKey || ctrlHeld.current;
    if (isCtrl) {
      setCompareNode(prev => prev?.id === node.id ? null : node);
    } else {
      setSelectedNode(prev => prev?.id === node.id ? null : node);
    }
  }, []);

  const handleCompareSelect = useCallback((node: GraphNode) => {
    setCompareNode(prev => prev?.id === node.id ? null : node);
  }, []);

  const activeData = stream.graph ?? data;
  const activeStats = stream.stats ?? null;
  const showCompare = !!(compareNode && selectedNode && compareNode.id !== selectedNode.id);

  if (error && !activeData) {
    return <ErrorState error={error} onRetry={refetch} />;
  }

  return (
    <div className="flex h-screen flex-col bg-background text-foreground">
      <NavigationBar
        onStartAnalysis={startStream}
        onFetchCorpusReview={fetchCorpusReview}
        corpusReviewLoading={corpusReviewLoading}
        stream={stream}
        onInspectorToggle={() => setInspectorOpen(o => !o)}
        isInspectorOpen={inspectorOpen}
        searchHits={searchHits}
        onSearchResults={setSearchHits}
      />

      <div className="flex flex-1 overflow-hidden">
        <main className="relative flex-1 overflow-hidden">
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

          <CorpusReviewPanel corpusReview={corpusReview} onClose={closeCorpusReview} />

          {showCompare && (
            <ComparePanel
              nodeA={compareNode!}
              nodeB={selectedNode!}
              onClose={() => setCompareNode(null)}
            />
          )}

          <LegendPanel selectedNode={selectedNode} compareNode={compareNode} />
        </main>

        {inspectorOpen && (
          <InspectorPanel
            node={selectedNode}
            issues={activeData?.issues ?? []}
            stats={activeStats}
            onNodeClose={() => setSelectedNode(null)}
            onCompareSelect={handleCompareSelect}
            compareNode={compareNode}
            onClose={() => setInspectorOpen(false)}
          />
        )}
      </div>
    </div>
  );
}
