import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, Sparkles, BookOpen, AlertCircle, ExternalLink } from 'lucide-react'
import { lawSearchApi, type SemanticChunk } from '@/features/law-search/api/lawSearchApi'
import { articleApi } from '@/features/graph/api/articleApi'
import type { ArticleDTO } from '@/entities/graph'

interface SearchState {
  semantic: SemanticChunk[]
  articles: ArticleDTO[]
  loading: boolean
  error: string | null
  submittedQuery: string
}

const initialState: SearchState = {
  semantic: [],
  articles: [],
  loading: false,
  error: null,
  submittedQuery: '',
}

const EXAMPLE_QUERIES = [
  'можно ли уволить за опоздание',
  'возврат товара ненадлежащего качества',
  'налог на дивиденды для нерезидента',
  'срок исковой давности по договорам',
  'ст. 152 ТК РК',
  'обязанности учредителя ТОО',
]

export default function SearchPage() {
  const [query, setQuery] = useState('')
  const [state, setState] = useState<SearchState>(initialState)
  const debounceRef = useRef<number | null>(null)
  const reqIdRef = useRef(0)

  const runSearch = useCallback(async (q: string) => {
    const trimmed = q.trim()
    if (!trimmed) {
      setState(initialState)
      return
    }
    const myReq = ++reqIdRef.current
    setState((s) => ({ ...s, loading: true, error: null, submittedQuery: trimmed }))
    try {
      const [semantic, articles] = await Promise.allSettled([
        lawSearchApi.semantic(trimmed),
        articleApi.search(trimmed),
      ])
      if (reqIdRef.current !== myReq) return
      setState({
        semantic: semantic.status === 'fulfilled' ? semantic.value : [],
        articles: articles.status === 'fulfilled' ? articles.value : [],
        loading: false,
        error: semantic.status === 'rejected' && articles.status === 'rejected'
          ? 'Не удалось выполнить поиск. Попробуйте ещё раз.'
          : null,
        submittedQuery: trimmed,
      })
    } catch {
      if (reqIdRef.current !== myReq) return
      setState((s) => ({ ...s, loading: false, error: 'Ошибка поиска', submittedQuery: trimmed }))
    }
  }, [])

  useEffect(() => {
    if (debounceRef.current) window.clearTimeout(debounceRef.current)
    if (!query.trim()) {
      setState(initialState)
      return
    }
    debounceRef.current = window.setTimeout(() => runSearch(query), 500)
    return () => {
      if (debounceRef.current) window.clearTimeout(debounceRef.current)
    }
  }, [query, runSearch])

  const totalFound = state.semantic.length + state.articles.length
  const isEmptyQuery = !state.submittedQuery && !state.loading

  return (
    <div className="le-cpage">
      <div className="le-cpage__hero">
        <div className="le-cpage__hero-icon">
          <Sparkles />
        </div>
        <div className="le-cpage__hero-text">
          <div className="le-cpage__eyebrow">smart_search</div>
          <h1 className="le-cpage__title">умный поиск по законам</h1>
          <p className="le-cpage__sub">
            по смыслу и по ключевым словам · реестр adilet.zan.kz
          </p>
        </div>
      </div>

      <div className="le-cpage__body">
        <div className="le-search le-search--page">
          {state.loading ? <div className="le-progress__spinner" /> : <Search />}
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="можно ли уволить за опоздание / возврат товара / ст. 152 ТК РК..."
          />
          {query && (
            <button onClick={() => setQuery('')}>сбросить</button>
          )}
        </div>

        <div
          className="le-section-head"
          style={{ justifyContent: 'space-between', display: 'flex', width: '100%' }}
        >
          <span className="le-section-head__count">
            {state.submittedQuery && !state.loading
              ? <>найдено: <b style={{ color: 'var(--purple)' }}>{totalFound}</b></>
              : 'введите запрос'}
          </span>
          <span className="le-section-head__count">vector + keyword</span>
        </div>

        {state.error && (
          <div className="le-notice">
            <AlertCircle />
            <span>{state.error}</span>
          </div>
        )}

        {isEmptyQuery && <EmptyHint onPick={(text) => setQuery(text)} />}

        {state.submittedQuery && !state.loading && totalFound === 0 && !state.error && (
          <div className="le-empty-hint">
            <div className="le-empty-hint__icon">
              <Search />
            </div>
            <h2 className="le-empty-hint__title">ничего не нашли</h2>
            <p className="le-empty-hint__sub">попробуйте сформулировать иначе или коротко</p>
          </div>
        )}

        {state.submittedQuery && (state.semantic.length > 0 || state.articles.length > 0) && (
          <div className="le-twocol">
            <SemanticColumn chunks={state.semantic} loading={state.loading} />
            <ArticlesColumn articles={state.articles} loading={state.loading} />
          </div>
        )}
      </div>
    </div>
  )
}

function EmptyHint({ onPick }: { onPick: (text: string) => void }) {
  return (
    <div className="le-empty-hint">
      <div className="le-empty-hint__icon">
        <Sparkles />
      </div>
      <h2 className="le-empty-hint__title">спрашивайте человеческим языком</h2>
      <p className="le-empty-hint__sub">
        не обязательно знать номер статьи. опишите ситуацию — мы найдём нормы по смыслу
        и параллельно проверим точные совпадения по тексту.
      </p>
      <div className="le-empty-hint__examples">
        {EXAMPLE_QUERIES.map((q, i) => (
          <button
            key={i}
            onClick={() => onPick(q)}
            className="le-empty-hint__example"
          >
            {q}
          </button>
        ))}
      </div>
    </div>
  )
}

function SemanticColumn({ chunks, loading }: { chunks: SemanticChunk[]; loading: boolean }) {
  return (
    <section className="le-col">
      <div className="le-section-head le-section-head--accent">
        <Sparkles />
        <span className="le-section-head__label">по смыслу</span>
        <span className="le-section-head__count">· {chunks.length}</span>
      </div>
      {chunks.length === 0 && !loading && <div className="le-dim-note">нет семантических совпадений</div>}
      {chunks.map((c, i) => (
        <SemanticCard key={`${c.documentId ?? 'x'}-${c.chunkIndex ?? i}-${i}`} chunk={c} />
      ))}
    </section>
  )
}

function SemanticCard({ chunk }: { chunk: SemanticChunk }) {
  const preview = useMemo(() => {
    const t = (chunk.text ?? '').replace(/\s+/g, ' ').trim()
    return t.length > 320 ? t.slice(0, 320) + '…' : t
  }, [chunk.text])
  const scorePct = chunk.score != null ? Math.round(chunk.score * 100) : null

  return (
    <article className="le-rcard le-rcard--static">
      <div className="le-rcard__head">
        <div className="le-rcard__lhs">
          <span className="le-rcard__meta" style={{ marginTop: 0 }}>
            {chunk.fileName ?? 'без названия'}
            {chunk.page != null && ` · стр. ${chunk.page}`}
          </span>
        </div>
        {scorePct != null && <span className="le-score" title="similarity score">{scorePct}%</span>}
      </div>
      <p className="le-rcard__body">{preview}</p>
    </article>
  )
}

function ArticlesColumn({ articles, loading }: { articles: ArticleDTO[]; loading: boolean }) {
  return (
    <section className="le-col">
      <div className="le-section-head">
        <BookOpen />
        <span className="le-section-head__label">по тексту статей</span>
        <span className="le-section-head__count">· {articles.length}</span>
      </div>
      {articles.length === 0 && !loading && <div className="le-dim-note">нет точных совпадений</div>}
      {articles.map((a) => <ArticleCard key={a.id} article={a} />)}
    </section>
  )
}

function ArticleCard({ article }: { article: ArticleDTO }) {
  const navigate = useNavigate()
  const preview = useMemo(() => {
    const t = (article.body ?? '').replace(/\s+/g, ' ').trim()
    return t.length > 280 ? t.slice(0, 280) + '…' : t
  }, [article.body])

  return (
    <article
      className="le-rcard"
      onClick={() => navigate(`/articles/${encodeURIComponent(article.lawCode)}/${encodeURIComponent(article.number)}`)}
    >
      <div className="le-rcard__head">
        <div className="le-rcard__lhs">
          <span className="le-rcard__code">ст. {article.number} · {article.lawCode}</span>
        </div>
        <ExternalLink size={12} style={{ color: 'var(--dim)' }} />
      </div>
      {article.title && <div className="le-rcard__title">{article.title}</div>}
      {preview && <p className="le-rcard__body">{preview}</p>}
    </article>
  )
}
