import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AlertTriangle,
  ChevronLeft,
  FileText,
  BookOpen,
  ExternalLink,
  Filter,
  Loader2,
  AlertCircle,
} from "lucide-react";
import {
  conflictApi,
  type ConflictKind,
  type ConflictRow,
} from "@/features/conflict/api/conflictApi";

type Status = "loading" | "ready" | "error";
type FilterMode = "ALL" | ConflictKind;

export default function ConflictsPage() {
  const { graphId } = useParams<{ graphId: string }>();
  const navigate = useNavigate();

  const [rows, setRows] = useState<ConflictRow[]>([]);
  const [status, setStatus] = useState<Status>("loading");
  const [filter, setFilter] = useState<FilterMode>("ALL");
  const [minConfidence, setMinConfidence] = useState(0);

  useEffect(() => {
    if (!graphId) return;
    setStatus("loading");
    conflictApi
      .list(graphId)
      .then((data) => {
        setRows(data);
        setStatus("ready");
      })
      .catch(() => setStatus("error"));
  }, [graphId]);

  const filtered = useMemo(() => {
    return rows.filter((r) => {
      if (filter !== "ALL" && r.kind !== filter) return false;
      if (minConfidence > 0 && (r.confidence ?? 0) < minConfidence)
        return false;
      return true;
    });
  }, [rows, filter, minConfidence]);

  const stats = useMemo(() => {
    const total = rows.length;
    const articleArticle = rows.filter(
      (r) => r.kind === "ARTICLE_ARTICLE",
    ).length;
    const docArticle = rows.filter((r) => r.kind === "DOC_ARTICLE").length;
    const high = rows.filter((r) => (r.confidence ?? 0) >= 0.7).length;
    return { total, articleArticle, docArticle, high };
  }, [rows]);

  return (
    <div
      className="flex-1 flex flex-col h-full overflow-hidden"
      style={{ background: "var(--color-bg)" }}
    >
      <div
        className="shrink-0 border-b"
        style={{ borderColor: "var(--color-border)" }}
      >
        <div className="max-w-[1200px] mx-auto px-6 pt-8 pb-5">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-1.5 mb-4 cursor-pointer transition-colors"
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "10px",
              letterSpacing: "0.1em",
              color: "var(--color-text-muted)",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = "var(--color-accent)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = "var(--color-text-muted)";
            }}
          >
            <ChevronLeft size={12} /> НАЗАД
          </button>

          <div className="flex items-center justify-between flex-wrap gap-4">
            <div className="flex items-center gap-3">
              <div
                className="w-9 h-9 rounded-xl flex items-center justify-center"
                style={{
                  background: "rgba(245,158,11,0.12)",
                  border: "1px solid rgba(245,158,11,0.25)",
                }}
              >
                <AlertTriangle size={16} style={{ color: "#d97706" }} />
              </div>
              <div>
                <div
                  style={{
                    fontFamily: "'IBM Plex Mono', monospace",
                    fontSize: "10px",
                    letterSpacing: "0.12em",
                    color: "#d97706",
                    textTransform: "uppercase",
                    fontWeight: 600,
                  }}
                >
                  Конфликт-сканер
                </div>
                <h1
                  style={{
                    fontFamily: "'IBM Plex Serif', serif",
                    fontSize: "22px",
                    fontWeight: 500,
                    margin: "2px 0 0 0",
                    color: "var(--color-text)",
                    letterSpacing: "-0.01em",
                  }}
                >
                  Противоречия в графе
                </h1>
              </div>
            </div>

            {status === "ready" && rows.length > 0 && (
              <div
                className="flex items-center gap-4"
                style={{
                  fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: "10px",
                  letterSpacing: "0.06em",
                }}
              >
                <StatCell label="ВСЕГО" value={stats.total} accent />
                <StatCell label="СТ↔СТ" value={stats.articleArticle} />
                <StatCell label="ДОК↔СТ" value={stats.docArticle} />
                <StatCell label="ВЫСОКАЯ УВ." value={stats.high} />
              </div>
            )}
          </div>

          {status === "ready" && rows.length > 0 && (
            <div className="flex items-center gap-3 mt-5 flex-wrap">
              <div
                className="flex items-center gap-1"
                style={{ color: "var(--color-text-muted)" }}
              >
                <Filter size={12} />
                <span
                  style={{
                    fontFamily: "'IBM Plex Mono', monospace",
                    fontSize: "10px",
                    letterSpacing: "0.08em",
                  }}
                >
                  ТИП
                </span>
              </div>
              <FilterChip
                label="все"
                active={filter === "ALL"}
                onClick={() => setFilter("ALL")}
              />
              <FilterChip
                label="статья ↔ статья"
                active={filter === "ARTICLE_ARTICLE"}
                onClick={() => setFilter("ARTICLE_ARTICLE")}
              />
              <FilterChip
                label="документ ↔ статья"
                active={filter === "DOC_ARTICLE"}
                onClick={() => setFilter("DOC_ARTICLE")}
              />

              <span
                style={{
                  width: "1px",
                  height: "16px",
                  background: "var(--color-border)",
                  margin: "0 4px",
                }}
              />

              <span
                style={{
                  fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: "10px",
                  letterSpacing: "0.08em",
                  color: "var(--color-text-muted)",
                }}
              >
                УВ. ОТ {Math.round(minConfidence * 100)}%
              </span>
              <input
                type="range"
                min={0}
                max={100}
                step={5}
                value={Math.round(minConfidence * 100)}
                onChange={(e) =>
                  setMinConfidence(parseInt(e.target.value, 10) / 100)
                }
                className="cursor-pointer"
                style={{ accentColor: "var(--color-accent)", width: "120px" }}
              />
            </div>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-[1200px] mx-auto px-6 py-6">
          {status === "loading" && (
            <div className="flex items-center gap-2 py-12 text-[var(--color-text-muted)]">
              <Loader2 size={16} className="animate-spin" /> загрузка
              конфликтов...
            </div>
          )}
          {status === "error" && (
            <div
              className="flex items-center gap-2 px-4 py-3 rounded-xl"
              style={{
                background: "rgba(239,68,68,0.05)",
                border: "1px solid rgba(239,68,68,0.15)",
                color: "var(--color-error)",
              }}
            >
              <AlertCircle size={16} />
              <span
                style={{
                  fontFamily: "'IBM Plex Sans', sans-serif",
                  fontSize: "13px",
                }}
              >
                Не удалось загрузить
              </span>
            </div>
          )}
          {status === "ready" && rows.length === 0 && <EmptyState />}
          {status === "ready" && rows.length > 0 && filtered.length === 0 && (
            <div className="py-12 text-center">
              <div
                style={{
                  fontFamily: "'IBM Plex Sans', sans-serif",
                  fontSize: "13px",
                  color: "var(--color-text-muted)",
                }}
              >
                По выбранным фильтрам ничего нет
              </div>
            </div>
          )}
          {status === "ready" && filtered.length > 0 && (
            <div className="flex flex-col gap-3">
              {filtered.map((row, i) => (
                <ConflictCard key={i} row={row} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function ConflictCard({ row }: { row: ConflictRow }) {
  const navigate = useNavigate();
  const conf = row.confidence ?? 0;
  const confPct = Math.round(conf * 100);
  const severity = conf >= 0.7 ? "high" : conf >= 0.5 ? "mid" : "low";

  const sevColors = {
    high: {
      bg: "rgba(239,68,68,0.07)",
      border: "rgba(239,68,68,0.3)",
      text: "#dc2626",
      label: "высокая",
    },
    mid: {
      bg: "rgba(245,158,11,0.06)",
      border: "rgba(245,158,11,0.3)",
      text: "#d97706",
      label: "средняя",
    },
    low: {
      bg: "rgba(139,92,246,0.04)",
      border: "rgba(139,92,246,0.2)",
      text: "#6d28d9",
      label: "низкая",
    },
  }[severity];

  return (
    <article
      className="rounded-2xl p-5"
      style={{
        background: "var(--color-surface)",
        border: `1px solid ${sevColors.border}`,
        boxShadow:
          severity === "high" ? "0 2px 12px rgba(239,68,68,0.04)" : undefined,
      }}
    >
      <div className="flex items-center justify-between gap-3 mb-3 flex-wrap">
        <div className="flex items-center gap-2">
          <span
            className="flex items-center gap-1.5 px-2 py-0.5 rounded-md"
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "10px",
              letterSpacing: "0.06em",
              background: sevColors.bg,
              color: sevColors.text,
              fontWeight: 600,
            }}
          >
            {row.kind === "DOC_ARTICLE" ? (
              <FileText size={10} />
            ) : (
              <BookOpen size={10} />
            )}
            {row.kind === "DOC_ARTICLE" ? "ДОК ↔ СТ" : "СТ ↔ СТ"}
          </span>
          <span
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "10px",
              color: sevColors.text,
              letterSpacing: "0.04em",
            }}
          >
            УВЕРЕННОСТЬ {confPct}% · {sevColors.label}
          </span>
        </div>
        {row.extractedAt && (
          <span
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "10px",
              color: "var(--color-text-light)",
              letterSpacing: "0.04em",
            }}
          >
            {formatDateTime(row.extractedAt)}
          </span>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-3">
        <SideRef
          label="Сторона A"
          code={row.codeA}
          number={row.numberA}
          title={row.titleA}
          onOpen={
            row.codeA && row.numberA
              ? () =>
                  navigate(
                    `/articles/${encodeURIComponent(row.codeA!)}/${encodeURIComponent(row.numberA!)}`,
                  )
              : undefined
          }
        />
        {row.kind === "ARTICLE_ARTICLE" ? (
          <SideRef
            label="Сторона B"
            code={row.codeB}
            number={row.numberB}
            title={row.titleB}
            onOpen={
              row.codeB && row.numberB
                ? () =>
                    navigate(
                      `/articles/${encodeURIComponent(row.codeB!)}/${encodeURIComponent(row.numberB!)}`,
                    )
                : undefined
            }
          />
        ) : (
          <DocSide documentId={row.documentId} clauseRef={row.clauseRef} />
        )}
      </div>

      {row.reason && (
        <div
          className="rounded-xl p-3.5"
          style={{
            background: "var(--color-bg)",
            border: "1px dashed var(--color-border)",
          }}
        >
          <div
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "9px",
              letterSpacing: "0.12em",
              color: "var(--color-text-muted)",
              textTransform: "uppercase",
              marginBottom: "4px",
            }}
          >
            ПРИЧИНА
          </div>
          <p
            style={{
              fontFamily: "'IBM Plex Sans', sans-serif",
              fontSize: "13px",
              lineHeight: 1.55,
              color: "var(--color-text)",
              margin: 0,
            }}
          >
            {row.reason}
          </p>
        </div>
      )}
    </article>
  );
}

function SideRef({
  label,
  code,
  number,
  title,
  onOpen,
}: {
  label: string;
  code: string | null;
  number: string | null;
  title: string | null;
  onOpen?: () => void;
}) {
  return (
    <div
      className="rounded-xl p-3"
      style={{
        background: "var(--color-bg)",
        border: "1px solid var(--color-border)",
      }}
    >
      <div className="flex items-center justify-between gap-2 mb-1.5">
        <span
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "9px",
            letterSpacing: "0.1em",
            color: "var(--color-text-muted)",
            textTransform: "uppercase",
          }}
        >
          {label}
        </span>
        {onOpen && (
          <button
            onClick={onOpen}
            className="flex items-center gap-1 cursor-pointer transition-colors"
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "9px",
              letterSpacing: "0.08em",
              color: "var(--color-accent)",
            }}
            title="Открыть статью в Машине времени"
          >
            ОТКРЫТЬ <ExternalLink size={9} />
          </button>
        )}
      </div>
      <div
        style={{
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "11px",
          color: "var(--color-accent)",
          letterSpacing: "0.04em",
          fontWeight: 600,
        }}
      >
        ст. {number ?? "—"} · {code ?? "—"}
      </div>
      {title && (
        <div
          style={{
            fontFamily: "'IBM Plex Sans', sans-serif",
            fontSize: "12.5px",
            color: "var(--color-text)",
            marginTop: "4px",
            lineHeight: 1.4,
          }}
        >
          {title}
        </div>
      )}
    </div>
  );
}

function DocSide({
  documentId,
  clauseRef,
}: {
  documentId: string | null;
  clauseRef: string | null;
}) {
  return (
    <div
      className="rounded-xl p-3"
      style={{
        background: "var(--color-bg)",
        border: "1px solid var(--color-border)",
      }}
    >
      <div className="flex items-center justify-between gap-2 mb-1.5">
        <span
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "9px",
            letterSpacing: "0.1em",
            color: "var(--color-text-muted)",
            textTransform: "uppercase",
          }}
        >
          Документ пользователя
        </span>
      </div>
      <div
        style={{
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "11px",
          color: "var(--color-accent)",
          letterSpacing: "0.04em",
          fontWeight: 600,
        }}
      >
        {clauseRef ? `п. ${clauseRef}` : "весь документ"}
      </div>
      {documentId && (
        <div
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "10px",
            color: "var(--color-text-muted)",
            marginTop: "4px",
          }}
        >
          {documentId.slice(0, 8)}…
        </div>
      )}
    </div>
  );
}

function StatCell({
  label,
  value,
  accent = false,
}: {
  label: string;
  value: number;
  accent?: boolean;
}) {
  return (
    <div className="flex flex-col items-end">
      <span
        style={{
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "18px",
          color: accent ? "var(--color-accent)" : "var(--color-text)",
          fontWeight: 600,
          lineHeight: 1,
        }}
      >
        {value}
      </span>
      <span
        style={{
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "9px",
          letterSpacing: "0.12em",
          color: "var(--color-text-muted)",
          marginTop: "2px",
        }}
      >
        {label}
      </span>
    </div>
  );
}

function FilterChip({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className="px-3 py-1 rounded-lg cursor-pointer transition-all duration-150"
      style={{
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: "10px",
        letterSpacing: "0.06em",
        background: active
          ? "var(--color-accent-glow)"
          : "var(--color-surface)",
        border: `1px solid ${active ? "rgba(139,92,246,0.35)" : "var(--color-border)"}`,
        color: active ? "var(--color-accent)" : "var(--color-text-muted)",
      }}
    >
      {label}
    </button>
  );
}

function EmptyState() {
  return (
    <div className="py-16 flex flex-col items-center gap-3">
      <AlertTriangle size={28} style={{ color: "var(--color-text-light)" }} />
      <div
        style={{
          fontFamily: "'IBM Plex Serif', serif",
          fontSize: "16px",
          color: "var(--color-text)",
        }}
      >
        Конфликтов не найдено
      </div>
      <div
        className="max-w-[400px] text-center"
        style={{
          fontFamily: "'IBM Plex Sans', sans-serif",
          fontSize: "13px",
          color: "var(--color-text-muted)",
        }}
      >
        Граф чист. Запустите «Глубокий анализ» документа в чате — система
        автоматически пометит возможные противоречия и они появятся здесь.
      </div>
    </div>
  );
}

const MONTHS = [
  "янв",
  "фев",
  "мар",
  "апр",
  "мая",
  "июн",
  "июл",
  "авг",
  "сен",
  "окт",
  "ноя",
  "дек",
];

function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const d = date.getDate();
  const m = MONTHS[date.getMonth()] ?? "";
  const y = date.getFullYear();
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  return `${d} ${m} ${y} · ${hh}:${mm}`;
}
