import { Check, Sparkles, Undo2, X } from "lucide-react";
import type { ViewerSuggestion } from "../model/useDocumentViewer";
import { MONO, SANS, verdictLabel, verdictTone } from "../lib/verdict";

interface Props {
  item: ViewerSuggestion;
  onAccept: () => void;
  onReject: () => void;
  onReset: () => void;
  onClose?: () => void;
}

const btn =
  "flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs cursor-pointer transition-colors";

export function ProposalCard({ item, onAccept, onReject, onReset, onClose }: Props) {
  const p = item.proposal;
  const tone = verdictTone(p.verdict);
  const label = verdictLabel(p.verdict);

  return (
    <div className="flex flex-col gap-2.5" style={{ fontFamily: SANS }}>
      <div
        className="flex items-center gap-1.5 flex-wrap"
        style={{ fontFamily: MONO, fontSize: "10px", letterSpacing: "0.04em" }}
      >
        {p.clause && <span style={{ color: "var(--color-text)" }}>п. {p.clause}</span>}
        {p.page ? <span style={{ color: "var(--color-text-muted)" }}>стр. {p.page}</span> : null}
        {label && <span style={{ color: tone.color }}>{label}</span>}
        {item.fromSelection && (
          <span className="flex items-center gap-1" style={{ color: "var(--color-accent)" }}>
            <Sparkles size={10} /> по выделению
          </span>
        )}
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Закрыть"
            className="ml-auto cursor-pointer"
            style={{ color: "var(--color-text-muted)" }}
          >
            <X size={14} />
          </button>
        )}
      </div>

      <div style={{ fontSize: "13px", lineHeight: 1.55, overflowWrap: "anywhere" }}>
        <span
          style={{
            textDecoration: "line-through",
            color: "var(--color-error)",
            background: "rgba(239,68,68,0.08)",
          }}
        >
          {p.original}
        </span>
        <span style={{ color: "var(--color-text-muted)" }}> → </span>
        <span style={{ color: "var(--color-success)", background: "rgba(16,185,129,0.10)" }}>
          {p.replacement}
        </span>
      </div>

      {p.reason && (
        <p style={{ fontSize: "12px", lineHeight: 1.5, color: "var(--color-text-muted)", margin: 0 }}>
          {p.reason}
        </p>
      )}
      {p.lawBasis && (
        <span style={{ fontFamily: MONO, fontSize: "11px", color: "var(--color-accent)" }}>
          {p.lawBasis}
        </span>
      )}
      {p.source === "llm" && (
        <span style={{ fontSize: "11px", color: "var(--color-warning)" }}>
          формулировка ИИ — проверьте
        </span>
      )}

      <div className="flex gap-2 flex-wrap">
        {item.status === "pending" ? (
          <>
            <button
              type="button"
              onClick={onAccept}
              className={btn}
              style={{ fontFamily: SANS, background: "var(--color-accent)", color: "#fff" }}
            >
              <Check size={12} /> Принять
            </button>
            <button
              type="button"
              onClick={onReject}
              className={btn}
              style={{
                fontFamily: SANS,
                color: "var(--color-text-muted)",
                border: "1px solid var(--color-border)",
              }}
            >
              <X size={12} /> Отклонить
            </button>
          </>
        ) : (
          <>
            <span
              className="flex items-center text-xs"
              style={{
                color: item.status === "accepted" ? "var(--color-success)" : "var(--color-text-muted)",
              }}
            >
              {item.status === "accepted" ? "Принято" : "Отклонено"}
            </span>
            <button
              type="button"
              onClick={onReset}
              className={btn}
              style={{
                fontFamily: SANS,
                color: "var(--color-accent)",
                border: "1px solid rgba(139,92,246,0.3)",
              }}
            >
              <Undo2 size={12} /> Отменить
            </button>
          </>
        )}
      </div>
    </div>
  );
}
