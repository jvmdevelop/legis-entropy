import { useState } from 'react'
import { useGraphStore, convertToGraphData } from '@/features/graph/model/useGraphStore'
import { GraphInteractive } from './GraphInteractive'
import { GraphSkeleton } from '@/shared/ui/Skeleton'
import { BookOpen, Maximize2 } from 'lucide-react'

export function GraphView() {
  const { graphs, selectedGraphCode, setSelectedGraph, isLoading } = useGraphStore()
  const [expandedView, setExpandedView] = useState(false)

  if (graphs.length === 0 && !isLoading) return null

  if (isLoading && graphs.length === 0) {
    return <GraphSkeleton />
  }

  const selectedGraph = selectedGraphCode
    ? graphs.find(g => g.law.code === selectedGraphCode)
    : graphs[0]

  if (!selectedGraph) return null

  const graphData = convertToGraphData(selectedGraph)

  if (expandedView) {
    return (
      <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-2xl w-full max-w-5xl h-[90vh] flex flex-col">
          <GraphInteractive
            data={graphData}
            mainLaw={selectedGraph.law}
            title={selectedGraph.law.title}
            onClose={() => setExpandedView(false)}
          />
        </div>
      </div>
    )
  }

  return (
    <div className="border-t border-[var(--color-border)] bg-[var(--color-surface)] rounded-t-3xl">

      {graphs.length > 1 && (
        <div className="flex items-center gap-3 px-6 py-4 border-b border-[var(--color-border)] overflow-x-auto">
          {graphs.map((graph) => (
            <button
              key={graph.law.code}
              onClick={() => setSelectedGraph(graph.law.code)}
              className={`flex-shrink-0 px-4 py-2 rounded-2xl text-xs font-medium transition-colors ${
                selectedGraphCode === graph.law.code
                  ? 'bg-[var(--color-accent)] text-white'
                  : 'bg-[var(--color-surface-2)] text-[var(--color-text-muted)] hover:text-[var(--color-text)]'
              }`}
            >
              {graph.law.code}
            </button>
          ))}
        </div>
      )}

      <div className="p-6 max-h-[400px] flex flex-col gap-4">

        <div className="flex items-start gap-3">
          <div className="w-8 h-8 rounded-lg bg-[var(--color-accent-glow)] flex items-center justify-center flex-shrink-0">
            <BookOpen size={16} style={{ color: 'var(--color-accent)' }} />
          </div>
          <div className="flex-1 min-w-0">
            <h4 className="font-semibold text-sm text-[var(--color-text)] truncate">
              {selectedGraph.law.title}
            </h4>
            <p className="text-xs text-[var(--color-text-muted)] mt-1 line-clamp-2">
              {selectedGraph.law.summary}
            </p>
            <div className="flex flex-wrap gap-2 mt-2">
              <span className="inline-block px-2 py-1 rounded-md bg-[var(--color-surface-2)] text-xs text-[var(--color-text-muted)]">
                {selectedGraph.law.code}
              </span>
              <span className="inline-block px-2 py-1 rounded-md bg-[var(--color-surface-2)] text-xs text-[var(--color-text-muted)]">
                {selectedGraph.law.type}
              </span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2 text-xs">
          {Object.entries(selectedGraph.relationships)
            .filter(([_, laws]) => laws.length > 0)
            .map(([relType, laws]) => (
              <div key={relType} className="flex items-center gap-1 px-2 py-1 rounded-lg bg-[var(--color-surface-2)]">
                <span className="text-[var(--color-accent)]">•</span>
                <span className="text-[var(--color-text-muted)]">
                  {relType.toLowerCase()}: {laws.length}
                </span>
              </div>
            ))}
        </div>

        <button
          onClick={() => setExpandedView(true)}
          className="flex items-center justify-center gap-2 w-full py-3 px-4 rounded-2xl bg-[var(--color-accent)] text-white text-sm font-semibold hover:bg-[var(--color-accent-hover)] transition-colors"
        >
          <Maximize2 size={16} />
          Открыть полностью
        </button>
      </div>
    </div>
  )
}
