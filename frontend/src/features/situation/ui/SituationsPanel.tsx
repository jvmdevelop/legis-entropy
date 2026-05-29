import { useCallback, useEffect, useState } from 'react'
import { Plus, Loader2 } from 'lucide-react'
import type { SituationDTO } from '@/entities/situation'
import { situationApi } from '@/features/situation/api/situationApi'
import { SituationEditor } from './SituationEditor'

interface Props {
  graphId: string

  initialDraftOpen?: boolean

  initialEditId?: string | null
}

export function SituationsPanel({ graphId, initialDraftOpen = false, initialEditId = null }: Props) {
  const [items, setItems] = useState<SituationDTO[]>([])
  const [loading, setLoading] = useState(false)
  const [draftOpen, setDraftOpen] = useState(initialDraftOpen)
  const [editingId, setEditingId] = useState<string | null>(initialEditId)
  const [savingId, setSavingId] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setItems(await situationApi.list(graphId))
    } finally {
      setLoading(false)
    }
  }, [graphId])

  useEffect(() => { reload() }, [reload])

  async function handleCreate(patch: { title: string; body: string; plainText: string }) {
    setSavingId('__draft__')
    try {
      const created = await situationApi.create(graphId, patch)
      setItems(prev => [created, ...prev])
      setDraftOpen(false)
    } finally {
      setSavingId(null)
    }
  }

  async function handleUpdate(s: SituationDTO, patch: { title: string; body: string; plainText: string }) {
    setSavingId(s.id)
    try {
      const updated = await situationApi.update(graphId, s.id, patch)
      setItems(prev => prev.map(it => it.id === s.id ? updated : it))
      setEditingId(null)
    } finally {
      setSavingId(null)
    }
  }

  async function handleDelete(s: SituationDTO) {
    if (!confirm(`Удалить ситуацию «${s.title}»?`)) return
    await situationApi.remove(graphId, s.id)
    setItems(prev => prev.filter(it => it.id !== s.id))
    setEditingId(null)
  }

  return (
    <aside className="le-sit-panel">
      <header className="le-sit-panel__head">
        <span className="le-sit-panel__title">
          <span className="le-sit-panel__title-glyph">✦</span>
          situations
          <span className="le-sit-panel__count">
            [{String(items.length).padStart(2, '0')}]
            {loading && (
              <Loader2 size={11} style={{ marginLeft: 6, verticalAlign: '-2px' }} className="le-spin" />
            )}
          </span>
        </span>
        <button
          type="button"
          onClick={() => setDraftOpen(true)}
          className="le-sit-panel__new"
          title="Добавить ситуацию (нажмите C)"
        >
          <Plus />
          новая
        </button>
      </header>

      {draftOpen && (
        <SituationEditor
          graphId={graphId}
          situation={null}
          collapsed={false}
          saving={savingId === '__draft__'}
          onSave={handleCreate}
          onClose={() => setDraftOpen(false)}
        />
      )}

      {!loading && items.length === 0 && !draftOpen && (
        <div className="le-sit-panel__empty">
          Пока нет ситуаций. Нажмите <b>«+ новая»</b> или клавишу <b>C</b> — опишите события, и ИИ свяжет их с документами, ГС и статьями.
        </div>
      )}

      <div className="le-sit-list">
        {items.map((s, i) =>
          editingId === s.id ? (
            <SituationEditor
              key={s.id}
              graphId={graphId}
              situation={s}
              collapsed={false}
              saving={savingId === s.id}
              index={i + 1}
              onSave={patch => handleUpdate(s, patch)}
              onDelete={() => handleDelete(s)}
              onClose={() => setEditingId(null)}
            />
          ) : (
            <div key={s.id} onClick={() => setEditingId(s.id)}>
              <SituationEditor
                graphId={graphId}
                situation={s}
                collapsed
                index={i + 1}
                onSave={() => {}}
              />
            </div>
          ),
        )}
      </div>
    </aside>
  )
}
