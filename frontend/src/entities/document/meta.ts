import type { DocumentStatusResponse } from "./types";

export interface StatusMeta {
  label: string;
  badge: string;
}

export const STATUS_LABEL: Record<string, StatusMeta> = {
  UPLOADING: { label: "загрузка", badge: "le-badge--warn" },
  INDEXING: { label: "обработка", badge: "le-badge--warn" },
  READY: { label: "готов", badge: "le-badge--ok" },
  ERROR: { label: "ошибка", badge: "le-badge--err" },
  DELETED: { label: "удалён", badge: "le-badge--neutral" },
};

export function statusMeta(status?: string): StatusMeta {
  return STATUS_LABEL[status ?? ""] ?? { label: status ?? "—", badge: "le-badge--neutral" };
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
