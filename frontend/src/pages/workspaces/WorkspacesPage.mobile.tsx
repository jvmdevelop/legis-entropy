import { formatRelative, type WorkspacesPageState } from './useWorkspacesPage'
import { I } from './wsIcons'

interface Props {
  state: WorkspacesPageState
}

export default function WorkspacesPageMobile({ state }: Props) {
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
    <div className="le-m-home">
      <div className="le-m-home__head">
        <div className="le-home__eyebrow">all_workspaces</div>
        <h1 className="le-m-home__title">
          workspaces
          <span className="le-home__count">{String(workspaces.length).padStart(2, '0')}</span>
        </h1>
        <div className="le-search le-m-search">
          <I.search />
          <input
            placeholder="filter_workspaces"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
      </div>

      {loading ? (
        <div className="le-m-wslist">
          {[1, 2, 3].map((i) => (
            <div key={i} className="le-m-wscard" style={{ height: 92, background: 'var(--bg)' }} />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="le-emptyws" style={{ padding: '40px 24px' }}>
          <div className="le-emptyws__glyph">◇</div>
          <div className="le-emptyws__title">
            {workspaces.length === 0 ? 'нет workspace-ов' : 'ничего не найдено'}
          </div>
          {workspaces.length === 0 && (
            <button
              className="le-btn le-btn--primary"
              onClick={() => setShowCreate(true)}
              style={{ marginTop: 16 }}
            >
              <I.plus /> create_workspace
            </button>
          )}
        </div>
      ) : (
        <ul className="le-m-wslist">
          {filtered.map((w) => (
            <li
              key={w.id}
              className="le-m-wscard"
              onClick={() => navigate(`/workspace/${w.id}`)}
            >
              <div className="le-m-wscard__icon"><I.graph /></div>
              <div className="le-m-wscard__body">
                <div className="le-m-wscard__name">{w.name}</div>
                {w.description && (
                  <div className="le-m-wscard__desc">{w.description}</div>
                )}
                <div className="le-m-wscard__date">{formatRelative(w.createdAt)}</div>
              </div>
              <button
                className="le-m-iconbtn le-m-iconbtn--danger"
                onClick={(e) => {
                  e.stopPropagation()
                  remove(w)
                }}
                title="удалить workspace"
              >
                <I.trash />
              </button>
            </li>
          ))}
        </ul>
      )}

      <button
        className="le-m-fab"
        onClick={() => setShowCreate(true)}
        title="create_workspace"
      >
        <I.plus />
      </button>

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
