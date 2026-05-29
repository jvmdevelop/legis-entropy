import { useEffect, useRef, useState } from "react";
import {
  X,
  Upload,
  FileText,
  Trash2,
  RefreshCw,
  Loader2,
  File,
} from "lucide-react";
import { useDocumentStore } from "@/features/document/model/useDocumentStore";
import { Spinner } from "@/shared/ui/Spinner";
import { cn } from "@/shared/lib/cn";
import type {
  DocumentStatusResponse,
  DocumentStatus,
} from "@/entities/document/types";

interface DocumentsPanelProps {
  onClose: () => void;
}

export function DocumentsPanel({ onClose }: DocumentsPanelProps) {
  const {
    documents,
    isLoading,
    isUploading,
    uploadProgress,
    selectedDocumentId,
    fetchDocuments,
    uploadDocument,
    deleteDocument,
    reindexDocument,
    selectDocument,
  } = useDocumentStore();

  const [isDragging, setDragging] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fetchDocuments();
  }, [fetchDocuments]);

  function onDragOver(e: React.DragEvent) {
    e.preventDefault();
    setDragging(true);
  }
  function onDragLeave() {
    setDragging(false);
  }
  function onDrop(e: React.DragEvent) {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) handleUpload(file);
  }

  async function handleUpload(file: File) {
    try {
      await uploadDocument(file);
    } catch {

    }
  }

  function onFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) handleUpload(file);
    e.target.value = "";
  }

  async function handleDelete(id: string) {
    setDeletingId(id);
    try {
      await deleteDocument(id);
    } finally {
      setDeletingId(null);
    }
  }

  const activeDoc = selectedDocumentId
    ? documents.find((d) => d.id === selectedDocumentId)
    : null;

  return (
    <div
      className="h-full flex overflow-hidden animate-slide-right"
      style={{ background: "var(--color-bg)" }}
    >

      <div className="flex-1 flex flex-col min-w-0 p-5 lg:p-5 md:p-4 sm:p-3 xs:p-2">

        <div className="flex items-end justify-between mb-5 lg:mb-5 md:mb-4 sm:mb-3 xs:mb-3">
          <div>
            <h2
              style={{
                fontFamily: "'IBM Plex Serif', serif",
                fontSize: "20px",
                fontWeight: 500,
                color: "var(--color-text)",
                margin: 0,
                letterSpacing: "-0.01em",
              }}
            >
              Документы
            </h2>
            <div
              className="label-mono mt-1 lg:block md:hidden sm:hidden xs:hidden"
              style={{ letterSpacing: "0.1em", fontSize: "9px" }}
            >
              {documents.filter((d) => d.status === "READY").length}{" "}
              проиндексировано
              {documents.filter(
                (d) => d.status === "INDEXING" || d.status === "UPLOADING",
              ).length > 0 && (
                <>
                  {" "}
                  ·{" "}
                  {
                    documents.filter(
                      (d) =>
                        d.status === "INDEXING" || d.status === "UPLOADING",
                    ).length
                  }{" "}
                  обрабатывается
                </>
              )}
              {documents.filter((d) => d.status === "ERROR").length > 0 && (
                <>
                  {" "}
                  · {documents.filter((d) => d.status === "ERROR").length}{" "}
                  ошибка
                </>
              )}
            </div>
            <div
              className="label-mono mt-1 lg:hidden md:block sm:block xs:block"
              style={{ letterSpacing: "0.1em", fontSize: "9px" }}
            >
              {documents.filter((d) => d.status === "READY").length} готово
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => !isUploading && fileRef.current?.click()}
              className="flex items-center gap-2 h-8 px-4 lg:px-4 md:px-3 sm:px-2 xs:px-2 rounded-md text-white text-xs font-medium transition-colors cursor-pointer"
              style={{
                fontFamily: "'IBM Plex Sans', sans-serif",
                background: "var(--color-accent)",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = "var(--color-accent-hover)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = "var(--color-accent)";
              }}
            >
              <Upload size={14} />{" "}
              <span className="lg:inline md:hidden sm:hidden xs:hidden">
                Загрузить
              </span>
            </button>
            <button
              onClick={onClose}
              className="w-8 h-8 flex items-center justify-center rounded-md text-[var(--color-text-muted)] hover:text-[var(--color-text)] hover:bg-[var(--color-surface-2)] transition-colors cursor-pointer"
            >
              <X size={16} />
            </button>
          </div>
        </div>

        <div
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          onDrop={onDrop}
          onClick={() => !isUploading && fileRef.current?.click()}
          className={cn(
            "relative flex flex-col items-center justify-center mb-4 py-5 px-4 text-center cursor-pointer transition-colors",
            "rounded-md",
            isDragging
              ? "bg-[var(--color-accent-glow)]"
              : "bg-[var(--color-surface)] hover:bg-[var(--color-surface-2)]",
            isUploading && "pointer-events-none opacity-70",
          )}
          style={{
            border: isDragging
              ? "1px dashed var(--color-accent)"
              : "1px dashed var(--color-border)",
          }}
        >
          {isUploading ? (
            <div className="flex flex-col items-center gap-3 w-full">
              <Loader2
                size={20}
                className="text-[var(--color-accent)] animate-spin"
              />
              <p
                className="label-mono"
                style={{ letterSpacing: "0.1em", fontSize: "9px" }}
              >
                Загрузка...
              </p>
              <div
                className="w-full max-w-[160px] rounded overflow-hidden"
                style={{ height: "3px", background: "var(--color-surface-3)" }}
              >
                <div
                  style={{
                    width: `${uploadProgress}%`,
                    height: "100%",
                    background: "var(--color-accent)",
                    transition: "width 0.2s ease-out",
                  }}
                />
              </div>
              <p className="label-mono" style={{ fontSize: "9px" }}>
                {uploadProgress}%
              </p>
            </div>
          ) : (
            <>
              <p
                style={{
                  fontFamily: "'IBM Plex Serif', serif",
                  fontSize: "14px",
                  color: "var(--color-text)",
                  marginBottom: "4px",
                }}
              >
                {isDragging
                  ? "Отпустите для загрузки"
                  : "Перетащите PDF, .docx или транскрипты сюда"}
              </p>
              <p
                className="label-mono"
                style={{ letterSpacing: "0.1em", fontSize: "9px" }}
              >
                или нажмите для выбора
              </p>
            </>
          )}
        </div>

        <input
          ref={fileRef}
          type="file"
          accept=".pdf,.txt,.docx,.doc"
          className="hidden"
          onChange={onFileChange}
        />

        <div className="flex-1 overflow-y-auto flex flex-col gap-1.5 pr-1">
          {isLoading && (
            <div className="flex justify-center py-10">
              <Spinner size="md" />
            </div>
          )}

          {!isLoading && documents.length === 0 && (
            <div className="flex flex-col items-center justify-center py-12 gap-3 text-center">
              <div
                className="w-12 h-12 rounded-md flex items-center justify-center"
                style={{ background: "var(--color-surface-2)" }}
              >
                <File size={18} style={{ color: "var(--color-text-muted)" }} />
              </div>
              <p
                className="label-mono"
                style={{ letterSpacing: "0.1em", fontSize: "10px" }}
              >
                Нет документов
              </p>
            </div>
          )}

          {documents.map((doc) => (
            <DocumentRow
              key={doc.id}
              doc={doc}
              isSelected={doc.id === selectedDocumentId}
              isDeleting={deletingId === doc.id}
              onSelect={() =>
                selectDocument(doc.id === selectedDocumentId ? null : doc.id)
              }
              onDelete={() => handleDelete(doc.id)}
              onReindex={() => reindexDocument(doc.id)}
            />
          ))}
        </div>
      </div>

      <aside
        className="w-[280px] shrink-0 border-l border-[var(--color-border)] overflow-auto p-5 lg:block md:hidden sm:hidden xs:hidden"
        style={{ background: "var(--color-surface)" }}
      >
        <div
          className="label-mono mb-2"
          style={{ letterSpacing: "0.1em", fontSize: "9px" }}
        >
          Превью
        </div>
        {activeDoc ? (
          <>
            <h3
              className="mb-4"
              style={{
                fontFamily: "'IBM Plex Serif', serif",
                fontSize: "14px",
                color: "var(--color-text)",
                fontWeight: 500,
                lineHeight: 1.4,
              }}
            >
              {activeDoc.fileName}
            </h3>

            <div
              className="mb-5 relative rounded-md border border-[var(--color-border)]"
              style={{ aspectRatio: "8.5/11", background: "var(--color-bg)" }}
            >
              <div className="absolute inset-4 flex flex-col gap-2">
                <div
                  className="rounded-sm"
                  style={{
                    width: "45%",
                    height: "8px",
                    background: "var(--color-surface-2)",
                  }}
                />
                <div style={{ height: "10px" }} />
                {Array.from({ length: 8 }).map((_, idx) => (
                  <div
                    key={idx}
                    className="rounded-sm"
                    style={{
                      width: `${90 - (idx % 4) * 12}%`,
                      height: "5px",
                      background: "var(--color-surface-2)",
                      opacity: 0.7,
                    }}
                  />
                ))}
              </div>
            </div>

            <div
              className="label-mono mb-2"
              style={{ letterSpacing: "0.1em", fontSize: "9px" }}
            >
              Метаданные
            </div>
            <dl
              style={{
                fontFamily: "'IBM Plex Mono', monospace",
                fontSize: "10px",
                color: "var(--color-text-muted)",
                display: "grid",
                gridTemplateColumns: "70px 1fr",
                gap: "6px 10px",
                letterSpacing: "0.04em",
                margin: 0,
              }}
            >
              <dt>СТАТУС</dt>
              <dd
                style={{
                  margin: 0,
                  color: statusColor(activeDoc.status),
                  textTransform: "uppercase",
                  fontWeight: 600,
                }}
              >
                {activeDoc.status}
              </dd>
              {activeDoc.chunkCount != null && activeDoc.chunkCount > 0 && (
                <>
                  <dt>ФРАГМЕНТЫ</dt>
                  <dd style={{ margin: 0, color: "var(--color-text)" }}>
                    {activeDoc.chunkCount} встроено
                  </dd>
                </>
              )}
              {activeDoc.errorMessage && (
                <>
                  <dt>ОШИБКА</dt>
                  <dd style={{ margin: 0, color: "var(--color-error)" }}>
                    {activeDoc.errorMessage}
                  </dd>
                </>
              )}
            </dl>

            <div className="flex flex-col gap-2 mt-6">
              <button
                onClick={() => reindexDocument(activeDoc.id)}
                className="flex items-center justify-center gap-2 h-8 px-4 text-xs rounded-md transition-colors cursor-pointer"
                style={{
                  background: "var(--color-bg)",
                  border: "1px solid var(--color-border)",
                  color: "var(--color-text-muted)",
                  fontFamily: "'IBM Plex Mono', monospace",
                  letterSpacing: "0.08em",
                  textTransform: "uppercase",
                  fontSize: "9px",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.color = "var(--color-text)";
                  e.currentTarget.style.borderColor = "var(--color-accent)";
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.color = "var(--color-text-muted)";
                  e.currentTarget.style.borderColor = "var(--color-border)";
                }}
              >
                <RefreshCw size={12} /> Переиндексировать
              </button>
              <button
                onClick={() => handleDelete(activeDoc.id)}
                className="flex items-center justify-center gap-2 h-8 px-4 text-xs rounded-md transition-colors cursor-pointer"
                style={{
                  background: "rgba(239,68,68,0.05)",
                  border: "1px solid rgba(239,68,68,0.1)",
                  color: "var(--color-error)",
                  fontFamily: "'IBM Plex Mono', monospace",
                  letterSpacing: "0.08em",
                  textTransform: "uppercase",
                  fontSize: "9px",
                }}
              >
                <Trash2 size={12} /> Удалить
              </button>
            </div>
          </>
        ) : (
          <div className="flex flex-col items-center justify-center h-48 text-center">
            <div
              className="w-10 h-10 rounded-md flex items-center justify-center mb-3"
              style={{ background: "var(--color-surface-2)" }}
            >
              <FileText
                size={18}
                style={{ color: "var(--color-text-light)" }}
              />
            </div>
            <p
              className="label-mono"
              style={{ letterSpacing: "0.1em", fontSize: "9px" }}
            >
              Выберите документ
            </p>
          </div>
        )}
      </aside>
    </div>
  );
}

function DocumentRow({
  doc,
  isSelected,
  isDeleting,
  onSelect,
  onDelete,
  onReindex,
}: {
  doc: DocumentStatusResponse;
  isSelected: boolean;
  isDeleting: boolean;
  onSelect(): void;
  onDelete(): void;
  onReindex(): void;
}) {
  return (
    <div
      onClick={onSelect}
      className={cn(
        "flex items-center gap-3 px-3 py-2.5 rounded-md cursor-pointer transition-colors group",
        isSelected
          ? "bg-[var(--color-surface-2)]"
          : "bg-[var(--color-bg)] hover:bg-[var(--color-surface)]",
        isDeleting && "opacity-40 pointer-events-none",
      )}
      style={{ border: "1px solid var(--color-border)" }}
    >

      <div
        className="w-8 h-8 rounded-md flex items-center justify-center shrink-0"
        style={{ background: statusBg(doc.status) }}
      >
        {doc.status === "INDEXING" || doc.status === "UPLOADING" ? (
          <Loader2
            size={14}
            className="animate-spin"
            style={{ color: "var(--color-warning)" }}
          />
        ) : (
          <FileText size={14} style={{ color: statusColor(doc.status) }} />
        )}
      </div>

      <div className="flex-1 min-w-0">
        <p
          className="font-medium text-[var(--color-text)] truncate leading-snug"
          style={{
            fontFamily: "'IBM Plex Sans', sans-serif",
            fontSize: "12px",
          }}
        >
          {doc.fileName}
        </p>
        <div className="flex items-center gap-2 mt-1">
          {doc.status === "INDEXING" || doc.status === "UPLOADING" ? (
            <div className="flex items-center gap-2 flex-1 max-w-[100px]">
              <div
                className="flex-1 rounded overflow-hidden"
                style={{ height: "3px", background: "var(--color-surface-3)" }}
              >
                <div
                  style={{
                    width: "45%",
                    height: "100%",
                    background: "var(--color-accent)",
                  }}
                />
              </div>
            </div>
          ) : (
            <StatusBadge status={doc.status} />
          )}
          {doc.chunkCount != null && doc.chunkCount > 0 && (
            <span
              className="label-mono"
              style={{ letterSpacing: "0.06em", fontSize: "9px" }}
            >
              {doc.chunkCount} фрагментов
            </span>
          )}
        </div>
        {doc.errorMessage && (
          <p
            className="label-mono mt-1 truncate"
            style={{
              color: "var(--color-error)",
              letterSpacing: "0.04em",
              fontSize: "9px",
            }}
          >
            {doc.errorMessage}
          </p>
        )}
      </div>

      <div
        className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity shrink-0"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={onReindex}
          className="w-6 h-6 flex items-center justify-center rounded-md text-[var(--color-text-muted)] hover:text-[var(--color-accent)] hover:bg-[var(--color-accent-glow)] transition-colors cursor-pointer"
        >
          <RefreshCw size={12} />
        </button>
        <button
          onClick={onDelete}
          className="w-6 h-6 flex items-center justify-center rounded-md text-[var(--color-text-muted)] hover:text-[var(--color-error)] hover:bg-red-50 transition-colors cursor-pointer"
        >
          <Trash2 size={12} />
        </button>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: DocumentStatus }) {
  const map: Record<
    DocumentStatus,
    { label: string; color: string; bg: string }
  > = {
    READY: {
      label: "ГОТОВО",
      color: "var(--color-success)",
      bg: "rgba(16,185,129,0.08)",
    },
    INDEXING: {
      label: "ИНДЕКСАЦИЯ",
      color: "var(--color-warning)",
      bg: "rgba(245,158,11,0.08)",
    },
    UPLOADING: {
      label: "ЗАГРУЗКА",
      color: "var(--color-info)",
      bg: "rgba(59,130,246,0.08)",
    },
    ERROR: {
      label: "ОШИБКА",
      color: "var(--color-error)",
      bg: "rgba(239,68,68,0.08)",
    },
    DELETED: {
      label: "УДАЛЕНО",
      color: "var(--color-text-muted)",
      bg: "var(--color-surface-2)",
    },
  };
  const { label, color, bg } = map[status] ?? map.ERROR;
  return (
    <span
      style={{
        fontFamily: "'IBM Plex Mono', monospace",
        fontSize: "8px",
        letterSpacing: "0.1em",
        textTransform: "uppercase",
        color,
        padding: "2px 6px",
        background: bg,
        borderRadius: "3px",
        fontWeight: 600,
      }}
    >
      {label}
    </span>
  );
}

function statusBg(status: DocumentStatus) {
  if (status === "READY") return "rgba(16,185,129,0.08)";
  if (status === "ERROR") return "rgba(239,68,68,0.08)";
  if (status === "INDEXING" || status === "UPLOADING")
    return "rgba(245,158,11,0.08)";
  return "var(--color-surface-2)";
}

function statusColor(status: DocumentStatus) {
  if (status === "READY") return "var(--color-success)";
  if (status === "ERROR") return "var(--color-error)";
  if (status === "INDEXING" || status === "UPLOADING")
    return "var(--color-warning)";
  return "var(--color-text-muted)";
}
