import type { ReactNode } from "react";
import { Trash2 } from "lucide-react";
import { CLAUSE_TYPES, CLAUSE_TYPE_LABEL, MODALITY_LABEL, PARTIES } from "@/entities/clause/labels";
import type { PlaybookRule, RuleKind, RuleModality } from "../api/playbooksApi";
import { MONO, SANS, fieldStyle } from "./fieldStyle";
import { RULE_KINDS, RULE_KIND_HINT, RULE_KIND_LABEL, ruleError } from "../lib/options";

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1 min-w-0" style={{ fontFamily: SANS }}>
      <span style={{ fontFamily: MONO, fontSize: "10px", color: "var(--color-text-muted)" }}>{label}</span>
      {children}
    </label>
  );
}

interface Props {
  index: number;
  rule: PlaybookRule;
  readOnly?: boolean;
  showErrors: boolean;
  onChange: (rule: PlaybookRule) => void;
  onRemove: () => void;
}

export function RuleEditor({ index, rule, readOnly, showErrors, onChange, onRemove }: Props) {
  const set = (patch: Partial<PlaybookRule>) => onChange({ ...rule, ...patch });
  const error = showErrors ? ruleError(rule) : null;
  const kind = rule.kind;
  const typeOptions = CLAUSE_TYPES.filter((c) => c !== "OTHER");

  return (
    <div
      className="flex flex-col gap-2.5 rounded-xl p-3 min-w-0"
      style={{
        background: "var(--color-surface)",
        border: `1px solid ${error ? "var(--color-error)" : "var(--color-border)"}`,
      }}
    >
      <div className="flex items-center gap-2">
        <span style={{ fontFamily: MONO, fontSize: "10px", color: "var(--color-text-muted)" }}>
          правило {index + 1}
        </span>
        {!readOnly && (
          <button
            type="button"
            onClick={onRemove}
            aria-label="Удалить правило"
            title="Удалить правило"
            className="ml-auto cursor-pointer"
            style={{ color: "var(--color-text-muted)" }}
          >
            <Trash2 size={13} />
          </button>
        )}
      </div>

      <div className="grid gap-2 sm:grid-cols-2 min-w-0">
        <Field label="Тип правила">
          <select
            value={kind}
            disabled={readOnly}
            onChange={(e) => {
              const next = e.target.value as RuleKind;
              set({ kind: next, clauseType: next === "max_days" ? rule.clauseType || "PAYMENT" : rule.clauseType });
            }}
            style={fieldStyle}
          >
            {RULE_KINDS.map((k) => (
              <option key={k} value={k}>
                {RULE_KIND_LABEL[k]}
              </option>
            ))}
          </select>
        </Field>

        {kind !== "text" && (
          <Field label="Тип пункта">
            <select
              value={rule.clauseType ?? ""}
              disabled={readOnly}
              onChange={(e) => set({ clauseType: e.target.value || null })}
              style={fieldStyle}
            >
              <option value="">— выберите —</option>
              {typeOptions.map((c) => (
                <option key={c} value={c}>
                  {CLAUSE_TYPE_LABEL[c]}
                </option>
              ))}
            </select>
          </Field>
        )}

        {kind === "forbidden" && (
          <>
            <Field label="Сторона">
              <select
                value={rule.party ?? ""}
                disabled={readOnly}
                onChange={(e) => set({ party: e.target.value || null })}
                style={fieldStyle}
              >
                <option value="">любая</option>
                {PARTIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Модальность">
              <select
                value={rule.modality ?? ""}
                disabled={readOnly}
                onChange={(e) => set({ modality: (e.target.value || null) as RuleModality | null })}
                style={fieldStyle}
              >
                <option value="">любая</option>
                {Object.entries(MODALITY_LABEL).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
            </Field>
          </>
        )}

        {kind === "max_days" && (
          <Field label="Не более, дней">
            <input
              type="number"
              min={1}
              inputMode="numeric"
              value={rule.maxDays ?? ""}
              disabled={readOnly}
              onChange={(e) => set({ maxDays: e.target.value ? Number(e.target.value) : null })}
              style={fieldStyle}
            />
          </Field>
        )}
      </div>

      <Field label={kind === "text" ? "Правило" : "Описание (необязательно)"}>
        <textarea
          value={rule.text}
          disabled={readOnly}
          rows={kind === "text" ? 3 : 2}
          maxLength={1000}
          placeholder={
            kind === "text"
              ? "Например: неустойка Исполнителя не должна превышать 10% от цены договора"
              : "Как правило будет показано в отчёте"
          }
          onChange={(e) => set({ text: e.target.value })}
          style={{ ...fieldStyle, resize: "vertical" }}
        />
      </Field>

      <span style={{ fontSize: "11px", color: error ? "var(--color-error)" : "var(--color-text-muted)" }}>
        {error ?? RULE_KIND_HINT[kind]}
      </span>
    </div>
  );
}
