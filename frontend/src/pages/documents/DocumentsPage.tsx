import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { FileText, Loader2, RefreshCw, Upload } from "lucide-react";
import { documentApi } from "@/features/document/api/documentApi";
import type { DocumentStatusResponse } from "@/entities/document/types";
import { docId, formatDate, statusMeta } from "@/entities/document/meta";

const PROCESSING = new Set(["UPLOADING", "INDEXING"]);

export default function DocumentsPage() {
  const [docs, setDocs] = useState<DocumentStatusResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploadPct, setUploadPct] = useState<number | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const reload = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const page = await documentApi.list(0, 50);
      setDocs((page.content ?? []).filter((d) => d.status !== "DELETED"));
    } catch {
      if (!silent) toast.error("Не удалось загрузить документы");
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    let alive = true;
    documentApi
      .list(0, 50)
      .then((page) => {
        if (alive) setDocs((page.content ?? []).filter((d) => d.status !== "DELETED"));
      })
      .catch(() => toast.error("Не удалось загрузить документы"))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, []);

  const hasProcessing = docs.some((d) => PROCESSING.has(d.status));
  useEffect(() => {
    if (!hasProcessing) return;
    const t = setInterval(() => void reload(true), 4000);
    return () => clearInterval(t);
  }, [hasProcessing, reload]);

  const upload = async (file: File) => {
    setUploadPct(0);
    try {
      await documentApi.upload(file, setUploadPct);
      toast.success(`Документ «${file.name}» загружен`);
      await reload(true);
    } catch {
      toast.error("Не удалось загрузить документ");
    } finally {
      setUploadPct(null);
    }
  };

  return (
    <div className="le-dv-scroll">
      <div className="le-dv le-dv-page le-dv-page--narrow">
        <div className="le-cpage__hero">
          <div className="le-cpage__hero-icon">
            <FileText />
          </div>
          <div className="le-cpage__hero-text">
            <div className="le-cpage__eyebrow">мои документы</div>
            <h1 className="le-cpage__title">Документы</h1>
            <p className="le-cpage__sub">откройте договор, чтобы проверить его с ИИ-ассистентом и внести правки</p>
          </div>
          <div className="le-cpage__hero-actions">
            <button type="button" className="le-btn" onClick={() => void reload()}>
              <RefreshCw /> обновить
            </button>
            <button
              type="button"
              className="le-btn le-btn--primary"
              onClick={() => inputRef.current?.click()}
              disabled={uploadPct !== null}
            >
              {uploadPct !== null ? <Loader2 className="animate-spin" /> : <Upload />}
              {uploadPct !== null ? `загрузка ${uploadPct}%` : "загрузить"}
            </button>
            <input
              ref={inputRef}
              type="file"
              accept=".pdf,.docx,.doc,.txt"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                e.target.value = "";
                if (f) void upload(f);
              }}
            />
          </div>
        </div>

        <div className="le-cpage__body">
          {loading ? (
            <div className="flex items-center gap-2" style={{ fontSize: 12, color: "var(--dim)" }}>
              <Loader2 size={14} className="animate-spin" /> загрузка…
            </div>
          ) : docs.length === 0 ? (
            <p className="le-dv-hint">Документов пока нет. Загрузите договор, чтобы проверить его с ИИ-ассистентом.</p>
          ) : (
            <>
              <div className="le-section-head">
                <span className="le-section-head__label">файлы</span>
                <span className="le-section-head__count">{docs.length}</span>
              </div>
              <ul className="le-dv-list">
                {docs.map((d) => {
                  const id = docId(d);
                  const st = statusMeta(d.status);
                  return (
                    <li key={id}>
                      <Link to={`/documents/${id}`} className="le-rcard">
                        <div className="le-rcard__head">
                          <span className="le-rcard__lhs min-w-0">
                            <FileText size={14} style={{ color: "var(--purple)", flexShrink: 0 }} />
                            <span className="le-rcard__title truncate">{d.fileName || "Без названия"}</span>
                          </span>
                          <span className={"le-badge " + st.badge}>{st.label}</span>
                        </div>
                        <div className="le-rcard__meta">
                          <span>{formatDate(d.createdAt)}</span>
                          <span className="le-rcard__sep">·</span>
                          <span className="le-rcard__open">открыть</span>
                        </div>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
