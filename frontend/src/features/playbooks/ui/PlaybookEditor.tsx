import { useState } from "react";
import { Copy, Loader2, Plus, Save, Trash2 } from "lucide-react";
import { isTemplateId, type Playbook } from "../api/playbooksApi";
import { newRule, normalizeRule, playbookError } from "../lib/options";
import { RuleEditor } from "./RuleEditor";
import { MONO, SANS, fieldStyle } from "./fieldStyle";

interface Props {
  initial: Playbook;
  onSave: (p: Playbook) => Promise<boolean>;
  onDelete: (id: string) => Promise<boolean>;
  onCopy: (p: Playbook) => void;
}

const btn = "flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed";

export function PlaybookEditor({ initial, onSave, onDelete, onCopy }: Props) {
  const [draft, setDraft] = useState<Playbook>(initial);
  const [showErrors, setShowErrors] = useState(false);
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const readOnly = isTemplateId(draft.id);
  const isNew = !draft.id;
  const error = showErrors ? playbookError(draft) : null;

  const save = async () => {
    if (playbookError(draft)) {
      setShowErrors(true);
      return;
    }
    setSaving(true);
    const ok = await onSave({ ...draft, name: draft.name.trim(), rules: draft.rules.map(normalizeRule) });
    setSaving(false);
    if (!ok) setShowErrors(true);
  };

  const remove = async () => {
    setDeleting(true);
    const ok = await onDelete(draft.id);
    setDeleting(false);
    if (!ok) setConfirming(false);
  };

  return (
    <div className="flex flex-col gap-3 min-w-0" style={{ fontFamily: SANS }}>
      <div className="flex items-center gap-2 flex-wrap">
        <span
          style={{
            fontFamily: MONO,
            fontSize: "10px",
            letterSpacing: "0.1em",
            textTransform: "uppercase",
            color: "var(--color-accent)",
          }}
        >
          {readOnly ? "шаблон · только чтение" : isNew ? "новый набор правил" : "редактирование"}
        </span>
      </div>

      <label className="flex flex-col gap-1">
        <span style={{ fontFamily: MONO, fontSize: "10px", color: "var(--color-text-muted)" }}>Название</span>
        <input
          value={draft.name}
          disabled={readOnly}
          maxLength={200}
          placeholder="Например: Политика закупок"
          onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          style={{ ...fieldStyle, fontSize: "13px", padding: "8px 10px" }}
        />
      </label>

      <div className="flex flex-col gap-2">
        {draft.rules.map((r, i) => (
          <RuleEditor
            key={r.id}
            index={i}
            rule={r}
            readOnly={readOnly}
            showErrors={showErrors}
            onChange={(next) => setDraft({ ...draft, rules: draft.rules.map((x) => (x.id === r.id ? next : x)) })}
            onRemove={() => setDraft({ ...draft, rules: draft.rules.filter((x) => x.id !== r.id) })}
          />
        ))}
        {!readOnly && (
          <button
            type="button"
            onClick={() => setDraft({ ...draft, rules: [...draft.rules, newRule()] })}
            className="le-btn le-btn--ghost self-start"
          >
            <Plus size={12} /> Добавить правило
          </button>
        )}
      </div>

      {error && <span style={{ fontSize: "12px", color: "var(--color-error)" }}>{error}</span>}

      <div className="flex gap-2 flex-wrap items-center">
        {readOnly ? (
          <button
            type="button"
            onClick={() => onCopy(draft)}
            className="le-btn le-btn--primary"
          >
            <Copy size={12} /> Создать из шаблона
          </button>
        ) : (
          <button
            type="button"
            onClick={() => void save()}
            disabled={saving}
            className="le-btn le-btn--primary"
          >
            {saving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}
            Сохранить
          </button>
        )}

        {!readOnly && !isNew && !confirming && (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className={btn}
            style={{ color: "var(--color-error)", border: "1px solid rgba(239,68,68,0.35)" }}
          >
            <Trash2 size={12} /> Удалить
          </button>
        )}

        {confirming && (
          <div
            className="flex items-center gap-2 flex-wrap rounded-xl px-3 py-1.5"
            style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.3)" }}
          >
            <span style={{ fontSize: "12px", color: "var(--color-error)" }}>Удалить «{draft.name}»?</span>
            <button
              type="button"
              onClick={() => void remove()}
              disabled={deleting}
              className={btn}
              style={{ background: "var(--color-error)", color: "#fff" }}
            >
              {deleting ? <Loader2 size={12} className="animate-spin" /> : <Trash2 size={12} />}
              Да, удалить
            </button>
            <button
              type="button"
              onClick={() => setConfirming(false)}
              disabled={deleting}
              className={btn}
              style={{ color: "var(--color-text-muted)", border: "1px solid var(--color-border)" }}
            >
              Отмена
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
