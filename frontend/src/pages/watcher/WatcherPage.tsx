import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Bell, BellRing, ChevronLeft, Trash2, AlertCircle, Inbox, Clock, CheckCheck,
} from 'lucide-react'
import {
  subscriptionApi,
  type ChangeEventDTO,
  type SubscriptionDTO,
} from '@/features/subscription/api/subscriptionApi'

type Status = 'loading' | 'ready' | 'error'

export default function WatcherPage() {
  const navigate = useNavigate()
  const [subs, setSubs] = useState<SubscriptionDTO[]>([])
  const [events, setEvents] = useState<ChangeEventDTO[]>([])
  const [status, setStatus] = useState<Status>('loading')
  const [tab, setTab] = useState<'subs' | 'events'>('subs')

  const load = useCallback(async () => {
    setStatus('loading')
    try {
      const [s, e] = await Promise.all([
        subscriptionApi.list(),
        subscriptionApi.events(),
      ])
      setSubs(s)
      setEvents(e)
      setStatus('ready')
    } catch {
      setStatus('error')
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const unread = events.filter((e) => !e.acknowledged).length

  async function remove(id: string) {
    try {
      await subscriptionApi.remove(id)
      setSubs((prev) => prev.filter((s) => s.id !== id))
    } catch {  }
  }

  async function ackAll() {
    try {
      await subscriptionApi.acknowledgeAll()
      setEvents((prev) => prev.map((e) => ({ ...e, acknowledged: true })))
    } catch {  }
  }

  return (
    <div className="le-cpage">
      <div className="le-cpage__hero">
        <div className="le-cpage__hero-icon">
          {unread > 0 ? <BellRing /> : <Bell />}
        </div>
        <div className="le-cpage__hero-text">
          <button
            className="le-backlink le-backlink--mb"
            onClick={() => navigate(-1)}
          >
            <ChevronLeft /> назад
          </button>
          <div className="le-cpage__eyebrow">change_tracker</div>
          <h1 className="le-cpage__title">уведомления о поправках</h1>
          <p className="le-cpage__sub">подписки на статьи и события из adilet.zan.kz</p>
        </div>
        <div className="le-cpage__hero-actions">
          {unread > 0 && tab === 'events' && (
            <button className="le-btn le-btn--primary" onClick={ackAll}>
              <CheckCheck /> отметить всё прочитанным
            </button>
          )}
        </div>
      </div>

      <div className="le-cpage__body">
        <div className="le-chips">
          <button
            className={'le-chip' + (tab === 'subs' ? ' le-chip--on' : '')}
            onClick={() => setTab('subs')}
          >
            подписки · {subs.length}
          </button>
          <button
            className={'le-chip' + (tab === 'events' ? (unread > 0 ? ' le-chip--warn le-chip--on' : ' le-chip--on') : '')}
            onClick={() => setTab('events')}
          >
            события · {events.length}{unread > 0 ? ` (${unread} новых)` : ''}
          </button>
        </div>

        {status === 'loading' && (
          <div className="le-progress">
            <div className="le-progress__spinner" />
            <div className="le-progress__body">
              <div className="le-progress__label">загружаем...</div>
            </div>
          </div>
        )}

        {status === 'error' && (
          <div className="le-notice">
            <AlertCircle />
            <span>не удалось загрузить</span>
          </div>
        )}

        {status === 'ready' && tab === 'subs' && (
          subs.length === 0 ? (
            <EmptyState
              icon={<Bell />}
              title="нет подписок"
              sub='откройте любую статью и нажмите «отслеживать» — мы будем мониторить её и уведомлять при изменениях.'
            />
          ) : (
            <div className="le-col">
              {subs.map((s) => <SubscriptionCard key={s.id} sub={s} onRemove={() => remove(s.id)} />)}
            </div>
          )
        )}

        {status === 'ready' && tab === 'events' && (
          events.length === 0 ? (
            <EmptyState
              icon={<Inbox />}
              title="изменений пока не было"
              sub="когда любая из отслеживаемых статей обновится в адилете — событие появится здесь."
            />
          ) : (
            <div className="le-col">
              {events.map((e) => <EventCard key={e.id} event={e} />)}
            </div>
          )
        )}
      </div>
    </div>
  )
}

function SubscriptionCard({ sub, onRemove }: { sub: SubscriptionDTO; onRemove: () => void }) {
  const navigate = useNavigate()
  return (
    <article
      className="le-rcard"
      onClick={() => navigate(`/articles/${encodeURIComponent(sub.lawCode)}/${encodeURIComponent(sub.articleNumber)}`)}
    >
      <div className="le-rcard__head">
        <div className="le-rcard__lhs">
          <span className="le-rcard__code">ст. {sub.articleNumber} · {sub.lawCode}</span>
          {sub.unreadEvents > 0 && (
            <span className="le-badge le-badge--warn">{sub.unreadEvents} новых</span>
          )}
        </div>
        <button
          className="le-rcard__del"
          onClick={(e) => { e.stopPropagation(); onRemove() }}
          title="отписаться"
        >
          <Trash2 />
        </button>
      </div>
      {sub.articleTitle && <div className="le-rcard__title">{sub.articleTitle}</div>}
      <div className="le-rcard__meta">
        <span>создана {formatDateTime(sub.createdAt)}</span>
        {sub.lastCheckedAt && (
          <>
            <span className="le-rcard__sep">·</span>
            <Clock />
            <span>проверена {formatDateTime(sub.lastCheckedAt)}</span>
          </>
        )}
      </div>
    </article>
  )
}

function EventCard({ event }: { event: ChangeEventDTO }) {
  const navigate = useNavigate()
  return (
    <article
      className={'le-rcard' + (!event.acknowledged ? ' le-rcard--unread' : '')}
      onClick={() => navigate(`/articles/${encodeURIComponent(event.lawCode)}/${encodeURIComponent(event.articleNumber)}`)}
    >
      <div className="le-rcard__head">
        <div className="le-rcard__lhs">
          {!event.acknowledged && <span className="le-rcard__dot" />}
          <span className="le-rcard__code">ст. {event.articleNumber} · {event.lawCode}</span>
        </div>
        <span className="le-rcard__meta" style={{ marginTop: 0 }}>{formatDateTime(event.detectedAt)}</span>
      </div>
      <p className="le-rcard__body">
        обнаружено изменение
        {event.newAmendmentDate && <> · новая дата поправки: <b>{formatDate(event.newAmendmentDate)}</b></>}
        {event.oldAmendmentDate && <> · ранее было {formatDate(event.oldAmendmentDate)}</>}
      </p>
    </article>
  )
}

function EmptyState({ icon, title, sub }: { icon: React.ReactNode; title: string; sub: string }) {
  return (
    <div className="le-empty-hint">
      <div className="le-empty-hint__icon">{icon}</div>
      <h2 className="le-empty-hint__title">{title}</h2>
      <p className="le-empty-hint__sub">{sub}</p>
    </div>
  )
}

const MONTHS = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  if (!y || !m || !d) return iso
  const month = MONTHS[parseInt(m, 10) - 1] ?? m
  return `${parseInt(d, 10)} ${month} ${y}`
}

function formatDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  const d = date.getDate()
  const m = MONTHS[date.getMonth()] ?? ''
  const y = date.getFullYear()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${d} ${m} ${y}, ${hh}:${mm}`
}
