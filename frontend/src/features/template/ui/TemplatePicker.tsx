import { useEffect, useMemo, useState } from 'react'
import { FileText, Loader2, Sparkles } from 'lucide-react'
import { cn } from '@/shared/lib/cn'
import type {
  GeneratedDocumentDTO, TemplateDTO, TemplatePlaceholder,
} from '@/entities/template'
import { templateApi } from '@/features/template/api/templateApi'

interface Props {
  graphId?: string
  situationId?: string

  initialVariables?: Record<string, string>
  onGenerated?: (doc: GeneratedDocumentDTO) => void

  kind?: string
}

export function TemplatePicker({ graphId, situationId, initialVariables, onGenerated, kind }: Props) {
  const [templates, setTemplates] = useState<TemplateDTO[]>([])
  const [selected, setSelected] = useState<TemplateDTO | null>(null)
  const [vars, setVars] = useState<Record<string, string>>({})
  const [rendering, setRendering] = useState(false)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    templateApi.list(kind)
      .then(setTemplates)
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [kind])

  const placeholders = useMemo<TemplatePlaceholder[]>(() => {
    if (!selected?.placeholdersJson) return []
    try {
      return JSON.parse(selected.placeholdersJson) as TemplatePlaceholder[]
    } catch {
      return []
    }
  }, [selected])

  useEffect(() => {
    if (!selected) return
    setVars(prev => {
      const next: Record<string, string> = {}
      for (const p of placeholders) {
        next[p.name] = initialVariables?.[p.name] ?? prev[p.name] ?? ''
      }
      return next
    })
  }, [selected?.id, placeholders, initialVariables])

  async function render() {
    if (!selected) return
    setRendering(true)
    try {
      const doc = await templateApi.render(selected.id, {
        graphId,
        situationId,
        variables: vars,
      })
      onGenerated?.(doc)
    } finally {
      setRendering(false)
    }
  }

  return (
    <div className="flex flex-col gap-4">

      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <FileText size={14} className="text-[var(--color-accent)]" />
          <h3 className="text-sm font-semibold text-[var(--color-text)]">Шаблоны</h3>
          {loading && <Loader2 size={12} className="animate-spin text-[var(--color-text-muted)]" />}
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {templates.map(t => (
            <button
              key={t.id}
              onClick={() => setSelected(t)}
              className={cn(
                'text-left p-3 rounded-xl border transition-colors cursor-pointer',
                selected?.id === t.id
                  ? 'border-[var(--color-accent)] bg-[var(--color-accent-glow)]'
                  : 'border-[var(--color-border)] bg-[var(--color-surface)] hover:border-[var(--color-accent)]',
              )}
            >
              <div className="flex items-center gap-2 mb-1">
                <span className="text-[10px] font-mono uppercase tracking-wider text-[var(--color-accent)]">
                  {t.kind}
                </span>
                {t.systemTemplate && (
                  <Sparkles size={10} className="text-[var(--color-text-muted)]" />
                )}
              </div>
              <div className="text-sm font-medium text-[var(--color-text)]" style={{ fontFamily: "'IBM Plex Serif', serif" }}>
                {t.title}
              </div>
              {t.description && (
                <div className="mt-1 text-xs text-[var(--color-text-muted)]">{t.description}</div>
              )}
            </button>
          ))}
        </div>
      </div>

      {selected && (
        <div className="flex flex-col gap-3 p-4 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]">
          <h4 className="text-sm font-semibold text-[var(--color-text)]" style={{ fontFamily: "'IBM Plex Serif', serif" }}>
            Параметры — {selected.title}
          </h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {placeholders.map(p => (
              <label key={p.name} className="flex flex-col gap-1">
                <span className="text-[11px] font-mono uppercase tracking-wider text-[var(--color-text-muted)]">
                  {p.label}{p.required && <span className="text-[var(--color-error)]"> *</span>}
                </span>
                {p.multiline ? (
                  <textarea
                    rows={3}
                    value={vars[p.name] ?? ''}
                    onChange={e => setVars(v => ({ ...v, [p.name]: e.target.value }))}
                    className="px-3 py-2 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg)] focus:border-[var(--color-accent)] outline-none text-sm resize-y"
                  />
                ) : (
                  <input
                    value={vars[p.name] ?? ''}
                    onChange={e => setVars(v => ({ ...v, [p.name]: e.target.value }))}
                    className="h-9 px-3 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg)] focus:border-[var(--color-accent)] outline-none text-sm"
                  />
                )}
              </label>
            ))}
          </div>
          <button
            onClick={render}
            disabled={rendering}
            className="self-start inline-flex items-center gap-2 h-9 px-4 rounded-lg text-sm font-medium bg-[var(--color-accent)] text-white hover:bg-[var(--color-accent-hover)] disabled:opacity-60 transition-colors cursor-pointer"
          >
            {rendering ? <Loader2 size={14} className="animate-spin" /> : <FileText size={14} />}
            Сгенерировать документ
          </button>
        </div>
      )}
    </div>
  )
}
