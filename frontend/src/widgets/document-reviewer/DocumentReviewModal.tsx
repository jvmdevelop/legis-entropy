import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { ExternalLink, FileSearch, X } from "lucide-react";
import type { AppliedResult } from "@/features/document-viewer";
import { DocumentReviewer } from "./DocumentReviewer";

interface Props {
  documentId: string;
  title?: string;
  graphId?: string;
  onClose: () => void;
  onApplied?: (result: AppliedResult) => void;
}

export function DocumentReviewModal({ documentId, title, graphId, onClose, onApplied }: Props) {
  const [currentId, setCurrentId] = useState(documentId);
  const [currentTitle, setCurrentTitle] = useState(title);
  const [revision, setRevision] = useState(0);
  const downOnBackdrop = useRef(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !e.defaultPrevented) onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const handleApplied = (res: AppliedResult) => {
    if (res.inPlace) {
      toast.success(res.message, { duration: 8000 });
      if (res.fileName) setCurrentTitle(res.fileName);
      setRevision((r) => r + 1);
      onApplied?.(res);
      return;
    }
    toast.success(res.message, {
      duration: 10000,
      action: {
        label: "Открыть копию",
        onClick: () => {
          setCurrentId(res.documentId);
          setCurrentTitle(res.fileName ?? undefined);
        },
      },
    });
    onApplied?.(res);
  };

  return (
    <div
      className="le-modal-backdrop"
      onMouseDown={(e) => {
        downOnBackdrop.current = e.target === e.currentTarget;
      }}
      onClick={(e) => {
        if (downOnBackdrop.current && e.target === e.currentTarget) onClose();
        downOnBackdrop.current = false;
      }}
    >
      <div className="le-modal le-dv-modal" role="dialog" aria-modal="true">
        <div className="le-modal__head">
          <span
            className="min-w-0"
            style={{
              display: "inline-flex",
              gap: 6,
              alignItems: "center",
              color: "var(--purple)",
              overflow: "hidden",
            }}
          >
            <FileSearch size={13} style={{ flexShrink: 0 }} />
            <span className="truncate">проверка документа{currentTitle ? ` · ${currentTitle}` : ""}</span>
          </span>
          <span className="inline-flex items-center gap-2 shrink-0">
            <Link to={`/documents/${currentId}`} className="le-dv-modal__link">
              <ExternalLink /> <span className="hidden sm:inline">открыть на отдельной странице</span>
            </Link>
            <button type="button" className="le-modal__close" onClick={onClose} aria-label="Закрыть">
              <X />
            </button>
          </span>
        </div>
        <div className="le-dv-modal__body">
          <DocumentReviewer
            key={`${currentId}:${revision}`}
            documentId={currentId}
            onApplied={handleApplied}
            applyOptions={graphId ? { inPlace: true, graphId } : undefined}
          />
        </div>
      </div>
    </div>
  );
}
