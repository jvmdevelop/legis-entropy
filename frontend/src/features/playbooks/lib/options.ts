import type { Playbook, PlaybookRule, RuleKind, RuleStatus } from "../api/playbooksApi";

export const RULE_KIND_LABEL: Record<RuleKind, string> = {
  required: "Обязательный пункт",
  forbidden: "Запрещённое условие",
  max_days: "Максимальный срок, дней",
  text: "Произвольное правило (ИИ)",
};

export const RULE_KINDS = Object.keys(RULE_KIND_LABEL) as RuleKind[];

export const RULE_KIND_HINT: Record<RuleKind, string> = {
  required: "В договоре должен быть пункт выбранного типа",
  forbidden: "Не должно быть пункта этого типа для указанной стороны и модальности",
  max_days: "Количество дней в пунктах выбранного типа не должно превышать лимит",
  text: "Правило своими словами — проверяет ИИ",
};

export const STATUS_META: Record<string, { label: string; color: string; background: string }> = {
  VIOLATED: { label: "Нарушено", color: "var(--color-error)", background: "rgba(239,68,68,0.08)" },
  OK: { label: "Соблюдено", color: "var(--color-success)", background: "rgba(16,185,129,0.08)" },
  NOT_APPLICABLE: { label: "Не применимо", color: "var(--color-text-muted)", background: "rgba(91,84,112,0.06)" },
  UNKNOWN: { label: "Не определено", color: "var(--color-text-muted)", background: "rgba(91,84,112,0.06)" },
};

export const STATUS_ORDER: RuleStatus[] = ["VIOLATED", "OK", "NOT_APPLICABLE", "UNKNOWN"];

export function newRule(kind: RuleKind = "required"): PlaybookRule {
  return {
    id: `r-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
    kind,
    text: "",
    clauseType: kind === "max_days" ? "PAYMENT" : null,
    party: null,
    modality: null,
    maxDays: null,
  };
}

export function normalizeRule(rule: PlaybookRule): PlaybookRule {
  const base = { id: rule.id, kind: rule.kind, text: rule.text.trim() };
  switch (rule.kind) {
    case "required":
      return { ...base, clauseType: rule.clauseType ?? null, party: null, modality: null, maxDays: null };
    case "forbidden":
      return {
        ...base,
        clauseType: rule.clauseType ?? null,
        party: rule.party ?? null,
        modality: rule.modality ?? null,
        maxDays: null,
      };
    case "max_days":
      return {
        ...base,
        clauseType: rule.clauseType || "PAYMENT",
        party: null,
        modality: null,
        maxDays: rule.maxDays ?? null,
      };
    default:
      return { ...base, clauseType: null, party: null, modality: null, maxDays: null };
  }
}

export function ruleError(rule: PlaybookRule): string | null {
  if ((rule.kind === "required" || rule.kind === "forbidden") && !rule.clauseType) {
    return "Выберите тип пункта";
  }
  if (rule.kind === "max_days" && !(rule.maxDays && rule.maxDays > 0)) {
    return "Укажите количество дней";
  }
  if (rule.kind === "text" && !rule.text.trim()) return "Опишите правило";
  return null;
}

export function playbookError(p: Playbook): string | null {
  if (!p.name.trim()) return "Укажите название набора правил";
  if (p.rules.length === 0) return "Добавьте хотя бы одно правило";
  return p.rules.some((r) => ruleError(r)) ? "Исправьте ошибки в правилах" : null;
}
