import type { DocumentStatusResponse } from "@/entities/document/types";

export const STATUS_LABEL: Record<string, { label: string; color: string }> = {
  UPLOADING: { label: "загрузка", color: "var(--color-warning)" },
  INDEXING: { label: "обработка", color: "var(--color-warning)" },
  READY: { label: "готов", color: "var(--color-success)" },
  ERROR: { label: "ошибка", color: "var(--color-error)" },
  DELETED: { label: "удалён", color: "var(--color-text-muted)" },
};

export function statusMeta(status?: string) {
  return STATUS_LABEL[status ?? ""] ?? { label: status ?? "—", color: "var(--color-text-muted)" };
}

export function docId(d: DocumentStatusResponse & { documentId?: string }): string {
  return d.documentId ?? d.id;
}

export function formatDate(value?: string): string {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
