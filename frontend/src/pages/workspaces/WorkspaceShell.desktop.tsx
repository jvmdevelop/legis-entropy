import { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { GraphCanvas, NODE_COLOR } from "@/widgets/graph-view/GraphCanvas";
import { formatRelative, type WorkspaceShellState } from "./useWorkspaceShell";
import { I } from "./wsIcons";
import { SituationsPanel } from "@/features/situation";
import { WorkspaceVoiceCard, WorkspaceVoiceRecorder } from "@/features/voice";
import { CommentViewerOverlay } from "@/features/comment";
import { GeneratedDocumentViewer } from "@/features/template/ui/GeneratedDocumentViewer";
import { generatedDocumentApi } from "@/features/template/api/templateApi";
import type { GeneratedDocumentDTO } from "@/entities/template";

interface Props {
  state: WorkspaceShellState;
}

function SituationsOverlay({
  graphId,
  onClose,
  initialDraftOpen,
  initialEditId,
}: {
  graphId: string;
  onClose: () => void;
  initialDraftOpen?: boolean;
  initialEditId?: string | null;
}) {
  return (
    <div className="le-modal-backdrop" onClick={onClose}>
      <div
        className="le-modal"
        style={{
          width: 720,
          maxWidth: "92vw",
          maxHeight: "85vh",
          display: "flex",
          flexDirection: "column",
        }}
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
            ✦ Ситуации
          </span>
          <button className="le-modal__close" onClick={onClose}>
            <I.close />
          </button>
        </div>
        <div style={{ flex: 1, overflowY: "auto" }}>
          <SituationsPanel
            graphId={graphId}
            initialDraftOpen={initialDraftOpen}
            initialEditId={initialEditId}
          />
        </div>
      </div>
    </div>
  );
}

function HelpOverlay({ onClose }: { onClose: () => void }) {
  const shortcuts: Array<{
    label: string;
    keys: Array<{ k: string; purple?: boolean }>;
    note?: string;
  }> = [
    { label: "новая ситуация", keys: [{ k: "C", purple: true }] },
    { label: "все ситуации", keys: [{ k: "Shift" }, { k: "C", purple: true }] },
    {
      label: "глубокий анализ выделенного",
      keys: [{ k: "A", purple: true }],
      note: "doc · ситуация · ГС",
    },
    {
      label: "помощь / шорткаты",
      keys: [{ k: "Shift" }, { k: "H", purple: true }],
    },
    { label: "сравнить два узла", keys: [{ k: "Ctrl/⌘" }, { k: "клик" }] },
  ];

  const legend: Array<{
    kind: keyof typeof NODE_COLOR;
    label: string;
    meta: string;
  }> = [
    { kind: "LAW", label: "закон", meta: "НПА" },
    { kind: "ARTICLE", label: "статья", meta: "clause" },
    { kind: "DOCUMENT", label: "документ", meta: "user-doc" },
    { kind: "SITUATION", label: "ситуация", meta: "situation" },
    { kind: "VOICE", label: "голосовое", meta: "voice" },
  ];

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="le-modal-backdrop" onClick={onClose}>
      <div
        className="le-modal"
        style={{
          width: 620,
          maxWidth: "92vw",
          maxHeight: "85vh",
          display: "flex",
          flexDirection: "column",
        }}
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
            ⌘ помощь
          </span>
          <button className="le-modal__close" onClick={onClose}>
            <I.close />
          </button>
        </div>
        <div style={{ flex: 1, overflowY: "auto" }}>
          <div className="le-help">
            <section className="le-help__section">
              <header className="le-help__heading">
                <span>
                  <span className="le-help__heading-glyph">⌘</span>shortcuts
                </span>
                <span className="le-help__heading-meta">desktop</span>
              </header>
              <div className="le-help__rows">
                {shortcuts.map(({ label, keys, note }) => (
                  <div key={label} className="le-help__row">
                    <span className="le-help__row-label">
                      {label}
                      {note && (
                        <span
                          style={{
                            marginLeft: 8,
                            color: "var(--dim)",
                            fontSize: 10,
                            letterSpacing: "0.06em",
                          }}
                        >
                          {note}
                        </span>
                      )}
                    </span>
                    <span className="le-help__row-keys">
                      {keys.map((k, idx) => (
                        <span
                          key={idx}
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: 4,
                          }}
                        >
                          {idx > 0 && <span className="le-kbd__plus">+</span>}
                          <kbd
                            className={
                              "le-kbd" + (k.purple ? " le-kbd--purple" : "")
                            }
                          >
                            {k.k}
                          </kbd>
                        </span>
                      ))}
                    </span>
                  </div>
                ))}
              </div>
            </section>

            <section className="le-help__section">
              <header className="le-help__heading">
                <span>
                  <span className="le-help__heading-glyph">●</span>цвета
                </span>
                <span className="le-help__heading-meta">node legend</span>
              </header>
              <div className="le-help__legend">
                {legend.map(({ kind, label, meta }) => (
                  <span key={kind} className="le-help__legend-item">
                    <span
                      className="le-help__legend-dot"
                      style={{ background: NODE_COLOR[kind].fill }}
                    />
                    {label}
                    <span className="le-help__legend-meta">{meta}</span>
                  </span>
                ))}
              </div>
              <div className="le-help__hint">
                Сплошные линии — смысловые связи (CITES, RELATED, REFERENCES).
                Пунктир — структурные (HAS_ARTICLE, doc → закон). Красный
                пунктир — <b>CONFLICTS_WITH</b>.
              </div>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function WorkspaceShellDesktop({ state }: Props) {
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
    analyzeSelected,
    voices,
    voiceLoading,
    voiceUploading,
    voiceFileInputRef,
    uploadVoiceFile,
    playingVoiceId,
    toggleVoicePlayback,
    removeVoice,
    handleVoiceAnalyzed,
    compareNodes,
    setCompareNodes,
    compareOpen,
    setCompareOpen,
    compareLoading,
    compareResult,
    toggleCompare,
    runCompare,
  } = state;

  const [situationsOpen, setSituationsOpen] = useState(false);
  const [situationsDraftOpen, setSituationsDraftOpen] = useState(false);
  const [situationsEditId, setSituationsEditId] = useState<string | null>(null);
  const [helpOpen, setHelpOpen] = useState(false);
  const [openCommentId, setOpenCommentId] = useState<string | null>(null);
  const [generatedDoc, setGeneratedDoc] = useState<GeneratedDocumentDTO | null>(
    null,
  );
  const [docViewerOpen, setDocViewerOpen] = useState(false);

  async function openGeneratedDoc(docId: string) {
    try {
      const doc = await generatedDocumentApi.get(docId);
      setGeneratedDoc(doc);
      setDocViewerOpen(true);
    } catch (e) {
      console.error("Failed to load generated doc", e);
    }
  }

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const t = e.target as HTMLElement | null;
      if (t) {
        const tag = t.tagName;
        if (
          tag === "INPUT" ||
          tag === "TEXTAREA" ||
          tag === "SELECT" ||
          t.isContentEditable
        )
          return;
      }
      if (e.code === "KeyH" && e.shiftKey) {
        e.preventDefault();
        setHelpOpen((v) => !v);
        return;
      }
      if (e.code === "KeyC" && graphId) {
        e.preventDefault();
        setSituationsEditId(null);
        setSituationsDraftOpen(!e.shiftKey);
        setSituationsOpen(true);
        return;
      }
      if (e.code === "KeyA" && !e.shiftKey && graphId) {
        e.preventDefault();
        void analyzeSelected();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [graphId, analyzeSelected]);

  function closeSituations() {
    setSituationsOpen(false);
    setSituationsDraftOpen(false);
    setSituationsEditId(null);
  }

  return (
    <div className="le-wsview">
      <aside className="le-sidebar">
        <div className="le-sidebar__head">
          <div className="le-sidebar__label">workspace_name</div>
          <div className="le-sidebar__wsname">{workspace?.name ?? "..."}</div>
          <div className="le-sidebar__wsmeta">
            {workspace ? formatRelative(workspace.createdAt) : ""}
          </div>
        </div>

        <div className="le-sidebar__actions">
          <button
            className="le-btn le-btn--primary le-btn--full"
            onClick={() => setShowCreate(true)}
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

        <ul className="le-gvlist">
          {filteredGraphs.map((g) => (
            <li
              key={g.id}
              className={
                "le-gvlist__item" +
                (g.id === graphId ? " le-gvlist__item--on" : "")
              }
              onClick={() =>
                navigate(`/workspace/${workspaceId}/graph/${g.id}`)
              }
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

      <div className="le-wsview__main">
        {!activeGraph ? (
          <div className="le-emptyws">
            <div className="le-emptyws__glyph">◇</div>
            <div className="le-emptyws__title">
              {graphs.length > 0 ? "выберите graph_view" : "workspace пуст"}
            </div>
            <div className="le-emptyws__sub">
              {graphs.length > 0
                ? "откройте граф из списка слева, чтобы начать работу"
                : "создайте первый graph_view, чтобы добавить законы и документы"}
            </div>
            {graphs.length === 0 && (
              <button
                className="le-btn le-btn--primary"
                onClick={() => setShowCreate(true)}
                style={{ marginTop: 16 }}
              >
                <I.plus /> create_new
              </button>
            )}
          </div>
        ) : !content ? (
          <div className="le-emptyws">
            <div className="le-emptyws__glyph">·</div>
            <div className="le-emptyws__sub">загрузка graph_view...</div>
          </div>
        ) : (
          <div className="le-graphshell">
            <div className="le-canvas" style={{ position: "relative" }}>
              <GraphCanvas
                content={content}
                onSelectNode={setSelectedNode}
                compareIds={compareNodes.map((n) => n.id)}
                onToggleCompare={toggleCompare}
                onEditSituation={(node) => {
                  setSituationsDraftOpen(false);
                  setSituationsEditId(node.id);
                  setSituationsOpen(true);
                }}
                onToggleVoicePlayback={toggleVoicePlayback}
                playingVoiceId={playingVoiceId}
                onOpenComment={(node) => setOpenCommentId(node.id)}
              />

              {compareNodes.length > 0 && (
                <div
                  style={{
                    position: "absolute",
                    top: 12,
                    left: "50%",
                    transform: "translateX(-50%)",
                    zIndex: 10,
                    background: "var(--panel)",
                    border: "1px solid var(--line)",
                    borderRadius: 999,
                    padding: "6px 10px",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 8,
                    fontSize: 11,
                    boxShadow: "0 4px 16px rgba(26,29,46,0.08)",
                  }}
                >
                  <span
                    style={{
                      color: "var(--purple)",
                      display: "inline-flex",
                      alignItems: "center",
                      gap: 5,
                    }}
                  >
                    <I.compare /> compare
                  </span>
                  {[0, 1].map((i) => (
                    <span
                      key={i}
                      style={{
                        padding: "3px 8px",
                        borderRadius: 4,
                        background: compareNodes[i]
                          ? "var(--purple-tint)"
                          : "var(--bg)",
                        color: compareNodes[i] ? "var(--purple)" : "var(--dim)",
                        border:
                          "1px solid " +
                          (compareNodes[i]
                            ? "color-mix(in oklab, var(--purple) 30%, transparent)"
                            : "var(--line)"),
                        maxWidth: 160,
                        whiteSpace: "nowrap",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                      }}
                      title={compareNodes[i]?.label ?? ""}
                    >
                      {compareNodes[i]
                        ? compareNodes[i].label
                        : `slot_${i + 1}`}
                    </span>
                  ))}
                  <button
                    className="le-btn le-btn--primary"
                    style={{ padding: "4px 10px", fontSize: 11 }}
                    disabled={compareNodes.length !== 2}
                    onClick={runCompare}
                  >
                    сравнить
                  </button>
                  <button
                    className="le-modal__close"
                    onClick={() => setCompareNodes([])}
                    title="очистить"
                  >
                    <I.close />
                  </button>
                </div>
              )}
            </div>

            <aside className="le-rpanel">
              <div className="le-rpanel__tabs">
                <button
                  className={
                    "le-rpanel__tab" +
                    (tab === "laws" ? " le-rpanel__tab--on" : "")
                  }
                  onClick={() => setTab("laws")}
                >
                  <I.law /> Законы
                </button>
                <button
                  className={
                    "le-rpanel__tab" +
                    (tab === "docs" ? " le-rpanel__tab--on" : "")
                  }
                  onClick={() => setTab("docs")}
                >
                  <I.doc /> Документы
                </button>
                <button
                  className={
                    "le-rpanel__tab" +
                    (tab === "voice" ? " le-rpanel__tab--on" : "")
                  }
                  onClick={() => setTab("voice")}
                >
                  <I.mic /> ГС
                </button>
                <button
                  className={
                    "le-rpanel__tab" +
                    (tab === "ai" ? " le-rpanel__tab--on" : "")
                  }
                  onClick={() => setTab("ai")}
                >
                  <I.ai /> ИИ
                </button>
              </div>

              {selectedNode && (
                <div className="le-rpanel__selected">
                  <div className="le-rpanel__eyebrow">
                    {selectedNode.kind === "LAW" && "выбран закон"}
                    {selectedNode.kind === "DOCUMENT" && "выбран документ"}
                    {selectedNode.kind === "ARTICLE" && "выбрана статья"}
                    {selectedNode.kind === "SITUATION" && "выбрана ситуация"}
                    {selectedNode.kind === "VOICE" && "выбрано голосовое"}
                  </div>
                  <div className="le-rpanel__seltitle">
                    {selectedNode.label}
                  </div>
                  {selectedNode.code && (
                    <code className="le-rpanel__code">{selectedNode.code}</code>
                  )}
                  <div className="le-rpanel__actions">
                    {(selectedNode.kind === "DOCUMENT" ||
                      selectedNode.kind === "SITUATION" ||
                      selectedNode.kind === "VOICE") && (
                      <button
                        className="le-rpanel__analyze"
                        onClick={() => void analyzeSelected()}
                        title="Глубокий анализ выделенного (A)"
                      >
                        ✦ ИИ-анализ
                      </button>
                    )}
                    {((selectedNode.kind === "SITUATION" &&
                      selectedNode.generatedDocId) ||
                      (selectedNode.kind === "DOCUMENT" &&
                        selectedNode.type === "GENERATED")) && (
                      <button
                        className="le-rpanel__analyze"
                        style={{
                          background: "var(--purple-tint)",
                          color: "var(--purple)",
                        }}
                        onClick={() =>
                          void openGeneratedDoc(
                            selectedNode.generatedDocId ?? selectedNode.id,
                          )
                        }
                        title="Открыть сгенерированный документ"
                      >
                        ✦ Открыть документ
                      </button>
                    )}
                    {selectedNode.type !== "GENERATED" && (
                      <button
                        className="le-rpanel__remove"
                        onClick={removeSelected}
                      >
                        <I.trash /> Убрать
                      </button>
                    )}
                  </div>
                </div>
              )}

              <div className="le-rpanel__body">
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
                      После загрузки ИИ автоматически найдёт связанные законы и
                      соединит их с вашим документом в графе.
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

                {tab === "voice" && (
                  <div className="le-voice-tab">
                    <input
                      ref={voiceFileInputRef}
                      type="file"
                      style={{ display: "none" }}
                      accept="audio/*,.mp3,.m4a,.wav,.webm,.ogg,.oga"
                      onChange={(e) => {
                        const f = e.target.files?.[0];
                        if (f) void uploadVoiceFile(f);
                      }}
                    />
                    <div className="le-voice-actions">
                      <button
                        className="le-upload"
                        disabled={voiceUploading}
                        onClick={() => voiceFileInputRef.current?.click()}
                      >
                        <I.upload />{" "}
                        {voiceUploading ? "загрузка…" : "Загрузить аудио"}
                      </button>
                      <WorkspaceVoiceRecorder
                        uploading={voiceUploading}
                        onCapture={(blob, name) =>
                          void uploadVoiceFile(blob, name)
                        }
                      />
                    </div>
                    <p className="le-rpanel__hint">
                      После загрузки запускается транскрипция (Whisper) и
                      анализ. Готовое ГС появляется узлом в графе — нажмите{" "}
                      <b>A</b>, чтобы привязать к нему применимые нормы.
                    </p>

                    {voiceLoading && voices.length === 0 && (
                      <div className="le-rpanel__hint">загрузка ГС…</div>
                    )}
                    {!voiceLoading && voices.length === 0 && (
                      <div className="le-voice-empty">
                        Здесь будут ваши голосовые. Запишите с микрофона или
                        загрузите файл — система распознает речь, разделит
                        реплики и подсветит угрозы/вымогательство, если они
                        есть.
                      </div>
                    )}

                    {voices.length > 0 && (
                      <div className="le-voice-list">
                        {voices.map((v) => (
                          <WorkspaceVoiceCard
                            key={v.id}
                            message={v}
                            isPlaying={playingVoiceId === v.id}
                            onTogglePlay={() =>
                              toggleVoicePlayback({ id: v.id })
                            }
                            onDelete={() => void removeVoice(v.id)}
                            onAnalyzed={handleVoiceAnalyzed}
                          />
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {tab === "ai" && (
                  <div className="le-ai-tab">
                    <p className="le-rpanel__hint le-rpanel__hint--top">
                      Попросите ИИ собрать законы по теме или связать ваш
                      документ с нормами:
                    </p>
                    <ul className="le-suggestions">
                      <li
                        onClick={() =>
                          sendChat(
                            "найди и добавь все законы про защиту прав потребителей",
                          )
                        }
                      >
                        «найди и добавь все законы про защиту прав потребителей»
                      </li>
                      <li
                        onClick={() => sendChat("добавь гражданский кодекс рк")}
                      >
                        «добавь гражданский кодекс рк»
                      </li>
                      <li
                        onClick={() =>
                          sendChat("свяжи мой документ с применимыми нормами")
                        }
                      >
                        «свяжи мой документ с применимыми нормами»
                      </li>
                    </ul>

                    {chatMsgs.length > 0 && (
                      <div
                        style={{
                          display: "flex",
                          flexDirection: "column",
                          gap: 8,
                          marginBottom: 10,
                        }}
                      >
                        {chatMsgs.map((m, i) => (
                          <div
                            key={i}
                            style={{
                              padding: "8px 10px",
                              borderRadius: 8,
                              fontSize: 12,
                              lineHeight: 1.5,
                              background:
                                m.role === "user"
                                  ? "var(--purple-tint)"
                                  : "var(--bg)",
                              border: "1px solid var(--line)",
                              color: "var(--ink)",
                            }}
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
                          <div
                            className="le-rpanel__hint"
                            style={{ padding: 8 }}
                          >
                            ИИ думает...
                          </div>
                        )}
                      </div>
                    )}

                    <div className="le-ai-input">
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
            </aside>
          </div>
        )}
      </div>

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
                  Пустой граф. Добавьте законы из правой панели или попросите ИИ
                  собрать связи.
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

      {situationsOpen && graphId && (
        <SituationsOverlay
          graphId={graphId}
          onClose={closeSituations}
          initialDraftOpen={situationsDraftOpen}
          initialEditId={situationsEditId}
        />
      )}

      {helpOpen && <HelpOverlay onClose={() => setHelpOpen(false)} />}

      {openCommentId && graphId && (
        <CommentViewerOverlay
          graphId={graphId}
          commentId={openCommentId}
          content={content}
          onClose={() => setOpenCommentId(null)}
          onFocusNode={(node) => {
            setOpenCommentId(null);
            setSelectedNode(node);
          }}
          onDeleted={async () => {
            await state.refreshContent();
          }}
        />
      )}

      {docViewerOpen && generatedDoc && (
        <div
          className="le-modal-backdrop"
          onClick={() => setDocViewerOpen(false)}
        >
          <div
            className="le-modal"
            style={{
              width: 760,
              maxWidth: "94vw",
              maxHeight: "88vh",
              display: "flex",
              flexDirection: "column",
            }}
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
                ✦ {generatedDoc.title}
              </span>
              <button
                className="le-modal__close"
                onClick={() => setDocViewerOpen(false)}
              >
                <I.close />
              </button>
            </div>
            <div style={{ flex: 1, overflowY: "auto" }}>
              <GeneratedDocumentViewer
                document={generatedDoc}
                onDelete={() => setDocViewerOpen(false)}
                onRegenerated={(doc) => setGeneratedDoc(doc)}
              />
            </div>
          </div>
        </div>
      )}

      {compareOpen && (
        <div
          className="le-modal-backdrop"
          onClick={() => !compareLoading && setCompareOpen(false)}
        >
          <div
            className="le-modal"
            style={{
              width: 720,
              maxWidth: "92vw",
              maxHeight: "85vh",
              display: "flex",
              flexDirection: "column",
            }}
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
                  gap: 8,
                  alignItems: "center",
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
              style={{ flex: 1, overflowY: "auto", padding: 18 }}
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
