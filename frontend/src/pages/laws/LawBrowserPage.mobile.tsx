import { useState } from 'react'
import { LAW_TYPES, type LawBrowserState } from './useLawBrowser'
import { IconBook, IconClose, IconFilter, IconSearch } from './lawIcons'

interface Props {
  state: LawBrowserState
}

export default function LawBrowserPageMobile({ state }: Props) {
  const {
    q,
    setQ,
    type,
    setType,
    allLaws,
    loading,
    submittedQuery,
    setSelectedCode,
    selected,
    filtered,
    typeCounts,
    search,
  } = state

  const [filterOpen, setFilterOpen] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)

  function openDetail(code: string) {
    setSelectedCode(code)
    setDetailOpen(true)
  }

  return (
    <div className="le-m-laws">
      <div className="le-m-laws__head">
        <div className="le-laws-page__hero-icon">
          <IconBook />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <h1 className="le-m-laws__title">Законы и кодексы</h1>
          <p className="le-m-laws__sub">
            {allLaws.length} актов{submittedQuery && ` · «${submittedQuery}»`}
          </p>
        </div>
      </div>

      <div className="le-m-laws__searchrow">
        <div className="le-search le-m-search" style={{ flex: 1 }}>
          <IconSearch />
          <input
            placeholder="название или код..."
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void search(q.trim())
            }}
          />
        </div>
        <button
          className={'le-m-iconbtn le-m-iconbtn--bordered' + (type !== 'все типы' ? ' le-m-iconbtn--on' : '')}
          onClick={() => setFilterOpen(true)}
          title="фильтры"
        >
          <IconFilter />
        </button>
      </div>

      {type !== 'все типы' && (
        <div className="le-m-laws__chip" onClick={() => setType('все типы')}>
          {type} <IconClose />
        </div>
      )}

      <div className="le-m-laws__count">
        найдено <b>{String(filtered.length).padStart(2, '0')}</b>
      </div>

      {loading ? (
        <div className="le-lp-empty">
          <div className="le-lp-empty__title">загрузка...</div>
        </div>
      ) : filtered.length === 0 ? (
        <div className="le-lp-empty">
          <div className="le-lp-empty__glyph">
            <IconBook />
          </div>
          <div className="le-lp-empty__title">ничего не найдено</div>
          <div className="le-lp-empty__sub">попробуйте изменить запрос или сбросить фильтр</div>
        </div>
      ) : (
        <ul className="le-m-lp-list">
          {filtered.map((l) => (
            <li
              key={l.code}
              className="le-m-lp-item"
              onClick={() => openDetail(l.code)}
            >
              <div className="le-lp-item__head">
                <span className={'le-lp-item__type le-lp-item__type--' + l.type}>{l.type}</span>
                {l.year && <span className="le-lp-item__year">{l.year}</span>}
              </div>
              <div className="le-lp-item__title">{l.title}</div>
              <div className="le-lp-item__meta">
                <code className="le-lp-item__code">{l.code}</code>
              </div>
            </li>
          ))}
        </ul>
      )}

      {filterOpen && (
        <>
          <div className="le-m-sheet__backdrop" onClick={() => setFilterOpen(false)} />
          <div className="le-m-sheet le-m-sheet--auto">
            <div className="le-m-sheet__grab" onClick={() => setFilterOpen(false)} />
            <div className="le-m-sheet__head">
              <span className="le-m-sheet__title">
                <IconFilter /> Фильтр
              </span>
              <button className="le-m-iconbtn" onClick={() => setFilterOpen(false)}>
                <IconClose />
              </button>
            </div>
            <div className="le-m-sheet__body">
              <div className="le-lp-filter__label">тип акта</div>
              <ul className="le-typelist">
                {LAW_TYPES.map((t) => (
                  <li
                    key={t}
                    className={'le-typelist__item' + (type === t ? ' le-typelist__item--on' : '')}
                    onClick={() => {
                      setType(t)
                      setFilterOpen(false)
                    }}
                  >
                    <span className="le-typelist__name">{t}</span>
                    <span className="le-typelist__count">
                      {t === 'все типы' ? allLaws.length : typeCounts[t] || 0}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </>
      )}

      {detailOpen && selected && (
        <>
          <div className="le-m-sheet__backdrop" onClick={() => setDetailOpen(false)} />
          <div className="le-m-sheet">
            <div className="le-m-sheet__grab" onClick={() => setDetailOpen(false)} />
            <div className="le-m-sheet__head">
              <span className="le-m-sheet__title">выбран акт</span>
              <button className="le-m-iconbtn" onClick={() => setDetailOpen(false)}>
                <IconClose />
              </button>
            </div>
            <div className="le-m-sheet__body">
              <span className={'le-lp-item__type le-lp-item__type--' + selected.type}>{selected.type}</span>
              <h2 className="le-lp-detail__title" style={{ marginTop: 10 }}>{selected.title}</h2>
              <div className="le-lp-detail__codes">
                <code className="le-lp-item__code">{selected.code}</code>
                {selected.year && <span className="le-lp-detail__year">{selected.year} г.</span>}
              </div>
              <div className="le-lp-detail__divider" />
              {selected.topic && (
                <div className="le-lp-detail__row">
                  <span className="le-lp-detail__rowlabel">тема</span>
                  <span className="le-lp-detail__rowval">#{selected.topic}</span>
                </div>
              )}
              <div className="le-lp-detail__row">
                <span className="le-lp-detail__rowlabel">статус</span>
                <span className="le-lp-detail__rowval">
                  <span className="le-blink">●</span> {selected.status?.toLowerCase() ?? 'действует'}
                </span>
              </div>
              {selected.summary && (
                <>
                  <div className="le-lp-detail__divider" />
                  <div
                    style={{
                      fontSize: 12,
                      color: 'var(--dim)',
                      lineHeight: 1.6,
                      whiteSpace: 'pre-wrap',
                    }}
                  >
                    {selected.summary}
                  </div>
                </>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
