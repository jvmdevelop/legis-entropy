import { MODALITY_COLOR, MODALITY_LABEL } from "@/entities/clause/labels";
import type { StructureClause } from "../api/structureApi";
import { MONO, SANS } from "../lib/verdict";

const noSelect = { userSelect: "none" as const, WebkitUserSelect: "none" as const };

export function PageDivider({ page }: { page: number }) {
  return (
    <span
      data-page-start={page}
      contentEditable={false}
      className="flex items-center gap-2 my-3"
      style={{ ...noSelect, fontFamily: MONO, fontSize: "10px", color: "var(--color-text-muted)" }}
    >
      <span className="flex-1" style={{ borderTop: "1px dashed var(--color-border)" }} />
      стр. {page}
      <span className="flex-1" style={{ borderTop: "1px dashed var(--color-border)" }} />
    </span>
  );
}

interface AnchorProps {
  index: number;
  clause: StructureClause;
  open: boolean;
  dim: boolean;
  onToggle: () => void;
}

export function ClauseAnchor({ index, clause, open, dim, onToggle }: AnchorProps) {
  const types = clause.types.filter((t) => t.code !== "OTHER");
  const modality = clause.modality ? MODALITY_LABEL[clause.modality] : null;
  const hasChips = types.length > 0 || !!modality || !!clause.party;
  const tip = clause.summary || undefined;

  return (
    <span data-clause-anchor={index} contentEditable={false} style={noSelect}>
      {hasChips && (
        <span
          className="inline-flex flex-wrap items-center gap-1 align-middle mr-1.5"
          style={{ whiteSpace: "normal", opacity: dim ? 0.35 : 1 }}
        >
          {types.map((t) => (
            <button
              key={t.code}
              type="button"
              onClick={onToggle}
              title={tip}
              className="rounded-md px-1.5 cursor-pointer"
              style={{
                fontFamily: MONO,
                fontSize: "9.5px",
                lineHeight: "16px",
                color: "var(--color-accent)",
                background: "rgba(139,92,246,0.10)",
                border: t.source === "llm" ? "1px dashed rgba(139,92,246,0.45)" : "1px solid transparent",
              }}
            >
              {t.label}
            </button>
          ))}
          {(modality || clause.party) && (
            <button
              type="button"
              onClick={onToggle}
              title={tip}
              className="rounded-md px-1.5 cursor-pointer"
              style={{
                fontFamily: MONO,
                fontSize: "9.5px",
                lineHeight: "16px",
                color: clause.modality ? MODALITY_COLOR[clause.modality] : "var(--color-text-muted)",
                border: "1px solid var(--color-border)",
              }}
            >
              {[modality, clause.party].filter(Boolean).join(" · ")}
            </button>
          )}
        </span>
      )}
      {open && clause.summary && (
        <span
          className="block rounded-xl px-3 py-2 my-1.5"
          style={{
            fontFamily: SANS,
            fontSize: "12px",
            lineHeight: 1.5,
            whiteSpace: "normal",
            color: "var(--color-text)",
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
          }}
        >
          <span style={{ fontFamily: MONO, fontSize: "10px", color: "var(--color-text-muted)" }}>
            п. {clause.number} ·{" "}
          </span>
          {clause.summary}
        </span>
      )}
    </span>
  );
}
