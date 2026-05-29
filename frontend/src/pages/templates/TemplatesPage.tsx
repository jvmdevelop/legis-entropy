import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { toast } from "sonner";
import {
  templateApi,
  generatedDocumentApi,
} from "@/features/template/api/templateApi";
import { GeneratedDocumentViewer } from "@/features/template/ui/GeneratedDocumentViewer";
import type {
  TemplateDTO,
  TemplateSlot,
  GeneratedDocumentDTO,
} from "@/entities/template";

export default function TemplatesPage() {
  const [tab, setTab] = useState<"templates" | "docs">("templates");
  const [items, setItems] = useState<TemplateDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [filter, setFilter] = useState("");
  const [kindFilter, setKindFilter] = useState<string>("все");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const [docs, setDocs] = useState<GeneratedDocumentDTO[]>([]);
  const [docsLoading, setDocsLoading] = useState(false);

  const reloadDocs = useCallback(async () => {
    setDocsLoading(true);
    try {
      const data = await generatedDocumentApi.list({});
      setDocs(data);
    } catch (e) {
      console.error(e);
    } finally {
      setDocsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (tab === "docs") void reloadDocs();
  }, [tab, reloadDocs]);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const data = await templateApi.list();
      setItems(data);
      if (!selectedId && data.length > 0) setSelectedId(data[0].id);
    } catch (e) {
      console.error(e);
      toast.error("Не удалось загрузить шаблоны");
    } finally {
      setLoading(false);
    }
  }, [selectedId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const kinds = useMemo(() => {
    const set = new Set<string>(["все"]);
    items.forEach((t) => set.add(t.kind || "OTHER"));
    return Array.from(set);
  }, [items]);

  const kindCounts = useMemo(() => {
    const c: Record<string, number> = {};
    items.forEach((t) => {
      const k = t.kind || "OTHER";
      c[k] = (c[k] ?? 0) + 1;
    });
    return c;
  }, [items]);

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    return items.filter((t) => {
      if (kindFilter !== "все" && (t.kind || "OTHER") !== kindFilter)
        return false;
      if (!q) return true;
      return (
        (t.title?.toLowerCase().includes(q) ?? false) ||
        (t.code?.toLowerCase().includes(q) ?? false) ||
        (t.description?.toLowerCase().includes(q) ?? false)
      );
    });
  }, [items, filter, kindFilter]);

  const selected = items.find((t) => t.id === selectedId) ?? null;

  async function handleUpload(file: File) {
    setUploading(true);
    try {
      const created = await templateApi.upload({ file, fileName: file.name });
      setItems((prev) => [created, ...prev.filter((t) => t.id !== created.id)]);
      setSelectedId(created.id);
      toast.success(`Шаблон импортирован (${created.sourceFormat})`);
    } catch (e) {
      console.error(e);
      toast.error("Не удалось импортировать шаблон");
    } finally {
      setUploading(false);
    }
  }

  async function handleDelete(t: TemplateDTO) {
    if (t.systemTemplate) {
      toast.error("Системные шаблоны нельзя удалить");
      return;
    }
    if (!confirm(`Удалить шаблон «${t.title}»?`)) return;
    try {
      await templateApi.remove(t.id);
      setItems((prev) => prev.filter((x) => x.id !== t.id));
      if (selectedId === t.id) setSelectedId(null);
      toast.success("Шаблон удалён");
    } catch (e) {
      console.error(e);
      toast.error("Не удалось удалить");
    }
  }

  const userCount = items.filter((t) => !t.systemTemplate).length;
  const systemCount = items.filter((t) => t.systemTemplate).length;

  return (
    <div className="le-tmpl-page">
      <div className="le-tmpl-page__hero">
        <div className="le-tmpl-page__hero-icon">
          <IconDoc />
        </div>
        <div>
          <h1 className="le-tmpl-page__title">Шаблоны документов</h1>
          <p className="le-tmpl-page__sub">
            ваша библиотека · {userCount} пользовательских + {systemCount}{" "}
            системных · доступны во всех workspace'ах
          </p>
        </div>
        <div className="le-tmpl-page__hero-actions">
          <button
            className="le-btn le-btn--primary"
            disabled={uploading}
            onClick={() => inputRef.current?.click()}
          >
            {uploading ? <IconSpinner /> : <IconUpload />}
            <span>{uploading ? "импорт…" : "загрузить"}</span>
          </button>
          <input
            ref={inputRef}
            type="file"
            accept=".docx,.pdf,.md,.txt,.html,.odt,.rtf"
            style={{ display: "none" }}
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) void handleUpload(f);
              if (inputRef.current) inputRef.current.value = "";
            }}
          />
        </div>
      </div>

      <div
        style={{
          display: "flex",
          gap: 4,
          padding: "0 0 4px",
          borderBottom: "1px solid var(--line)",
          marginBottom: 20,
        }}
      >
        {(["templates", "docs"] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            style={{
              border: "none",
              cursor: "pointer",
              padding: "6px 16px",
              borderRadius: 8,
              fontFamily: "var(--mono)",
              fontSize: 12,
              color: tab === t ? "var(--purple)" : "var(--dim)",
              background: tab === t ? "rgba(139,92,246,0.08)" : "transparent",
            }}
          >
            {t === "templates"
              ? "шаблоны"
              : `мои документы${docs.length ? ` (${docs.length})` : ""}`}
          </button>
        ))}
      </div>

      {tab === "docs" && (
        <div
          style={{
            maxWidth: 860,
            margin: "0 auto",
            display: "flex",
            flexDirection: "column",
            gap: 16,
          }}
        >
          {docsLoading ? (
            <div className="le-lp-empty">
              <div className="le-lp-empty__glyph">
                <IconSpinner />
              </div>
            </div>
          ) : docs.length === 0 ? (
            <div className="le-lp-empty">
              <div className="le-lp-empty__glyph">
                <IconDoc />
              </div>
              <div className="le-lp-empty__title">нет документов</div>
              <div className="le-lp-empty__sub">
                попросите ИИ составить договор или заявление — он появится здесь
              </div>
            </div>
          ) : (
            docs.map((doc) => (
              <GeneratedDocumentViewer
                key={doc.id}
                document={doc}
                onDelete={async () => {
                  if (!confirm(`Удалить «${doc.title}»?`)) return;
                  try {
                    await generatedDocumentApi.remove(doc.id);
                    setDocs((prev) => prev.filter((d) => d.id !== doc.id));
                  } catch {
                    toast.error("Не удалось удалить");
                  }
                }}
                onRegenerated={(newDoc) =>
                  setDocs((prev) => [
                    newDoc,
                    ...prev.filter((d) => d.id !== doc.id),
                  ])
                }
              />
            ))
          )}
        </div>
      )}

      <div
        className="le-tmpl-page__grid"
        style={{ display: tab === "templates" ? undefined : "none" }}
      >
        <aside className="le-lp-filter">
          <div className="le-lp-filter__head">
            <IconFilter />
            <span>Фильтр</span>
          </div>

          <div className="le-lp-filter__section">
            <div className="le-lp-filter__label">поиск</div>
            <div className="le-search">
              <IconSearch />
              <input
                placeholder="название или код..."
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
              />
            </div>
          </div>

          <div className="le-lp-filter__section">
            <div className="le-lp-filter__label">тип</div>
            <ul className="le-typelist">
              {kinds.map((k) => (
                <li
                  key={k}
                  className={
                    "le-typelist__item" +
                    (kindFilter === k ? " le-typelist__item--on" : "")
                  }
                  onClick={() => setKindFilter(k)}
                >
                  <span className="le-typelist__name">{k}</span>
                  <span className="le-typelist__count">
                    {k === "все" ? items.length : kindCounts[k] || 0}
                  </span>
                </li>
              ))}
            </ul>
          </div>

          <div className="le-lp-filter__stat">
            <div className="le-lp-filter__statrow">
              <span>пользовательских</span>
              <span className="le-lp-filter__statval">{userCount}</span>
            </div>
            <div className="le-lp-filter__statrow">
              <span>системных</span>
              <span className="le-lp-filter__statval le-lp-filter__statval--dim">
                {systemCount}
              </span>
            </div>
          </div>

          <div className="le-tmpl-hint">
            <IconInfo />
            <p>
              Загруженный файл (.docx/.pdf/.md/.txt) автоматически
              конвертируется в Markdown, поля типа <code>[ФИО]</code>,{" "}
              <code>____</code>, <code>&lt;дата&gt;</code> превращаются в слоты{" "}
              <code>{"{{...}}"}</code> с контекстом — ИИ потом заполнит их по
              сюжету.
            </p>
          </div>
        </aside>

        <section className="le-lp-results">
          <div className="le-lp-results__head">
            <span>
              {filter || kindFilter !== "все"
                ? `найдено: ${filtered.length}`
                : "все шаблоны"}
            </span>
            <span className="le-lp-results__count">
              {String(filtered.length).padStart(2, "0")}
            </span>
          </div>

          {loading ? (
            <div className="le-lp-empty">
              <div className="le-lp-empty__glyph">
                <IconSpinner />
              </div>
              <div className="le-lp-empty__sub">загружаю шаблоны…</div>
            </div>
          ) : filtered.length === 0 ? (
            <div className="le-lp-empty">
              <div className="le-lp-empty__glyph">
                <IconDoc />
              </div>
              <div className="le-lp-empty__title">
                {items.length === 0 ? "библиотека пуста" : "ничего не найдено"}
              </div>
              <div className="le-lp-empty__sub">
                {items.length === 0
                  ? "загрузите .docx или .pdf — мы найдём поля для заполнения автоматически"
                  : "попробуйте другой запрос или сбросьте фильтр типа"}
              </div>
            </div>
          ) : (
            <ul className="le-lp-list">
              {filtered.map((t) => (
                <li
                  key={t.id}
                  className={
                    "le-lp-item" +
                    (selectedId === t.id ? " le-lp-item--on" : "")
                  }
                  onClick={() => setSelectedId(t.id)}
                >
                  <div className="le-lp-item__head">
                    <span
                      className={
                        "le-lp-item__type le-lp-item__type--" +
                        (t.systemTemplate ? "sys" : "user")
                      }
                    >
                      {t.systemTemplate ? "system" : "user"}
                    </span>
                    <span className="le-lp-item__code">{t.code}</span>
                    <span className="le-lp-item__year">
                      {t.sourceFormat ?? "MANUAL"}
                    </span>
                  </div>
                  <div className="le-lp-item__title">{t.title}</div>
                  <div className="le-lp-item__meta">
                    <span className="le-lp-item__topic">{t.kind}</span>
                    {t.description && (
                      <span style={{ fontSize: 11, color: "var(--dim)" }}>
                        · {t.description.slice(0, 80)}
                      </span>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <aside className="le-lp-detail">
          {selected ? (
            <TemplateDetail
              template={selected}
              onDelete={() => void handleDelete(selected)}
            />
          ) : (
            <div className="le-lp-detail__hint">
              <IconDoc />
              <p>Выберите шаблон слева, чтобы увидеть поля и тело</p>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}

function TemplateDetail({
  template,
  onDelete,
}: {
  template: TemplateDTO;
  onDelete: () => void;
}) {
  const slots = useMemo<TemplateSlot[]>(() => {
    if (!template.slotsJson) return [];
    try {
      return JSON.parse(template.slotsJson) as TemplateSlot[];
    } catch {
      return [];
    }
  }, [template.slotsJson]);

  return (
    <>
      <div className="le-lp-detail__eyebrow">
        {template.kind} · {template.sourceFormat ?? "MANUAL"}
      </div>
      <h2 className="le-lp-detail__title">{template.title}</h2>
      <div className="le-lp-detail__codes">
        <code className="le-lp-item__code">{template.code}</code>
        {template.systemTemplate ? (
          <span className={"le-lp-item__type le-lp-item__type--sys"}>
            system
          </span>
        ) : (
          <span className={"le-lp-item__type le-lp-item__type--user"}>
            user
          </span>
        )}
      </div>
      {template.description && (
        <p
          style={{
            fontSize: 12,
            color: "var(--dim)",
            margin: 0,
            lineHeight: 1.5,
          }}
        >
          {template.description}
        </p>
      )}

      <div className="le-lp-detail__divider" />

      <div
        className="le-tmpl-detail__row"
        style={{
          width: "100%",
          display: "flex",
          justifyContent: "space-between",
        }}
      >
        <span
          style={{
            fontSize: 11,
            color: "var(--dim)",
            letterSpacing: "0.06em",
            textTransform: "uppercase",
          }}
        >
          поля
        </span>
        <span className="le-lp-results__count">{slots.length}</span>
      </div>

      {slots.length === 0 ? (
        <p
          style={{
            fontSize: 11,
            color: "var(--dim)",
            margin: 0,
            fontStyle: "italic",
          }}
        >
          В шаблоне нет явных пропусков
        </p>
      ) : (
        <ul className="le-slot-list">
          {slots.map((s) => (
            <li key={s.id} className="le-slot">
              <div className="le-slot__head">
                <span
                  className={
                    "le-slot__type le-slot__type--" +
                    (s.type ?? "TEXT").toLowerCase()
                  }
                >
                  {s.type ?? "TEXT"}
                </span>
                {s.required && <span className="le-slot__flag">required</span>}
                {s.multiline && (
                  <span className="le-slot__flag">multiline</span>
                )}
              </div>
              <div className="le-slot__label">{s.label}</div>
              <code className="le-slot__token">{`{{${s.id}}}`}</code>
              {s.aiHint && (
                <p className="le-slot__hint">
                  <span style={{ color: "var(--purple)" }}>ai · </span>
                  {s.aiHint}
                </p>
              )}
              {s.context && <p className="le-slot__ctx">{s.context}</p>}
            </li>
          ))}
        </ul>
      )}

      <div className="le-lp-detail__divider" />

      <div style={{ width: "100%" }}>
        <div
          style={{
            fontSize: 11,
            color: "var(--dim)",
            letterSpacing: "0.06em",
            textTransform: "uppercase",
            marginBottom: 8,
          }}
        >
          тело (markdown)
        </div>
        <div className="le-tmpl-body markdown-body prose-legal">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {template.body}
          </ReactMarkdown>
        </div>
      </div>

      {!template.systemTemplate && (
        <button
          onClick={onDelete}
          className="le-btn le-btn--ghost"
          style={{ marginTop: 12, color: "var(--danger)" }}
        >
          <IconTrash /> удалить шаблон
        </button>
      )}
    </>
  );
}

function IconDoc() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinejoin="round"
      strokeLinecap="round"
    >
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <path d="M14 3v5h5" />
      <path d="M8 13h8M8 17h5" />
    </svg>
  );
}
function IconFilter() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
    >
      <path d="M3 5h18l-7 9v6l-4-2v-4z" />
    </svg>
  );
}
function IconSearch() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4.3-4.3" />
    </svg>
  );
}
function IconUpload() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 4v12" />
      <path d="M6 10l6-6 6 6" />
      <path d="M4 20h16" />
    </svg>
  );
}
function IconSpinner() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      style={{ animation: "le-spin 1s linear infinite" }}
    >
      <path d="M12 3a9 9 0 1 0 9 9" />
    </svg>
  );
}
function IconInfo() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8h.01" />
      <path d="M11 12h1v5" />
    </svg>
  );
}
function IconTrash() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
    </svg>
  );
}
