import { Check, FilePen, Loader2, ScanSearch, Undo2, X } from "lucide-react";
import type { DocumentViewerModel, ViewerSuggestion } from "../model/useDocumentViewer";
import { MONO, SANS, verdictLabel, verdictTone } from "../lib/verdict";

interface Props {
  model: DocumentViewerModel;
  onFocus: (id: string) => void;
  onApply: () => void;
}

const iconBtn = "inline-flex items-center justify-center w-7 h-7 rounded-lg cursor-pointer shrink-0";

export function SuggestionsPanel({ model, onFocus, onApply }: Props) {
  const { items, summary, problems, checking, checked, applying, accepted, layout } = model;
  const missing = new Set(layout.missing);
  const pending = items.filter((s) => s.status === "pending").length;

  return (
    <aside
      data-dv-panel=""
      className="flex flex-col gap-3 rounded-2xl p-4 min-w-0"
      style={{
        fontFamily: SANS,
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
      }}
    >
      <div className="flex items-center gap-2">
        <FilePen size={14} style={{ color: "var(--color-accent)", flexShrink: 0 }} />
        <span
          style={{
            fontFamily: MONO,
            fontSize: "10px",
            letterSpacing: "0.1em",
            color: "var(--color-accent)",
            textTransform: "uppercase",
          }}
        >
          ИИ-ассистент · {items.length}
        </span>
      </div>

      <button
        type="button"
        onClick={() => void model.check()}
        disabled={checking || model.text === null}
        className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl text-xs cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
        style={{ fontFamily: SANS, background: "var(--color-accent)", color: "#fff" }}
      >
        {checking ? <Loader2 size={12} className="animate-spin" /> : <ScanSearch size={12} />}
        {checking ? "Проверяем документ…" : checked ? "Проверить ИИ ещё раз" : "Проверить ИИ"}
      </button>
      {checking && (
        <span style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
          Анализ ссылок на законы занимает 1–2 минуты. Можно продолжать читать документ.
        </span>
      )}

      {summary && (
        <div
          className="rounded-xl p-3"
          style={{
            fontSize: "12px",
            lineHeight: 1.55,
            whiteSpace: "pre-wrap",
            overflowWrap: "anywhere",
            background: "var(--color-bg, #fff)",
            border: "1px solid var(--color-border)",
            color: "var(--color-text)",
          }}
        >
          {problems !== null && (
            <div style={{ fontFamily: MONO, fontSize: "10px", color: "var(--color-text-muted)", marginBottom: 4 }}>
              проблем: {problems}
            </div>
          )}
          {summary}
        </div>
      )}

      {items.length === 0 ? (
        <p style={{ fontSize: "12px", color: "var(--color-text-muted)", margin: 0 }}>
          {checked
            ? "ИИ не нашёл, что исправить."
            : "Запустите проверку или выделите фрагмент текста, чтобы получить правку от ИИ."}
        </p>
      ) : (
        <ul className="flex flex-col gap-2 m-0 p-0" style={{ listStyle: "none" }}>
          {items.map((s) => (
            <SuggestionRow
              key={s.proposal.id}
              item={s}
              active={s.proposal.id === model.activeId}
              missing={missing.has(s.proposal.id)}
              onFocus={() => onFocus(s.proposal.id)}
              onAccept={() => model.accept(s.proposal.id)}
              onReject={() => model.reject(s.proposal.id)}
              onReset={() => model.reset(s.proposal.id)}
            />
          ))}
        </ul>
      )}

      <div className="flex flex-col gap-1.5 pt-1">
        <button
          type="button"
          onClick={onApply}
          disabled={applying || accepted.length === 0}
          className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl text-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          style={{ fontFamily: SANS, background: "var(--color-success)", color: "#fff" }}
        >
          {applying ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
          Применить принятые ({accepted.length})
        </button>
        <span style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
          {pending > 0 ? `Ожидают решения: ${pending}. ` : ""}
          Исправленный документ сохранится отдельной копией — оригинал не изменится.
        </span>
      </div>
    </aside>
  );
}

interface RowProps {
  item: ViewerSuggestion;
  active: boolean;
  missing: boolean;
  onFocus: () => void;
  onAccept: () => void;
  onReject: () => void;
  onReset: () => void;
}

function SuggestionRow({ item, active, missing, onFocus, onAccept, onReject, onReset }: RowProps) {
  const p = item.proposal;
  const tone = verdictTone(p.verdict);
  const label = verdictLabel(p.verdict);
  const dim = item.status === "rejected";

  return (
    <li
      className="flex gap-2 rounded-xl p-2.5"
      style={{
        background: "var(--color-bg, #fff)",
        border: `1px solid ${active ? tone.color : "var(--color-border)"}`,
        borderLeft: `3px solid ${item.status === "accepted" ? "var(--color-success)" : tone.color}`,
        opacity: dim ? 0.55 : 1,
      }}
    >
      <button
        type="button"
        onClick={onFocus}
        disabled={missing || dim}
        className="flex flex-col gap-1 min-w-0 flex-1 text-left cursor-pointer disabled:cursor-default"
        style={{ fontFamily: SANS }}
      >
        <span className="flex items-center gap-1.5 flex-wrap" style={{ fontFamily: MONO, fontSize: "10px" }}>
          {p.clause && <span style={{ color: "var(--color-text)" }}>п. {p.clause}</span>}
          {p.page ? <span style={{ color: "var(--color-text-muted)" }}>стр. {p.page}</span> : null}
          {label && <span style={{ color: tone.color }}>{label}</span>}
          {item.status === "accepted" && <span style={{ color: "var(--color-success)" }}>принято</span>}
          {dim && <span style={{ color: "var(--color-text-muted)" }}>отклонено</span>}
          {missing && !dim && <span style={{ color: "var(--color-error)" }}>не найдено в тексте</span>}
        </span>
        <span
          className="line-clamp-2"
          style={{ fontSize: "12px", lineHeight: 1.45, overflowWrap: "anywhere", color: "var(--color-text)" }}
        >
          <span style={{ textDecoration: "line-through", color: "var(--color-error)" }}>{p.original}</span>
          <span style={{ color: "var(--color-text-muted)" }}> → </span>
          <span style={{ color: "var(--color-success)" }}>{p.replacement}</span>
        </span>
      </button>
      <div className="flex flex-col gap-1">
        {item.status === "pending" ? (
          <>
            <button
              type="button"
              onClick={onAccept}
              aria-label="Принять"
              title="Принять"
              className={iconBtn}
              style={{ color: "var(--color-success)", background: "rgba(16,185,129,0.10)" }}
            >
              <Check size={13} />
            </button>
            <button
              type="button"
              onClick={onReject}
              aria-label="Отклонить"
              title="Отклонить"
              className={iconBtn}
              style={{ color: "var(--color-text-muted)", background: "rgba(91,84,112,0.08)" }}
            >
              <X size={13} />
            </button>
          </>
        ) : (
          <button
            type="button"
            onClick={onReset}
            aria-label="Отменить решение"
            title="Отменить решение"
            className={iconBtn}
            style={{ color: "var(--color-accent)", background: "rgba(139,92,246,0.10)" }}
          >
            <Undo2 size={13} />
          </button>
        )}
      </div>
    </li>
  );
}
