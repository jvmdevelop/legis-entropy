import { useState } from "react";
import { Check, FilePen, Loader2, X } from "lucide-react";
import type {
  DocumentEditProposals,
  EditProposal,
} from "@/features/message/model/useStreamingMessage";
import { editsApi, type EditApplyResponse } from "../api/editsApi";

const MONO = "'IBM Plex Mono', monospace";
const SANS = "'IBM Plex Sans', sans-serif";

const VERDICT_LABEL: Record<string, string> = {
  WRONG_ARTICLE: "ошибочная ссылка",
  VIOLATION: "нарушение закона",
  REPEALED: "утративший силу акт",
  WRONG_REQUISITES: "реквизиты акта",
  IMPRECISE: "неточная ссылка",
  NOT_FOUND: "несуществующая статья",
};

interface Props {
  data: DocumentEditProposals;
  onApplied?: (documentId: string, fileName: string) => void;
}

export function DocumentEditsCard({ data, onApplied }: Props) {
  const [selected, setSelected] = useState<Set<string>>(
    () => new Set(data.proposals.map((p) => p.id)),
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<EditApplyResponse | null>(null);
  const [dismissed, setDismissed] = useState(false);

  if (dismissed) return null;

  const toggle = (id: string) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const apply = async () => {
    const chosen = data.proposals.filter((p) => selected.has(p.id));
    if (!chosen.length) return;
    setBusy(true);
    setError(null);
    try {
      const res = await editsApi.apply(data.documentId, chosen);
      setResult(res);
      if (res.documentId && res.fileName) onApplied?.(res.documentId, res.fileName);
    } catch {
      setError("Не удалось применить исправления. Попробуйте ещё раз.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="animate-fade-in rounded-2xl p-4 flex flex-col gap-3"
      style={{
        background: "var(--color-accent-glow)",
        border: "1px solid rgba(139,92,246,0.2)",
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
          Предлагаемые исправления · {data.proposals.length}
        </span>
        {!result && (
          <button
            type="button"
            onClick={() => setDismissed(true)}
            className="ml-auto cursor-pointer"
            aria-label="Скрыть исправления"
            style={{ color: "var(--color-text-muted)", background: "none", border: "none" }}
          >
            <X size={14} />
          </button>
        )}
      </div>

      {result ? (
        <ApplyResult result={result} proposals={data.proposals} />
      ) : (
        <>
          <p style={{ fontFamily: SANS, fontSize: "12px", color: "var(--color-text-muted)" }}>
            Отметьте правки, которые нужно внести. Исправленный документ сохранится
            отдельной копией — оригинал не изменится.
          </p>
          <div className="flex flex-col gap-2 max-h-[420px] overflow-y-auto pr-1">
            {data.proposals.map((p) => (
              <ProposalRow
                key={p.id}
                p={p}
                checked={selected.has(p.id)}
                onToggle={() => toggle(p.id)}
              />
            ))}
          </div>
          {error && (
            <span style={{ fontFamily: SANS, fontSize: "12px", color: "var(--color-error)" }}>
              {error}
            </span>
          )}
          <div className="flex gap-2 flex-wrap">
            <button
              type="button"
              onClick={apply}
              disabled={busy || selected.size === 0}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs cursor-pointer transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              style={{ fontFamily: SANS, background: "var(--color-accent)", color: "#fff", border: "none" }}
            >
              {busy ? <Loader2 size={11} className="animate-spin" /> : <Check size={11} />}
              Применить выбранные ({selected.size})
            </button>
            <button
              type="button"
              onClick={() =>
                setSelected(
                  selected.size === data.proposals.length
                    ? new Set()
                    : new Set(data.proposals.map((p) => p.id)),
                )
              }
              className="px-3 py-1.5 rounded-xl text-xs cursor-pointer"
              style={{
                fontFamily: SANS,
                background: "transparent",
                color: "var(--color-accent)",
                border: "1px solid rgba(139,92,246,0.3)",
              }}
            >
              {selected.size === data.proposals.length ? "Снять все" : "Выбрать все"}
            </button>
          </div>
        </>
      )}
    </div>
  );
}

function ProposalRow({
  p,
  checked,
  onToggle,
}: {
  p: EditProposal;
  checked: boolean;
  onToggle: () => void;
}) {
  return (
    <label
      className="flex gap-2.5 rounded-xl p-3 cursor-pointer"
      style={{
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
        opacity: checked ? 1 : 0.6,
      }}
    >
      <input
        type="checkbox"
        checked={checked}
        onChange={onToggle}
        className="mt-0.5 shrink-0"
        style={{ accentColor: "var(--color-accent)" }}
      />
      <div className="flex flex-col gap-1.5 min-w-0">
        <div className="flex items-center gap-1.5 flex-wrap" style={{ fontFamily: MONO, fontSize: "10px" }}>
          {p.clause && <span style={{ color: "var(--color-text)" }}>п. {p.clause}</span>}
          {p.verdict && (
            <span style={{ color: "var(--color-warning)" }}>{VERDICT_LABEL[p.verdict] ?? p.verdict}</span>
          )}
          {p.lawBasis && <span style={{ color: "var(--color-accent)" }}>{p.lawBasis}</span>}
          <span
            title={
              p.source === "llm"
                ? "Формулировку предложил ИИ — проверьте перед применением"
                : "Точная замена по правилу проверки"
            }
            style={{ color: "var(--color-text-muted)" }}
          >
            {p.source === "llm" ? "· формулировка ИИ" : "· по правилу"}
          </span>
        </div>
        <div style={{ fontFamily: SANS, fontSize: "12px", lineHeight: 1.5, wordBreak: "break-word" }}>
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
          <span style={{ color: "var(--color-success)", background: "rgba(34,197,94,0.08)" }}>
            {p.replacement}
          </span>
        </div>
        {p.reason && (
          <span style={{ fontFamily: SANS, fontSize: "11px", color: "var(--color-text-muted)" }}>
            {p.reason}
          </span>
        )}
      </div>
    </label>
  );
}

function ApplyResult({
  result,
  proposals,
}: {
  result: EditApplyResponse;
  proposals: EditProposal[];
}) {
  const clauseOf = (id: string) => proposals.find((p) => p.id === id)?.clause;
  return (
    <div className="flex flex-col gap-2" style={{ fontFamily: SANS, fontSize: "12px" }}>
      {result.documentId ? (
        <span style={{ color: "var(--color-success)" }}>
          Применено исправлений: {result.applied.length}. Создан документ «{result.fileName}» — он
          прикреплён к чату.
        </span>
      ) : (
        <span style={{ color: "var(--color-error)" }}>Ни одно исправление не применено.</span>
      )}
      {result.skipped.length > 0 && (
        <ul className="flex flex-col gap-1" style={{ color: "var(--color-text-muted)" }}>
          {result.skipped.map((s) => (
            <li key={s.id}>
              Пропущено{clauseOf(s.id) ? ` (п. ${clauseOf(s.id)})` : ""}: {s.reason}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
