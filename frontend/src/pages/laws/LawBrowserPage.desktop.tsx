import { LAW_TYPES, type LawBrowserState } from './useLawBrowser'
import { IconBook, IconFilter, IconSearch } from './lawIcons'

interface Props {
  state: LawBrowserState
}

export default function LawBrowserPageDesktop({ state }: Props) {
  const {
    q,
    setQ,
    type,
    setType,
    allLaws,
    loading,
    submittedQuery,
    selectedCode,
    setSelectedCode,
    selected,
    filtered,
    typeCounts,
    search,
  } = state

  return (
    <div className="le-laws-page">
      <div className="le-laws-page__hero">
        <div className="le-laws-page__hero-icon">
          <IconBook />
        </div>
        <div>
          <h1 className="le-laws-page__title">Законы и кодексы</h1>
          <p className="le-laws-page__sub">
            база казахстанского законодательства · {allLaws.length} актов в выдаче
            {submittedQuery && ` · запрос: «${submittedQuery}»`}
          </p>
        </div>
      </div>

      <div className="le-laws-page__grid">
        <aside className="le-lp-filter">
          <div className="le-lp-filter__head">
            <IconFilter />
            <span>Поиск и фильтр</span>
          </div>

          <div className="le-lp-filter__section">
            <div className="le-lp-filter__label">поиск</div>
            <div className="le-search">
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
          </div>

          <div className="le-lp-filter__section">
            <div className="le-lp-filter__label">тип акта</div>
            <ul className="le-typelist">
              {LAW_TYPES.map((t) => (
                <li
                  key={t}
                  className={'le-typelist__item' + (type === t ? ' le-typelist__item--on' : '')}
                  onClick={() => setType(t)}
                >
                  <span className="le-typelist__name">{t}</span>
                  <span className="le-typelist__count">
                    {t === 'все типы' ? allLaws.length : typeCounts[t] || 0}
                  </span>
                </li>
              ))}
            </ul>
          </div>

          <div className="le-lp-filter__stat">
            <div className="le-lp-filter__label">статистика</div>
            <div className="le-lp-filter__statrow">
              <span>найдено</span>
              <span className="le-lp-filter__statval">{String(filtered.length).padStart(2, '0')}</span>
            </div>
            <div className="le-lp-filter__statrow">
              <span>в выдаче</span>
              <span className="le-lp-filter__statval le-lp-filter__statval--dim">{allLaws.length}</span>
            </div>
          </div>
        </aside>

        <section className="le-lp-results">
          <div className="le-lp-results__head">
            <span>Найденные акты</span>
            <span className="le-lp-results__count">{filtered.length}</span>
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
            <ul className="le-lp-list">
              {filtered.map((l) => (
                <li
                  key={l.code}
                  className={'le-lp-item' + (selectedCode === l.code ? ' le-lp-item--on' : '')}
                  onClick={() => setSelectedCode(selectedCode === l.code ? null : l.code)}
                >
                  <div className="le-lp-item__head">
                    <span className={'le-lp-item__type le-lp-item__type--' + l.type}>{l.type}</span>
                    <code className="le-lp-item__code">{l.code}</code>
                    {l.year && <span className="le-lp-item__year">{l.year}</span>}
                  </div>
                  <div className="le-lp-item__title">{l.title}</div>
                  {l.topic && (
                    <div className="le-lp-item__meta">
                      <span className="le-lp-item__topic">#{l.topic}</span>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>

        <aside className="le-lp-detail">
          {selected ? (
            <>
              <div className="le-lp-detail__eyebrow">выбран акт</div>
              <span className={'le-lp-item__type le-lp-item__type--' + selected.type}>{selected.type}</span>
              <h2 className="le-lp-detail__title">{selected.title}</h2>
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
                      lineHeight: 1.55,
                      whiteSpace: 'pre-wrap',
                      maxHeight: 240,
                      overflowY: 'auto',
                    }}
                  >
                    {selected.summary.length > 800
                      ? selected.summary.slice(0, 800) + '...'
                      : selected.summary}
                  </div>
                </>
              )}
            </>
          ) : (
            <div className="le-lp-detail__hint">
              <IconBook />
              <p>выберите акт слева, чтобы посмотреть подробности</p>
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}
