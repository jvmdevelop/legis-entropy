import { formatRelative, type WorkspacesPageState } from './useWorkspacesPage'
import { I } from './wsIcons'

interface Props {
  state: WorkspacesPageState
}

export default function WorkspacesPageDesktop({ state }: Props) {
  const {
    navigate,
    workspaces,
    filtered,
    loading,
    filter,
    setFilter,
    showCreate,
    setShowCreate,
    newName,
    setNewName,
    newDesc,
    setNewDesc,
    creating,
    nameInputRef,
    create,
    remove,
  } = state

  return (
    <div className="le-home">
      <div className="le-home__head">
        <div>
          <div className="le-home__eyebrow">all_workspaces</div>
          <h1 className="le-home__title">
            workspaces <span className="le-home__count">{String(workspaces.length).padStart(2, '0')}</span>
          </h1>
        </div>
        <div className="le-home__tools">
          <div className="le-search le-search--lg">
            <I.search />
            <input
              placeholder="filter_workspaces"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          <button className="le-btn le-btn--primary" onClick={() => setShowCreate(true)}>
            <I.plus /> create_workspace
          </button>
        </div>
      </div>

      {loading ? (
        <div className="le-grid">
          {[1, 2, 3].map((i) => (
            <div key={i} className="le-card" style={{ height: 220, background: 'var(--bg)' }} />
          ))}
        </div>
      ) : (
        <div className="le-grid">
          {filtered.map((w) => (
            <div
              key={w.id}
              className="le-card"
              role="button"
              tabIndex={0}
              onClick={() => navigate(`/workspace/${w.id}`)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') navigate(`/workspace/${w.id}`)
              }}
            >
              <div className="le-card__preview">
                <div className="le-mini le-mini--empty">
                  <span>graph_view preview</span>
                </div>
              </div>
              <div className="le-card__body">
                <div className="le-card__name">{w.name}</div>
                {w.description && (
                  <div className="le-card__meta" style={{ marginBottom: 4 }}>
                    <span style={{ color: 'var(--dim)' }}>{w.description}</span>
                  </div>
                )}
                <div className="le-card__date">{formatRelative(w.createdAt)}</div>
              </div>
              <button
                className="le-card__del"
                onClick={(e) => {
                  e.stopPropagation()
                  remove(w)
                }}
                title="удалить workspace"
              >
                <I.trash />
              </button>
            </div>
          ))}

          <button className="le-card le-card--new" onClick={() => setShowCreate(true)}>
            <div className="le-card--new__inner">
              <I.plus />
              <span>create_workspace</span>
              <span className="le-card--new__hint">пустое пространство</span>
            </div>
          </button>
        </div>
      )}

      {showCreate && (
        <div className="le-modal-backdrop" onClick={() => !creating && setShowCreate(false)}>
          <div className="le-modal" onClick={(e) => e.stopPropagation()}>
            <div className="le-modal__head">
              <span>create_workspace</span>
              <button className="le-modal__close" onClick={() => setShowCreate(false)}>
                <I.close />
              </button>
            </div>
            <form onSubmit={create}>
              <div className="le-modal__body">
                <label className="le-field">
                  <span className="le-field__label">название workspace</span>
                  <input
                    ref={nameInputRef}
                    placeholder="snake_case_name"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value.replace(/\s+/g, '_').toLowerCase())}
                    disabled={creating}
                  />
                </label>
                <label className="le-field">
                  <span className="le-field__label">описание (опционально)</span>
                  <textarea
                    rows={3}
                    placeholder="кратко о проекте"
                    value={newDesc}
                    onChange={(e) => setNewDesc(e.target.value)}
                    disabled={creating}
                  />
                </label>
                <p className="le-rpanel__hint" style={{ textAlign: 'left' }}>
                  Workspace — контейнер для ваших graph_views. Один проект — один workspace.
                </p>
              </div>
              <div className="le-modal__foot">
                <button
                  type="button"
                  className="le-btn le-btn--ghost"
                  onClick={() => setShowCreate(false)}
                  disabled={creating}
                >
                  отмена
                </button>
                <button type="submit" className="le-btn le-btn--primary" disabled={!newName.trim() || creating}>
                  {creating ? 'creating...' : 'create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
