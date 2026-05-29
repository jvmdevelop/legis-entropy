import { useEffect, useRef } from 'react'
import cytoscape, { type Core, type ElementDefinition } from 'cytoscape'

import cose from 'cytoscape-cose-bilkent'
import type { GraphData, RelationType } from '@/entities/graph'
import { X } from 'lucide-react'

cytoscape.use(cose)

const RELATION_TYPE_LABELS: Record<RelationType, { ru: string; color: string }> = {
  REFERENCES: { ru: 'ссылается на', color: 'rgb(59, 130, 246)' },
  AMENDS: { ru: 'изменяет', color: 'rgb(139, 92, 246)' },
  DEFINES: { ru: 'определяет', color: 'rgb(34, 197, 94)' },
  RELATED: { ru: 'связан с', color: 'rgb(168, 85, 247)' },
  SUPERSEDES: { ru: 'заменяет', color: 'rgb(220, 38, 38)' },
  IMPLEMENTS: { ru: 'имплементирует', color: 'rgb(251, 146, 60)' },
}

interface GraphVisualizationProps {
  data: GraphData
  title: string
  onClose?: () => void
}

export function GraphVisualization({ data, title, onClose }: GraphVisualizationProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const cyRef = useRef<Core | null>(null)

  useEffect(() => {
    if (!containerRef.current) return

    const elements: ElementDefinition[] = [
      ...data.nodes.map((node) => ({
        data: {
          id: node.id,
          label: node.label,
          code: node.code,
          nodeType: node.type,
        },
      })),
      ...data.edges.map((edge) => ({
        data: {
          id: `${edge.source}-${edge.target}`,
          source: edge.source,
          target: edge.target,
          label: RELATION_TYPE_LABELS[edge.relationType].ru,
          relationType: edge.relationType,
        },
      })),
    ]

    const cy = cytoscape({
      container: containerRef.current,
      elements,
      style: [
        {
          selector: 'node',
          style: {
            'background-color': function (ele: any) {
              return ele.data('nodeType') === 'main' ? 'rgb(139, 92, 246)' : 'rgb(229, 231, 235)'
            },
            'label': 'data(label)',
            'color': function (ele: any) {
              return ele.data('nodeType') === 'main' ? '#fff' : 'rgb(55, 65, 81)'
            },
            'font-size': '11px',
            'width': function (ele: any) {
              return ele.data('nodeType') === 'main' ? '180px' : '140px'
            },
            'height': 'label',
            'padding': '12px',
            'shape': 'roundrectangle',
            'border-width': function (ele: any) {
              return ele.data('nodeType') === 'main' ? '2px' : '0px'
            },
            'border-color': 'rgba(139, 92, 246, 0.3)',
            'text-valign': 'center',
            'text-halign': 'center',
            'text-wrap': 'wrap',
            'text-max-width': '160px',
          } as any,
        },
        {
          selector: 'edge',
          style: {
            'line-color': function (ele: any) {
              const relType = ele.data('relationType') as RelationType
              return RELATION_TYPE_LABELS[relType]?.color || '#ccc'
            },
            'target-arrow-color': function (ele: any) {
              const relType = ele.data('relationType') as RelationType
              return RELATION_TYPE_LABELS[relType]?.color || '#ccc'
            },
            'target-arrow-shape': 'triangle',
            'label': 'data(label)',
            'font-size': '9px',
            'color': 'rgb(107, 114, 128)',
            'width': '2px',
            'curve-style': 'bezier',
            'text-background-color': 'white',
            'text-background-opacity': 0.7,
            'text-background-padding': '2px',
          } as any,
        },
      ],
      layout: {
        name: 'cose',
        animate: true,
        animationDuration: 500,
        nodeSpacing: 50,
        padding: 30,
        randomize: false,
      } as any,
    })

    cyRef.current = cy

    return () => {
      cy.destroy()
    }
  }, [data])

  const relationshipStats = Object.entries(data.edges.reduce(
    (acc, edge) => {
      acc[edge.relationType] = (acc[edge.relationType] || 0) + 1
      return acc
    },
    {} as Record<string, number>,
  ))

  return (
    <div className="flex flex-col h-full bg-white rounded-xl border border-[var(--color-border)]">

      <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--color-border)]">
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-semibold text-[var(--color-text)] truncate">{title}</h3>
          <p className="text-xs text-[var(--color-text-muted)] mt-1">
            {data.nodes.length} законов · {data.edges.length} связей
          </p>
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="p-1.5 hover:bg-[var(--color-surface)] rounded-lg transition-colors shrink-0"
          >
            <X size={16} className="text-[var(--color-text-muted)]" />
          </button>
        )}
      </div>

      <div ref={containerRef} className="flex-1 relative bg-gradient-to-br from-[var(--color-bg)] to-[var(--color-surface)]" />

      <div className="px-5 py-3 border-t border-[var(--color-border)] bg-[var(--color-surface)]">
        <p className="text-xs font-semibold text-[var(--color-text-muted)] mb-2">Типы связей:</p>
        <div className="grid grid-cols-2 gap-2 text-xs">
          {relationshipStats.map(([type, count]) => (
            <div key={type} className="flex items-center gap-2">
              <div
                className="w-2 h-2 rounded-full shrink-0"
                style={{
                  backgroundColor: RELATION_TYPE_LABELS[type as RelationType]?.color || '#ccc',
                }}
              />
              <span className="text-[var(--color-text-muted)]">
                {RELATION_TYPE_LABELS[type as RelationType]?.ru || type} ({count})
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
