import { useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { Download, FileSearch, Loader2 } from "lucide-react";
import { documentApi } from "@/features/document/api/documentApi";
import {
  DocumentText,
  markSelector,
  useDocumentStructure,
  useDocumentViewer,
  type AppliedResult,
} from "@/features/document-viewer";
import { formatDate, statusMeta } from "@/entities/document/meta";
import { AssistantPanel } from "./AssistantPanel";
import type { ApplyOptions } from "@/features/document-edits/api/editsApi";

interface Props {
  documentId: string;
  actions?: ReactNode;
  onApplied?: (result: AppliedResult) => void;
  applyOptions?: ApplyOptions;
}

export function DocumentReviewer({ documentId, actions, onApplied, applyOptions }: Props) {
  const navigate = useNavigate();
  const model = useDocumentViewer(documentId, applyOptions);
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
    if (!res) return;
    if (onApplied) {
      onApplied(res);
      return;
    }
    toast.success(res.message);
    navigate(`/documents/${res.documentId}`);
  };

  return (
    <div className="le-dv le-dv-review">
      <div className="le-cpage__hero">
        <div className="le-cpage__hero-icon">
          <FileSearch />
        </div>
        <div className="le-cpage__hero-text">
          <div className="le-cpage__eyebrow">проверка документа</div>
          <h1 className="le-dv-head__title">{doc?.fileName ?? "Документ"}</h1>
          {doc && (
            <div className="le-dv-head__meta">
              <span className={"le-badge " + st.badge}>{st.label}</span>
              <span>{formatDate(doc.createdAt)}</span>
              {structure.loading && (
                <span className="inline-flex items-center gap-1" style={{ color: "var(--purple)" }}>
                  <Loader2 size={11} className="animate-spin" /> разбираем структуру…
                </span>
              )}
            </div>
          )}
        </div>
        <div className="le-cpage__hero-actions">
          {actions}
          <button
            type="button"
            className="le-btn"
            onClick={() => void download()}
            disabled={downloading || !doc}
          >
            {downloading ? <Loader2 className="animate-spin" /> : <Download />}
            оригинал
          </button>
        </div>
      </div>

      <div className="le-dv-grid">
        <div className="le-dv-main">
          {loadError ? (
            <div className="le-dv-card" style={{ color: "var(--danger)", fontSize: 12 }}>
              {loadError}
            </div>
          ) : text === null ? (
            <div className="le-dv-card flex items-center gap-2" style={{ fontSize: 12, color: "var(--dim)" }}>
              <Loader2 size={14} className="animate-spin" />
              {doc && doc.status !== "READY" ? "документ обрабатывается…" : "загрузка текста…"}
            </div>
          ) : (
            <>
              <p className="le-dv-hint">
                Выделите фрагмент, чтобы попросить ИИ предложить правку. Нажмите на подсвеченный текст,
                чтобы принять или отклонить исправление.
              </p>
              <DocumentText model={model} structure={structure} />
            </>
          )}
        </div>
        <div className="le-dv-side">
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
  );
}
