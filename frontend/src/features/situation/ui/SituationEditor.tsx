import { useEffect, useMemo, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  Bold, Italic, List, Heading1, Heading2, Quote, Eye, Pencil,
  Save, Trash2, X, Loader2,
} from 'lucide-react'
import type { SituationDTO } from '@/entities/situation'

interface Props {
  graphId: string
  situation: SituationDTO | null

  collapsed?: boolean
  saving?: boolean

  index?: number
  onSave: (patch: { title: string; body: string; plainText: string }) => Promise<void> | void
  onDelete?: () => void | Promise<void>
  onClose?: () => void
}

export function SituationEditor({
  situation, collapsed = false, saving = false, index, onSave, onDelete, onClose,
}: Props) {
  const [title, setTitle] = useState(situation?.title ?? '')
  const [body, setBody] = useState(situation?.body ?? '')
  const [mode, setMode] = useState<'edit' | 'preview'>('edit')
  const [editing, setEditing] = useState(!collapsed)

  useEffect(() => {
    setTitle(situation?.title ?? '')
    setBody(situation?.body ?? '')
  }, [situation?.id, situation?.title, situation?.body])

  const plainText = useMemo(
    () => body.replace(/[#*_>`-]/g, '').replace(/\s+/g, ' ').trim(),
    [body],
  )

  function insert(token: string, wrap = false) {
    if (wrap) {
      setBody(b => `${b}${token}текст${token}`)
    } else {
      setBody(b => (b.endsWith('\n') || b === '' ? b : b + '\n') + token + ' ')
    }
  }

  async function handleSave() {
    await onSave({ title: title.trim() || 'Без названия', body, plainText })
  }

  if (collapsed && !editing) {
    return (
      <div
        className="le-sit-card"
        onClick={() => setEditing(true)}
        title="Открыть редактор ситуации"
      >
        {typeof index === 'number' && (
          <span className="le-sit-card__index">{String(index).padStart(2, '0')}</span>
        )}
        <div className="le-sit-card__body">
          <div className="le-sit-card__title">
            {situation?.title || 'Новая ситуация'}
          </div>
          {plainText && (
            <div className="le-sit-card__preview">{plainText}</div>
          )}
        </div>
        <span className="le-sit-card__pencil">
          <Pencil />
        </span>
      </div>
    )
  }

  const eyebrowMode = situation?.id ? 'edit' : 'new'

  return (
    <div className="le-sit-editor">
      <div className="le-sit-editor__head">
        <span className="le-sit-editor__eyebrow">
          situation_{typeof index === 'number' ? String(index).padStart(2, '0') : '##'} <b>/ {eyebrowMode}</b>
        </span>
        <div className="le-sit-editor__spacer" />
        {onClose && (
          <button
            type="button"
            onClick={() => { setEditing(false); onClose() }}
            className="le-sit-editor__iconbtn"
            title="Свернуть"
          >
            <X />
          </button>
        )}
      </div>

      <div className="le-sit-editor__title">
        <input
          value={title}
          onChange={e => setTitle(e.target.value)}
          placeholder="название_ситуации"
          autoFocus={!situation?.id}
        />
      </div>

      <div className="le-sit-editor__tabs">
        <button
          type="button"
          className={'le-sit-tab' + (mode === 'edit' ? ' le-sit-tab--on' : '')}
          onClick={() => setMode('edit')}
        >
          <Pencil /> Редактировать
        </button>
        <button
          type="button"
          className={'le-sit-tab' + (mode === 'preview' ? ' le-sit-tab--on' : '')}
          onClick={() => setMode('preview')}
        >
          <Eye /> Просмотр
        </button>
      </div>

      {mode === 'edit' && (
        <div className="le-sit-editor__toolbar">
          <button type="button" className="le-sit-tool" onClick={() => insert('#')} title="Заголовок 1"><Heading1 /></button>
          <button type="button" className="le-sit-tool" onClick={() => insert('##')} title="Заголовок 2"><Heading2 /></button>
          <span className="le-sit-tool__sep" />
          <button type="button" className="le-sit-tool" onClick={() => insert('**', true)} title="Жирный"><Bold /></button>
          <button type="button" className="le-sit-tool" onClick={() => insert('_', true)} title="Курсив"><Italic /></button>
          <span className="le-sit-tool__sep" />
          <button type="button" className="le-sit-tool" onClick={() => insert('-')} title="Список"><List /></button>
          <button type="button" className="le-sit-tool" onClick={() => insert('>')} title="Цитата"><Quote /></button>
        </div>
      )}

      <div className="le-sit-editor__body">
        {mode === 'edit' ? (
          <textarea
            className="le-sit-editor__textarea"
            value={body}
            onChange={e => setBody(e.target.value)}
            placeholder="Опишите ситуацию подробно. Что произошло, где, кто участвовал, какие документы есть."
            rows={10}
          />
        ) : (
          <div className="le-sit-editor__preview prose-legal markdown-body">
            {body ? (
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{body}</ReactMarkdown>
            ) : (
              <span className="le-sit-editor__preview-empty">
                Превью пустое — переключитесь в режим редактирования.
              </span>
            )}
          </div>
        )}
      </div>

      <div className="le-sit-editor__foot">
        <span className="le-sit-editor__foot-meta">
          {plainText ? `${plainText.length} симв.` : 'черновик'}
        </span>
        <div className="le-sit-editor__foot-actions">
          {onDelete && situation?.id && (
            <button
              type="button"
              onClick={() => onDelete()}
              className="le-sit-editor__delete"
            >
              <Trash2 /> Удалить
            </button>
          )}
          <button
            type="button"
            onClick={handleSave}
            disabled={saving}
            className="le-sit-editor__save"
          >
            {saving ? <Loader2 className="le-spin" /> : <Save />}
            {situation?.id ? 'Сохранить' : 'Создать'}
          </button>
        </div>
      </div>
    </div>
  )
}
