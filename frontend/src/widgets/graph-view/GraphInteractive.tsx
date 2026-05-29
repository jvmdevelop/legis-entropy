import { useState, useEffect, useRef } from 'react'
import cytoscape, { type Core, type ElementDefinition } from 'cytoscape'

import cose from 'cytoscape-cose-bilkent'
import type { GraphData, RelationType, LawDTO } from '@/entities/graph'
import { X, Download, Filter } from 'lucide-react'
import { toast } from 'sonner'

cytoscape.use(cose)

const RELATION_TYPE_LABELS: Record<RelationType, { ru: string; color: string }> = {
  REFERENCES: { ru: 'ссылается на', color: 'rgb(59, 130, 246)' },
  AMENDS: { ru: 'изменяет', color: 'rgb(139, 92, 246)' },
  DEFINES: { ru: 'определяет', color: 'rgb(34, 197, 94)' },
  RELATED: { ru: 'связан с', color: 'rgb(168, 85, 247)' },
  SUPERSEDES: { ru: 'заменяет', color: 'rgb(220, 38, 38)' },
  IMPLEMENTS: { ru: 'имплементирует', color: 'rgb(251, 146, 60)' },
}

interface GraphInteractiveProps {
  data: GraphData
  mainLaw: LawDTO
  title: string
  onClose?: () => void
}

export function GraphInteractive({ data, mainLaw, title, onClose }: GraphInteractiveProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const cyRef = useRef<Core | null>(null)
  const [selectedNode, setSelectedNode] = useState<string | null>(null)
  const [filters, setFilters] = useState<Set<RelationType>>(new Set(Object.keys(RELATION_TYPE_LABELS) as RelationType[]))

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
      ...data.edges
        .filter(edge => filters.has(edge.relationType))
        .map((edge) => ({
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
              return ele.data('nodeType') === 'main' ? '2px' : '1px'
            },
            'border-color': 'rgba(139, 92, 246, 0.3)',
            'text-valign': 'center',
            'text-halign': 'center',
            'text-wrap': 'wrap',
            'text-max-width': '160px',
            'cursor': 'pointer',
          } as any,
        },
        {
          selector: 'node:selected',
          style: {
            'border-width': '3px',
            'border-color': 'rgb(139, 92, 246)',
            'box-shadow': '0 0 12px rgba(139, 92, 246, 0.4)',
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
            'text-background-opacity': 0.8,
            'text-background-padding': '3px',
          } as any,
        },
      ],
      layout: {
        name: 'cose',
        animate: true,
        animationDuration: 400,
        nodeSpacing: 50,
        padding: 30,
        randomize: false,
      } as any,
    })

    cy.on('tap', 'node', (event: any) => {
      const node = event.target
      setSelectedNode(node.id())
    })

    cy.on('tap', (event: any) => {
      if (event.target === cy) {
        setSelectedNode(null)
      }
    })

    cyRef.current = cy

    return () => {
      cy.destroy()
    }
  }, [data, filters])

  const handleExport = () => {
    if (!cyRef.current) return
    const png = cyRef.current.png({ full: true })
    const link = document.createElement('a')
    link.href = png
    link.download = `graph-${mainLaw.code}.png`
    link.click()
    toast.success('Граф скачан')
  }

  const toggleFilter = (relType: RelationType) => {
    const newFilters = new Set(filters)
    if (newFilters.has(relType)) {
      newFilters.delete(relType)
    } else {
      newFilters.add(relType)
    }
    setFilters(newFilters)
  }

  const selectedNodeData = data.nodes.find(n => n.id === selectedNode)

  return (
    <div className="flex flex-col h-full bg-white rounded-xl border border-[var(--color-border)]">

      <div className="flex items-center justify-between px-5 py-3 border-b border-[var(--color-border)]">
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-semibold text-[var(--color-text)] truncate">{title}</h3>
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          <button
            onClick={handleExport}
            className="p-1.5 hover:bg-[var(--color-surface)] rounded-lg transition-colors"
            title="Скачать"
          >
            <Download size={16} className="text-[var(--color-text-muted)]" />
          </button>
          {onClose && (
            <button
              onClick={onClose}
              className="p-1.5 hover:bg-[var(--color-surface)] rounded-lg transition-colors"
            >
              <X size={16} className="text-[var(--color-text-muted)]" />
            </button>
          )}
        </div>
      </div>

      <div className="flex flex-1 min-h-0 overflow-hidden">

        <div ref={containerRef} className="flex-1 relative bg-gradient-to-br from-[var(--color-bg)] to-[var(--color-surface)]" />

        <div className="w-64 border-l border-[var(--color-border)] bg-[var(--color-surface)] flex flex-col overflow-hidden">

          <div className="px-4 py-3 border-b border-[var(--color-border)]">
            <div className="flex items-center gap-2 mb-2">
              <Filter size={14} className="text-[var(--color-accent)]" />
              <span className="text-xs font-semibold text-[var(--color-text-muted)]">Типы связей</span>
            </div>
            <div className="space-y-1">
              {Object.entries(RELATION_TYPE_LABELS).map(([type, { ru, color }]) => (
                <label key={type} className="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-[var(--color-surface-2)] cursor-pointer transition-colors">
                  <input
                    type="checkbox"
                    checked={filters.has(type as RelationType)}
                    onChange={() => toggleFilter(type as RelationType)}
                    className="w-4 h-4 accent-[var(--color-accent)]"
                  />
                  <div className="w-2 h-2 rounded-full" style={{ backgroundColor: color }} />
                  <span className="text-xs text-[var(--color-text-muted)] flex-1">{ru}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-4 py-3">
            {selectedNodeData ? (
              <div>
                <div className="text-xs font-semibold text-[var(--color-text-muted)] mb-2">ВЫБРАННЫЙ ЗАКОН</div>
                <div className="text-sm font-medium text-[var(--color-text)] mb-2">{selectedNodeData.label}</div>
                <div className="text-xs text-[var(--color-text-muted)] mb-3 px-2 py-1 rounded bg-white border border-[var(--color-border)]">
                  Код: {selectedNodeData.code}
                </div>
                <button
                  onClick={() => {
                    toast.info(`Загружение информации о ${selectedNodeData.code}...`)
                  }}
                  className="w-full px-3 py-1.5 text-xs font-medium rounded-lg bg-[var(--color-accent)] text-white hover:bg-[var(--color-accent-hover)] transition-colors"
                >
                  Показать детали
                </button>
              </div>
            ) : (
              <div className="text-xs text-[var(--color-text-muted)] text-center py-8">
                Кликните на узел чтобы увидеть детали
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="px-4 py-2 border-t border-[var(--color-border)] bg-[var(--color-surface)] text-xs text-[var(--color-text-muted)]">
        <span>{data.nodes.length} узлов · {data.edges.filter(e => filters.has(e.relationType)).length} связей</span>
      </div>
    </div>
  )
}
