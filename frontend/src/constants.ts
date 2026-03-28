// ── Document status ───────────────────────────────────────────────────────────

export const STATUS_LABEL: Record<string, string> = {
  active: 'Действующий',
  outdated: 'Утратил силу',
  unknown: 'Неизвестен',
};

export const STATUS_DOT: Record<string, string> = {
  active: 'bg-gray-800',
  outdated: 'bg-gray-600',
  unknown: 'bg-gray-400',
};

export const STATUS_STYLE: Record<string, string> = {
  active: 'text-gray-800 bg-gray-100 border-gray-300',
  outdated: 'text-gray-700 bg-gray-100 border-gray-300',
  unknown: 'text-gray-500 bg-gray-100 border-gray-200',
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
  high: 'text-gray-800 bg-gray-100 border-gray-300',
  medium: 'text-gray-700 bg-gray-100 border-gray-300',
  low: 'text-gray-600 bg-gray-100 border-gray-200',
};

// ── Compare assessment ────────────────────────────────────────────────────────

export const ASSESSMENT_LABEL: Record<string, string> = {
  duplicate: 'Дублирование',
  highly_related: 'Тесно связаны',
  related: 'Связаны',
  independent: 'Независимы',
};

export const ASSESSMENT_STYLE: Record<string, string> = {
  duplicate: 'text-gray-800 bg-gray-100 border-gray-300',
  highly_related: 'text-gray-700 bg-gray-100 border-gray-300',
  related: 'text-gray-600 bg-gray-100 border-gray-200',
  independent: 'text-gray-500 bg-gray-100 border-gray-200',
};

export function similarityColor(score: number): string {
  if (score >= 0.8) return '#000000';
  if (score >= 0.6) return '#333333';
  if (score >= 0.4) return '#666666';
  return '#999999';
}
