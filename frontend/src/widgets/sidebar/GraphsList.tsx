import { useState } from 'react'
import { Network, ChevronDown, ChevronUp, Trash2, Eye } from 'lucide-react'
import { useGraphStore } from '@/features/graph'

export function GraphsList() {
  const { graphs, selectedGraphCode, setSelectedGraph, removeGraph } = useGraphStore()
  const [isExpanded, setIsExpanded] = useState(true)

  if (graphs.length === 0) return null

  return (
    <div className="pr-5 flex flex-col" style={{ paddingLeft: '30px' }}>

      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="flex items-center justify-between py-2 text-xs font-semibold text-[var(--color-text-muted)] hover:text-[var(--color-text)] transition-colors"
        style={{
          fontFamily: "'IBM Plex Mono', monospace",
          textTransform: 'uppercase',
          letterSpacing: '0.12em',
        }}
      >
        <span className="flex items-center gap-2">
          <Network size={12} />
          Графы ({graphs.length})
        </span>
        {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
      </button>

      {isExpanded && (
        <div className="mt-3 space-y-2 pb-4">
          {graphs.map((graph) => (
            <div
              key={graph.law.code}
              onClick={() => setSelectedGraph(graph.law.code)}
              className={`group flex items-center gap-3 px-4 py-2.5 rounded-2xl text-sm cursor-pointer transition-colors ${
                selectedGraphCode === graph.law.code
                  ? 'bg-[var(--color-accent)] text-white'
                  : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-2)] hover:text-[var(--color-text)]'
              }`}
            >
              <Eye size={12} className="flex-shrink-0" />
              <div className="flex-1 min-w-0">
                <div className="truncate font-medium">{graph.law.title}</div>
                <div className="text-[10px] opacity-70 truncate">{graph.law.code}</div>
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  removeGraph(graph.law.code)
                }}
                className="opacity-0 group-hover:opacity-100 transition-opacity p-1 hover:text-red-500"
              >
                <Trash2 size={12} />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
