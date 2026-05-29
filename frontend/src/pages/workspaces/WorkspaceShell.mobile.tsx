import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { GraphCanvas } from "@/widgets/graph-view/GraphCanvas";
import {
  formatRelative,
  type WorkspaceShellState,
  type WorkspaceTab,
} from "./useWorkspaceShell";
import { I } from "./wsIcons";
import { SituationsPanel } from "@/features/situation";

interface Props {
  state: WorkspaceShellState;
}

type MobileTab = WorkspaceTab | "graph";

export default function WorkspaceShellMobile({ state }: Props) {
  const {
    workspaceId,
    graphId,
    navigate,
    user,
    workspace,
    graphs,
    filteredGraphs,
    activeGraph,
    content,
    loading,
    filter,
    setFilter,
    showCreate,
    setShowCreate,
    newName,
    setNewName,
    creating,
    createGraph,
    deleteGraph,
    tab,
    setTab,
    selectedNode,
    setSelectedNode,
    removeSelected,
    q,
    setQ,
    submitted,
    setSubmitted,
    results,
    searching,
    doSearch,
    addLaw,
    fileInputRef,
    uploading,
    handleFile,
    chatInput,
    setChatInput,
    chatMsgs,
    chatLoading,
    sendChat,
    compareNodes,
    setCompareNodes,
    compareOpen,
    setCompareOpen,
    compareLoading,
    compareResult,
    toggleCompare,
    runCompare,
  } = state;

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [activeMobileTab, setActiveMobileTab] = useState<MobileTab>("graph");
  const [mobileEditSitId, setMobileEditSitId] = useState<string | null>(null);

  function openSheet(t: WorkspaceTab) {
    setTab(t);
    setActiveMobileTab(t);
  }
  function closeSheet() {
    setActiveMobileTab("graph");
    setMobileEditSitId(null);
  }

  const sheetOpen = activeMobileTab !== "graph";

  return (
    <div className="le-m-wsview">

      <div className="le-m-subhead">
        <button
          className="le-m-iconbtn"
          onClick={() => setDrawerOpen(true)}
          title="graph_views"
        >
          <I.menu />
        </button>
        <div className="le-m-subhead__title">
          {activeGraph ? activeGraph.name : (workspace?.name ?? "...")}
        </div>
        {activeGraph && content && (
          <div className="le-m-subhead__meta">
            <span>
              <b>{content.nodes.filter((n) => n.kind === "LAW").length}</b>з
            </span>
            <span className="le-canvas__sep">·</span>
            <span>
              <b>{content.nodes.filter((n) => n.kind === "DOCUMENT").length}</b>
              д
            </span>
          </div>
        )}
      </div>

      <div className="le-m-main">
        {!activeGraph ? (
          <div className="le-emptyws">
            <div className="le-emptyws__glyph">◇</div>
            <div className="le-emptyws__title">
              {graphs.length > 0 ? "выберите graph_view" : "workspace пуст"}
            </div>
            <div className="le-emptyws__sub" style={{ padding: "0 24px" }}>
              {graphs.length > 0
                ? "откройте граф из меню слева"
                : "создайте первый graph_view"}
            </div>
            <button
              className="le-btn le-btn--primary"
              onClick={() =>
                graphs.length > 0 ? setDrawerOpen(true) : setShowCreate(true)
              }
              style={{ marginTop: 16 }}
            >
              {graphs.length > 0 ? (
                <>
                  <I.menu /> открыть список
                </>
              ) : (
                <>
                  <I.plus /> create_new
                </>
              )}
            </button>
          </div>
        ) : !content ? (
          <div className="le-emptyws">
            <div className="le-emptyws__glyph">·</div>
            <div className="le-emptyws__sub">загрузка...</div>
          </div>
        ) : (
          <div className="le-m-canvas">
            <GraphCanvas
              content={content}
              onSelectNode={setSelectedNode}
              compareIds={compareNodes.map((n) => n.id)}
              onToggleCompare={toggleCompare}
              onEditSituation={(node) => {
                setTab("situations");
                setActiveMobileTab("situations");
                setMobileEditSitId(node.id);
              }}
            />

            {compareNodes.length > 0 && (
              <div className="le-m-comparebar">
                <span className="le-m-comparebar__title">
                  <I.compare /> compare
                </span>
                <div className="le-m-comparebar__slots">
                  {[0, 1].map((i) => (
                    <span
                      key={i}
                      className={
                        "le-m-comparebar__slot" +
                        (compareNodes[i] ? " le-m-comparebar__slot--on" : "")
                      }
                      title={compareNodes[i]?.label ?? ""}
                    >
                      {compareNodes[i]
                        ? compareNodes[i].label
                        : `slot_${i + 1}`}
                    </span>
                  ))}
                </div>
                <button
                  className="le-btn le-btn--primary"
                  style={{ padding: "6px 12px", fontSize: 11 }}
                  disabled={compareNodes.length !== 2}
                  onClick={runCompare}
                >
                  сравнить
                </button>
                <button
                  className="le-m-iconbtn"
                  onClick={() => setCompareNodes([])}
                  title="очистить"
                >
                  <I.close />
                </button>
              </div>
            )}

            {selectedNode && (
              <div className="le-m-selected">
                <div className="le-m-selected__main">
                  <div className="le-rpanel__eyebrow">
                    {selectedNode.kind === "LAW" ? "закон" : "документ"}
                  </div>
                  <div className="le-m-selected__title">
                    {selectedNode.label}
                  </div>
                </div>
                <button
                  className="le-m-iconbtn le-m-iconbtn--danger"
                  onClick={removeSelected}
                  title="убрать"
                >
                  <I.trash />
                </button>
                <button
                  className="le-m-iconbtn"
                  onClick={() => setSelectedNode(null)}
                  title="закрыть"
                >
                  <I.close />
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      <nav className="le-m-dock">
        <button
          className={
            "le-m-dock__btn" +
            (activeMobileTab === "graph" ? " le-m-dock__btn--on" : "")
          }
          onClick={closeSheet}
          disabled={!activeGraph}
        >
          <I.graph />
          <span>граф</span>
        </button>
        <button
          className={
            "le-m-dock__btn" +
            (activeMobileTab === "laws" ? " le-m-dock__btn--on" : "")
          }
          onClick={() => openSheet("laws")}
          disabled={!activeGraph}
        >
          <I.law />
          <span>законы</span>
        </button>
        <button
          className={
            "le-m-dock__btn" +
            (activeMobileTab === "docs" ? " le-m-dock__btn--on" : "")
          }
          onClick={() => openSheet("docs")}
          disabled={!activeGraph}
        >
          <I.doc />
          <span>док-ты</span>
        </button>
        <button
          className={
            "le-m-dock__btn" +
            (activeMobileTab === "ai" ? " le-m-dock__btn--on" : "")
          }
          onClick={() => openSheet("ai")}
          disabled={!activeGraph}
        >
          <I.ai />
          <span>ии</span>
        </button>
        <button
          className={
            "le-m-dock__btn" +
            (activeMobileTab === "situations" ? " le-m-dock__btn--on" : "")
          }
          onClick={() => openSheet("situations")}
          disabled={!activeGraph}
        >
          <span style={{ fontSize: 14 }}>✦</span>
          <span>сит.</span>
        </button>
      </nav>

      {sheetOpen && activeGraph && content && (
        <>
          <div className="le-m-sheet__backdrop" onClick={closeSheet} />
          <div className="le-m-sheet">
            <div className="le-m-sheet__grab" onClick={closeSheet} />
            <div className="le-m-sheet__head">
              <span className="le-m-sheet__title">
                {tab === "laws" && (
                  <>
                    <I.law /> Законы
                  </>
                )}
                {tab === "docs" && (
                  <>
                    <I.doc /> Документы
                  </>
                )}
                {tab === "ai" && (
                  <>
                    <I.ai /> ИИ
                  </>
                )}
                {tab === "situations" && <>✦ Ситуации</>}
              </span>
              <button
                className="le-m-iconbtn"
                onClick={closeSheet}
                title="закрыть"
              >
                <I.close />
              </button>
            </div>

            <div className="le-m-sheet__body">
              {tab === "laws" && (
                <div className="le-laws-tab">
                  <div className="le-search">
                    <I.search />
                    <input
                      placeholder="найти закон..."
                      value={q}
                      onChange={(e) => {
                        setQ(e.target.value);
                        setSubmitted(false);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") doSearch();
                      }}
                    />
                  </div>

                  {!submitted && (
                    <div className="le-rpanel__hint">
                      введите запрос и нажмите Enter
                    </div>
                  )}
                  {submitted && searching && (
                    <div className="le-rpanel__hint">поиск...</div>
                  )}
                  {submitted && !searching && results.length === 0 && (
                    <div className="le-rpanel__hint">ничего не найдено</div>
                  )}
                  {submitted && !searching && results.length > 0 && (
                    <ul className="le-results">
                      {results.map((l) => {
                        const added = content.nodes.some(
                          (n) => n.kind === "LAW" && n.code === l.code,
                        );
                        return (
                          <li key={l.code} className="le-result">
                            <div className="le-result__title">{l.title}</div>
                            <div className="le-result__meta">
                              <code>{l.code}</code>
                              {l.type && (
                                <span className="le-result__topic">
                                  {l.type}
                                </span>
                              )}
                            </div>
                            <button
                              className={
                                "le-result__add" +
                                (added ? " le-result__add--done" : "")
                              }
                              disabled={added}
                              onClick={() => addLaw(l.code, l.title)}
                            >
                              {added ? (
                                "✓ в графе"
                              ) : (
                                <>
                                  <I.plus /> добавить
                                </>
                              )}
                            </button>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              )}

              {tab === "docs" && (
                <div className="le-docs-tab">
                  <input
                    ref={fileInputRef}
                    type="file"
                    style={{ display: "none" }}
                    accept=".pdf,.docx,.txt"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) void handleFile(f);
                    }}
                  />
                  <button
                    className="le-upload"
                    disabled={uploading}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <I.upload />{" "}
                    {uploading ? "загрузка..." : "Загрузить документ"}
                  </button>
                  <p className="le-rpanel__hint">
                    После загрузки ИИ найдёт связанные законы и соединит их с
                    документом.
                  </p>

                  {content.nodes.filter((n) => n.kind === "DOCUMENT").length >
                    0 && (
                    <ul className="le-results" style={{ marginTop: 12 }}>
                      {content.nodes
                        .filter((n) => n.kind === "DOCUMENT")
                        .map((d) => (
                          <li key={d.id} className="le-result">
                            <div className="le-result__title">{d.label}</div>
                            {d.type && (
                              <div className="le-result__meta">
                                <code>{d.type}</code>
                              </div>
                            )}
                          </li>
                        ))}
                    </ul>
                  )}
                </div>
              )}

              {tab === "situations" && graphId && (
                <SituationsPanel
                  graphId={graphId}
                  initialEditId={mobileEditSitId}
                />
              )}

              {tab === "ai" && (
                <div className="le-ai-tab le-m-ai">
                  {chatMsgs.length === 0 && (
                    <>
                      <p className="le-rpanel__hint le-rpanel__hint--top">
                        Попросите ИИ собрать законы или связать документ:
                      </p>
                      <ul className="le-suggestions">
                        <li
                          onClick={() =>
                            sendChat(
                              "найди и добавь все законы про защиту прав потребителей",
                            )
                          }
                        >
                          «найди законы про защиту прав потребителей»
                        </li>
                        <li
                          onClick={() =>
                            sendChat("добавь гражданский кодекс рк")
                          }
                        >
                          «добавь гражданский кодекс рк»
                        </li>
                        <li
                          onClick={() =>
                            sendChat("свяжи мой документ с применимыми нормами")
                          }
                        >
                          «свяжи документ с нормами»
                        </li>
                      </ul>
                    </>
                  )}

                  {chatMsgs.length > 0 && (
                    <div className="le-m-chatmsgs">
                      {chatMsgs.map((m, i) => (
                        <div
                          key={i}
                          className={"le-m-chatmsg le-m-chatmsg--" + m.role}
                        >
                          {m.role === "user" ? (
                            <div style={{ whiteSpace: "pre-wrap" }}>
                              {m.content}
                            </div>
                          ) : (
                            <div className="prose-legal markdown-body">
                              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                {m.content}
                              </ReactMarkdown>
                            </div>
                          )}
                        </div>
                      ))}
                      {chatLoading && (
                        <div className="le-rpanel__hint" style={{ padding: 8 }}>
                          ИИ думает...
                        </div>
                      )}
                    </div>
                  )}

                  <div className="le-ai-input le-m-ai-input">
                    <input
                      placeholder="что добавить в граф?"
                      value={chatInput}
                      onChange={(e) => setChatInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" && !chatLoading) sendChat();
                      }}
                      disabled={chatLoading}
                    />
                    <button
                      onClick={() => sendChat()}
                      disabled={chatLoading || !chatInput.trim()}
                    >
                      <I.arrowRight />
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </>
      )}

      {drawerOpen && (
        <>
          <div
            className="le-m-drawer__backdrop"
            onClick={() => setDrawerOpen(false)}
          />
          <aside className="le-m-drawer">
            <div className="le-sidebar__head">
              <div className="le-sidebar__label">workspace_name</div>
              <div className="le-sidebar__wsname">
                {workspace?.name ?? "..."}
              </div>
              <div className="le-sidebar__wsmeta">
                {workspace ? formatRelative(workspace.createdAt) : ""}
              </div>
            </div>

            <div className="le-sidebar__actions">
              <button
                className="le-btn le-btn--primary le-btn--full"
                onClick={() => {
                  setDrawerOpen(false);
                  setShowCreate(true);
                }}
              >
                <I.plus /> create_new
              </button>
            </div>

            <div className="le-sidebar__divider">
              <span>graph_views</span>
              <span className="le-sidebar__count">
                {String(graphs.length).padStart(2, "0")}
              </span>
            </div>

            <div className="le-search le-search--inset">
              <I.search />
              <input
                placeholder="filter_views"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
              />
            </div>

            <ul className="le-gvlist" style={{ flex: 1, overflowY: "auto" }}>
              {filteredGraphs.map((g) => (
                <li
                  key={g.id}
                  className={
                    "le-gvlist__item" +
                    (g.id === graphId ? " le-gvlist__item--on" : "")
                  }
                  onClick={() => {
                    navigate(`/workspace/${workspaceId}/graph/${g.id}`);
                    setDrawerOpen(false);
                  }}
                >
                  <div className="le-gvlist__head">
                    <I.graph />
                    <span className="le-gvlist__name">{g.name}</span>
                    <button
                      className="le-gvlist__del"
                      onClick={(e) => deleteGraph(g, e)}
                      title="удалить"
                    >
                      <I.trash />
                    </button>
                  </div>
                  <div className="le-gvlist__meta">
                    <span>{g.lawCount} зак</span>
                    <span className="le-gvlist__sep">·</span>
                    <span>{g.documentCount} док</span>
                  </div>
                </li>
              ))}
              {filteredGraphs.length === 0 && !loading && (
                <li className="le-gvlist__empty">
                  {graphs.length === 0
                    ? "— пусто. создайте новый —"
                    : "— ничего не найдено —"}
                </li>
              )}
            </ul>

            <div className="le-sidebar__foot">
              <span className="le-blink">●</span> connected ·{" "}
              {user?.username ?? "anon"}
            </div>
          </aside>
        </>
      )}

      {showCreate && (
        <div
          className="le-modal-backdrop"
          onClick={() => !creating && setShowCreate(false)}
        >
          <div className="le-modal" onClick={(e) => e.stopPropagation()}>
            <div className="le-modal__head">
              <span>create_graph_view</span>
              <button
                className="le-modal__close"
                onClick={() => setShowCreate(false)}
              >
                <I.close />
              </button>
            </div>
            <form onSubmit={createGraph}>
              <div className="le-modal__body">
                <label className="le-field">
                  <span className="le-field__label">название graph_view</span>
                  <input
                    autoFocus
                    placeholder="snake_case_name"
                    value={newName}
                    onChange={(e) =>
                      setNewName(
                        e.target.value.replace(/\s+/g, "_").toLowerCase(),
                      )
                    }
                    disabled={creating}
                  />
                </label>
                <p className="le-rpanel__hint" style={{ textAlign: "left" }}>
                  Пустой граф. Добавьте законы или попросите ИИ собрать связи.
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
                <button
                  type="submit"
                  className="le-btn le-btn--primary"
                  disabled={!newName.trim() || creating}
                >
                  {creating ? "creating..." : "create"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {compareOpen && (
        <div
          className="le-modal-backdrop"
          onClick={() => !compareLoading && setCompareOpen(false)}
        >
          <div
            className="le-modal le-m-modal--full"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="le-modal__head">
              <span
                style={{
                  display: "inline-flex",
                  gap: 6,
                  alignItems: "center",
                  color: "var(--purple)",
                }}
              >
                <I.compare /> сравнение
              </span>
              <button
                className="le-modal__close"
                onClick={() => setCompareOpen(false)}
              >
                <I.close />
              </button>
            </div>
            <div
              className="le-modal__body"
              style={{
                background: "var(--bg)",
                borderBottom: "1px dashed var(--line)",
              }}
            >
              <div
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: 6,
                  fontSize: 11,
                }}
              >
                <span className="le-rpanel__code">
                  A: {compareNodes[0]?.label}
                </span>
                <span style={{ color: "var(--dim)" }}>vs</span>
                <span className="le-rpanel__code">
                  B: {compareNodes[1]?.label}
                </span>
              </div>
            </div>
            <div
              className="le-md"
              style={{ flex: 1, overflowY: "auto", padding: 14 }}
            >
              {compareLoading ? (
                <div className="le-rpanel__hint">ИИ анализирует тексты...</div>
              ) : (
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {compareResult}
                </ReactMarkdown>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
