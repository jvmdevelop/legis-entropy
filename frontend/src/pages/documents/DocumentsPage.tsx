import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { FileText, Loader2, RefreshCw, Upload } from "lucide-react";
import { documentApi } from "@/features/document/api/documentApi";
import type { DocumentStatusResponse } from "@/entities/document/types";
import { docId, formatDate, statusMeta } from "./documentMeta";

const MONO = "'IBM Plex Mono', monospace";
const SANS = "'IBM Plex Sans', sans-serif";
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
    <div className="flex-1 min-w-0 overflow-y-auto" style={{ fontFamily: SANS }}>
      <div className="mx-auto flex flex-col gap-4" style={{ maxWidth: 960 }}>
        <div className="flex items-center gap-3 flex-wrap">
          <h1 style={{ fontSize: 20, fontWeight: 600, margin: 0, color: "var(--color-text)" }}>
            Документы
          </h1>
          <div className="ml-auto flex gap-2">
            <button
              type="button"
              onClick={() => void reload()}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs cursor-pointer"
              style={{ border: "1px solid var(--color-border)", color: "var(--color-text-muted)" }}
            >
              <RefreshCw size={12} /> Обновить
            </button>
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              disabled={uploadPct !== null}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs cursor-pointer disabled:opacity-60"
              style={{ background: "var(--color-accent)", color: "#fff" }}
            >
              {uploadPct !== null ? <Loader2 size={12} className="animate-spin" /> : <Upload size={12} />}
              {uploadPct !== null ? `Загрузка ${uploadPct}%` : "Загрузить"}
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

        {loading ? (
          <div className="flex items-center gap-2 text-xs" style={{ color: "var(--color-text-muted)" }}>
            <Loader2 size={14} className="animate-spin" /> Загрузка…
          </div>
        ) : docs.length === 0 ? (
          <p style={{ fontSize: 13, color: "var(--color-text-muted)" }}>
            Документов пока нет. Загрузите договор, чтобы проверить его с ИИ-ассистентом.
          </p>
        ) : (
          <ul className="flex flex-col gap-2 m-0 p-0" style={{ listStyle: "none" }}>
            {docs.map((d) => {
              const id = docId(d);
              const st = statusMeta(d.status);
              return (
                <li key={id}>
                  <Link
                    to={`/documents/${id}`}
                    className="flex items-center gap-3 rounded-xl px-4 py-3 min-w-0"
                    style={{
                      background: "var(--color-bg, #fff)",
                      border: "1px solid var(--color-border)",
                      color: "var(--color-text)",
                      textDecoration: "none",
                    }}
                  >
                    <FileText size={16} style={{ color: "var(--color-accent)", flexShrink: 0 }} />
                    <span className="flex flex-col min-w-0 flex-1">
                      <span className="truncate" style={{ fontSize: 13, fontWeight: 500 }}>
                        {d.fileName || "Без названия"}
                      </span>
                      <span style={{ fontFamily: MONO, fontSize: 10, color: "var(--color-text-muted)" }}>
                        {formatDate(d.createdAt)}
                      </span>
                    </span>
                    <span
                      className="shrink-0 rounded-lg px-2 py-0.5"
                      style={{ fontFamily: MONO, fontSize: 10, color: st.color, border: `1px solid ${st.color}` }}
                    >
                      {st.label}
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
