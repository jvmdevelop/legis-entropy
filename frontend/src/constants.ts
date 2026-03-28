// ── Document status ───────────────────────────────────────────────────────────

export const STATUS_LABEL: Record<string, string> = {
  active: 'Действующий',
  outdated: 'Утратил силу',
  unknown: 'Неизвестен',
};

export const STATUS_DOT: Record<string, string> = {
  active: 'bg-status-active',
  outdated: 'bg-status-inactive',
  unknown: 'bg-status-unknown',
};

export const STATUS_STYLE: Record<string, string> = {
  active: 'bg-status-active/20 text-status-active border-status-active/30',
  outdated: 'bg-status-inactive/20 text-status-inactive border-status-inactive/30',
  unknown: 'bg-status-unknown/20 text-status-unknown border-status-unknown/30',
};

// ── Issue kind ────────────────────────────────────────────────────────────────

export const ISSUE_KIND_LABEL: Record<string, string> = {
  duplication: 'Дублирование',
  contradiction: 'Противоречие',
  outdated_reference: 'Устаревшая ссылка',
  circular_reference: 'Циклические ссылки',
  amendment: 'Акт внесения изменений',
};

export const ISSUE_KIND_ICON: Record<string, string> = {
  duplication: '⊙',
  contradiction: '⚡',
  outdated_reference: '🔗',
  circular_reference: '↺',
  amendment: '✎',
};

// ── Severity ──────────────────────────────────────────────────────────────────

export const SEVERITY_LABEL: Record<string, string> = {
  high: 'Высокий',
  medium: 'Средний',
  low: 'Низкий',
};

export const SEVERITY_STYLE: Record<string, string> = {
  high: 'bg-destructive/20 text-destructive border-destructive/30',
  medium: 'bg-status-amendment/20 text-status-amendment border-status-amendment/30',
  low: 'bg-secondary text-muted-foreground border-border',
};

// ── Compare assessment ────────────────────────────────────────────────────────

export const ASSESSMENT_LABEL: Record<string, string> = {
  duplicate: 'Дублирование',
  highly_related: 'Тесно связаны',
  related: 'Связаны',
  independent: 'Независимы',
};

export const ASSESSMENT_STYLE: Record<string, string> = {
  duplicate: 'bg-destructive/20 text-destructive border-destructive/30',
  highly_related: 'bg-status-amendment/20 text-status-amendment border-status-amendment/30',
  related: 'bg-primary/20 text-primary border-primary/30',
  independent: 'bg-secondary text-muted-foreground border-border',
};

export function similarityColor(score: number): string {
  if (score >= 0.8) return 'var(--destructive)';
  if (score >= 0.6) return 'var(--status-amendment)';
  if (score >= 0.4) return 'var(--primary)';
  return 'var(--muted-foreground)';
}
