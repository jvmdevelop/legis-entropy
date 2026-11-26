import { useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { ArrowLeft, Download, Loader2 } from "lucide-react";
import { documentApi } from "@/features/document/api/documentApi";
import {
  DocumentText,
  markSelector,
  useDocumentStructure,
  useDocumentViewer,
} from "@/features/document-viewer";
import { AssistantPanel } from "./AssistantPanel";
import { formatDate, statusMeta } from "./documentMeta";

const MONO = "'IBM Plex Mono', monospace";
const SANS = "'IBM Plex Sans', sans-serif";

export default function DocumentViewerPage() {
  const { documentId } = useParams<{ documentId: string }>();
  if (!documentId) return <Navigate to="/documents" replace />;
  return <DocumentViewer key={documentId} documentId={documentId} />;
}

function DocumentViewer({ documentId }: { documentId: string }) {
  const navigate = useNavigate();
  const model = useDocumentViewer(documentId);
  const structure = useDocumentStructure(documentId, model.text !== null);
  const [downloading, setDownloading] = useState(false);
  const { doc, text, loadError } = model;
  const st = statusMeta(doc?.status);

  const download = async () => {
    setDownloading(true);
    try {
      const blob = await documentApi.file(documentId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = doc?.fileName || "document";
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      toast.error("Не удалось скачать файл");
    } finally {
      setDownloading(false);
    }
  };

  const focus = (id: string) => {
    model.setActiveId(id);
    requestAnimationFrame(() =>
      document.querySelector(markSelector(id))?.scrollIntoView({ behavior: "smooth", block: "center" }),
    );
  };

  const locate = (clause: string | null, page: number | null) => {
    const idx = structure.findClause(clause);
    if (idx >= 0) structure.focusClause(idx);
    else if (page) structure.focusPage(page);
    else toast.info("Пункт не найден в тексте");
  };

  const apply = async () => {
    const res = await model.apply();
    if (res) navigate(`/documents/${res.documentId}`);
  };

  return (
    <div className="flex-1 min-w-0 overflow-y-auto overflow-x-hidden" style={{ fontFamily: SANS }}>
      <div className="mx-auto flex flex-col gap-4 min-w-0" style={{ maxWidth: 1280 }}>
        <div className="flex items-center gap-3 flex-wrap min-w-0">
          <Link
            to="/documents"
            className="flex items-center gap-1 text-xs"
            style={{ color: "var(--color-text-muted)", textDecoration: "none" }}
          >
            <ArrowLeft size={13} /> документы
          </Link>
          <div className="flex flex-col min-w-0 flex-1">
            <h1
              className="truncate"
              style={{ fontSize: 17, fontWeight: 600, margin: 0, color: "var(--color-text)" }}
            >
              {doc?.fileName ?? "Документ"}
            </h1>
            {doc && (
              <span style={{ fontFamily: MONO, fontSize: 10, color: "var(--color-text-muted)" }}>
                <span style={{ color: st.color }}>{st.label}</span> · {formatDate(doc.createdAt)}
              </span>
            )}
          </div>
          <button
            type="button"
            onClick={() => void download()}
            disabled={downloading || !doc}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs cursor-pointer disabled:opacity-60"
            style={{ border: "1px solid var(--color-border)", color: "var(--color-text)" }}
          >
            {downloading ? <Loader2 size={12} className="animate-spin" /> : <Download size={12} />}
            Оригинал
          </button>
        </div>

        <div className="grid gap-4 min-w-0 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
          <div className="min-w-0">
            {loadError ? (
              <p style={{ fontSize: 13, color: "var(--color-error)" }}>{loadError}</p>
            ) : text === null ? (
              <div className="flex items-center gap-2 text-xs" style={{ color: "var(--color-text-muted)" }}>
                <Loader2 size={14} className="animate-spin" />
                {doc && doc.status !== "READY" ? "Документ обрабатывается…" : "Загрузка текста…"}
              </div>
            ) : (
              <>
                <p className="mb-2" style={{ fontSize: 11, color: "var(--color-text-muted)" }}>
                  Выделите фрагмент, чтобы попросить ИИ предложить правку. Нажмите на подсвеченный
                  текст, чтобы принять или отклонить исправление.
                  {structure.loading && (
                    <span className="inline-flex items-center gap-1 ml-2" style={{ color: "var(--color-accent)" }}>
                      <Loader2 size={11} className="animate-spin" /> разбираем структуру…
                    </span>
                  )}
                </p>
                <DocumentText model={model} structure={structure} />
              </>
            )}
          </div>
          <div className="min-w-0 lg:sticky lg:top-0 lg:max-h-[calc(100vh-110px)] lg:overflow-y-auto">
            <AssistantPanel
              documentId={documentId}
              model={model}
              structure={structure}
              onFocus={focus}
              onApply={() => void apply()}
              onLocate={locate}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
