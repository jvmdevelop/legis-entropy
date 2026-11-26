export const MONO = "'IBM Plex Mono', monospace";
export const SANS = "'IBM Plex Sans', sans-serif";

export const VERDICT_LABEL: Record<string, string> = {
  WRONG_ARTICLE: "ошибочная ссылка",
  VIOLATION: "нарушение закона",
  REPEALED: "утративший силу акт",
  WRONG_REQUISITES: "реквизиты акта",
  IMPRECISE: "неточная ссылка",
  NOT_FOUND: "несуществующая статья",
};

export interface VerdictTone {
  color: string;
  background: string;
}

const RED: VerdictTone = { color: "var(--color-error)", background: "rgba(239,68,68,0.12)" };
const AMBER: VerdictTone = { color: "var(--color-warning)", background: "rgba(245,158,11,0.16)" };
const MUTED: VerdictTone = { color: "var(--color-text-muted)", background: "rgba(91,84,112,0.10)" };
const ACCENT: VerdictTone = { color: "var(--color-accent)", background: "rgba(139,92,246,0.12)" };

export function verdictTone(verdict?: string | null): VerdictTone {
  switch (verdict) {
    case "VIOLATION":
    case "REPEALED":
      return RED;
    case "WRONG_ARTICLE":
    case "NOT_FOUND":
    case "WRONG_REQUISITES":
      return AMBER;
    case "IMPRECISE":
      return MUTED;
    default:
      return ACCENT;
  }
}

export function verdictLabel(verdict?: string | null): string | null {
  if (!verdict) return null;
  return VERDICT_LABEL[verdict] ?? verdict;
}
