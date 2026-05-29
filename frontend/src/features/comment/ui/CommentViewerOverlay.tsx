import { useEffect, useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { X, Trash2, Loader2 } from "lucide-react";
import type { CommentDTO } from "@/entities/comment";
import type { GraphContentResponse, UserGraphNode } from "@/entities/graph";
import { commentApi } from "@/features/comment/api/commentApi";

interface Props {
  graphId: string;
  commentId: string;
  content: GraphContentResponse | null;
  onClose: () => void;
  onFocusNode?: (node: UserGraphNode) => void;
  onDeleted?: (commentId: string) => void;
}

export function CommentViewerOverlay({
  graphId,
  commentId,
  content,
  onClose,
  onFocusNode,
  onDeleted,
}: Props) {
  const [comment, setComment] = useState<CommentDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    commentApi
      .get(graphId, commentId)
      .then((c) => {
        if (!cancelled) setComment(c);
      })
      .catch((e) => {
        console.error(e);
        if (!cancelled) setComment(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [graphId, commentId]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const { laws, articles, subject } = useMemo(() => {
    const laws: UserGraphNode[] = [];
    const articles: UserGraphNode[] = [];
    let subject: UserGraphNode | null = null;
    if (!content) return { laws, articles, subject };
    const byId = new Map<string, UserGraphNode>();
    for (const n of content.nodes) byId.set(n.id, n);
    for (const e of content.edges) {
      if (e.source !== commentId) continue;
      const target = byId.get(e.target);
      if (!target) continue;
      if (e.relationType === "REFERENCES_LAW") laws.push(target);
      else if (e.relationType === "REFERENCES_ARTICLE") articles.push(target);
      else if (e.relationType === "ANALYZES") subject = target;
    }
    return { laws, articles, subject };
  }, [content, commentId]);

  async function handleDelete() {
    if (!confirm("Удалить комментарий?")) return;
    setDeleting(true);
    try {
      await commentApi.remove(graphId, commentId);
      onDeleted?.(commentId);
      onClose();
    } catch (e) {
      console.error(e);
      setDeleting(false);
    }
  }

  const kindLabel =
    comment?.kind === "DEEP_ANALYSIS"
      ? "глубокий анализ"
      : comment?.kind === "CHAT"
        ? "диалог"
        : comment?.kind === "MANUAL"
          ? "заметка"
          : (comment?.kind ?? "анализ").toLowerCase();

  return (
    <div className="le-modal-backdrop" onClick={onClose}>
      <div
        className="le-modal"
        style={{
          width: 980,
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
            ✦ комментарий
          </span>
          <div style={{ display: "inline-flex", gap: 6, alignItems: "center" }}>
            <button
              type="button"
              onClick={handleDelete}
              disabled={deleting}
              className="le-modal__close"
              style={{ color: deleting ? "var(--dim)" : "var(--danger)" }}
              title="Удалить комментарий"
            >
              <Trash2 size={12} />
            </button>
            <button
              className="le-modal__close"
              onClick={onClose}
              title="Закрыть"
            >
              <X size={12} />
            </button>
          </div>
        </div>

        <div style={{ flex: 1, minHeight: 0 }}>
          <div className="le-cmt">
            <div className="le-cmt__main">
              <header className="le-cmt__head">
                <span className="le-cmt__eyebrow">{kindLabel}</span>
                <span className="le-cmt__title">
                  {loading ? (
                    <Loader2
                      size={14}
                      className="le-spin"
                      style={{ display: "inline-block" }}
                    />
                  ) : (
                    comment?.title || "Без названия"
                  )}
                </span>
                <span className="le-cmt__date">
                  {comment?.createdAt
                    ? new Date(comment.createdAt).toLocaleString("ru-RU")
                    : ""}
                </span>
              </header>
              <div className="le-cmt__body prose-legal markdown-body">
                {loading ? (
                  <p style={{ color: "var(--dim)", fontStyle: "italic" }}>
                    загрузка…
                  </p>
                ) : comment?.body ? (
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {comment.body}
                  </ReactMarkdown>
                ) : (
                  <p style={{ color: "var(--dim)" }}>
                    Тело комментария пустое.
                  </p>
                )}
              </div>
            </div>

            <aside className="le-cmt__rail">
              {subject && (
                <section>
                  <div className="le-cmt__rail-h">субъект</div>
                  <div className="le-cmt__rail-list" style={{ marginTop: 8 }}>
                    <button
                      type="button"
                      className="le-cmt__chip"
                      onClick={() => onFocusNode?.(subject)}
                      title="Перейти к субъекту в графе"
                    >
                      {subject.label || subject.id}
                    </button>
                  </div>
                </section>
              )}

              <section>
                <div className="le-cmt__rail-h">законы ({laws.length})</div>
                <div className="le-cmt__rail-list" style={{ marginTop: 8 }}>
                  {laws.length === 0 && (
                    <span className="le-cmt__rail-empty">
                      нет ссылок на нормы
                    </span>
                  )}
                  {laws.map((l) => (
                    <button
                      key={l.id}
                      type="button"
                      className="le-cmt__chip le-cmt__chip--law"
                      onClick={() => onFocusNode?.(l)}
                      title={l.label}
                    >
                      {l.code || l.label}
                    </button>
                  ))}
                </div>
              </section>

              <section>
                <div className="le-cmt__rail-h">статьи ({articles.length})</div>
                <div className="le-cmt__rail-list" style={{ marginTop: 8 }}>
                  {articles.length === 0 && (
                    <span className="le-cmt__rail-empty">
                      статьи не привязаны
                    </span>
                  )}
                  {articles.map((a) => {
                    const [, num] = a.id.split(":");
                    return (
                      <button
                        key={a.id}
                        type="button"
                        className="le-cmt__chip"
                        onClick={() => onFocusNode?.(a)}
                        title={a.label}
                      >
                        <span className="le-cmt__chip__num">ст. {num}</span>
                        <span
                          style={{
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                          }}
                        >
                          {a.code}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </section>
            </aside>
          </div>
        </div>
      </div>
    </div>
  );
}
