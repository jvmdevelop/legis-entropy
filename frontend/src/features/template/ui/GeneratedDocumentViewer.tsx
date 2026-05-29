import { useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { GeneratedDocumentDTO } from "@/entities/template";
import { generatedDocumentApi } from "@/features/template/api/templateApi";

interface Props {
  document: GeneratedDocumentDTO;
  onDelete?: () => void;
  onRegenerated?: (doc: GeneratedDocumentDTO) => void;
}

type Tab = "fields" | "markdown" | "preview";

const TOKEN_RE = /\{\{([^}]+)\}\}|\(([а-яёa-z][а-яёa-z0-9_ ]{1,60})\)/gi;

function extractTokens(md: string): string[] {
  const seen = new Set<string>();
  const order: string[] = [];
  for (const m of md.matchAll(TOKEN_RE)) {
    const key = (m[1] ?? m[2]).trim();
    if (!seen.has(key)) {
      seen.add(key);
      order.push(key);
    }
  }
  return order;
}

function substitute(md: string, values: Record<string, string>): string {
  return md.replace(TOKEN_RE, (full, k1, k2) => {
    const key = (k1 ?? k2).trim();
    const v = values[key];
    if (!v?.trim()) return full;
    return v;
  });
}

export function GeneratedDocumentViewer({
  document,
  onDelete,
  onRegenerated,
}: Props) {
  const [tab, setTab] = useState<Tab>("fields");
  const [values, setValues] = useState<Record<string, string>>({});
  const [markdown, setMarkdown] = useState(document.bodyMarkdown);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [regenerating, setRegenerating] = useState(false);

  const tokens = useMemo(() => extractTokens(markdown), [markdown]);
  const preview = useMemo(
    () => substitute(markdown, values),
    [markdown, values],
  );
  const filledCount = tokens.filter((t) => values[t]?.trim()).length;
  const dirty = markdown !== document.bodyMarkdown;

  async function handleSave() {
    setSaving(true);
    try {
      const updated = await generatedDocumentApi.updateBody(
        document.id,
        markdown,
      );
      onRegenerated?.(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch (e) {
      console.error("save failed", e);
    } finally {
      setSaving(false);
    }
  }

  async function handleRegenerate() {
    setRegenerating(true);
    try {
      const newDoc = await generatedDocumentApi.regenerate(document.id);
      onRegenerated?.(newDoc);
    } catch (e) {
      console.error("regenerate failed", e);
    } finally {
      setRegenerating(false);
    }
  }

  function downloadFilled() {
    const blob = new Blob([preview], { type: "text/markdown" });
    const url = URL.createObjectURL(blob);
    const a = window.document.createElement("a");
    a.href = url;
    a.download = slugify(document.title) + ".md";
    window.document.body.appendChild(a);
    a.click();
    window.document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  function openExport(format: "pdf" | "docx") {
    window.open(generatedDocumentApi.exportUrl(document.id, format), "_blank");
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>

      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 8,
          padding: "8px 16px",
          borderBottom: "1px dashed var(--line)",
          background: "var(--bg)",
          flexShrink: 0,
        }}
      >
        <code
          style={{
            fontSize: 10,
            color: "var(--dim)",
            letterSpacing: "0.06em",
            minWidth: 0,
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {document.templateCode ?? "CUSTOM"} ·{" "}
          {document.createdAt
            ? new Date(document.createdAt).toLocaleString("ru")
            : "—"}
        </code>

        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 5,
            flexShrink: 0,
          }}
        >
          <button
            className="le-rpanel__analyze"
            onClick={() => openExport("pdf")}
            title="Скачать PDF"
          >
            ↓ PDF
          </button>
          <button
            className="le-rpanel__analyze"
            onClick={() => openExport("docx")}
            title="Скачать DOCX"
          >
            ↓ DOCX
          </button>
          <button
            className="le-rpanel__analyze"
            onClick={downloadFilled}
            title="Скачать заполненный .md"
          >
            ↓ .md
          </button>
          {onRegenerated && (
            <button
              className="le-rpanel__analyze"
              onClick={handleRegenerate}
              disabled={regenerating}
              style={{ opacity: regenerating ? 0.5 : 1 }}
            >
              {regenerating ? "↻..." : "↻ шаблон"}
            </button>
          )}
        </div>
      </div>

      <div
        style={{
          display: "flex",
          borderBottom: "1px solid var(--line)",
          background: "var(--panel)",
          flexShrink: 0,
        }}
      >
        {(
          [
            ["fields", `поля (${filledCount}/${tokens.length})`],
            ["markdown", dirty ? "редактор ●" : "редактор"],
            ["preview", "просмотр"],
          ] as [Tab, string][]
        ).map(([t, label]) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            style={{
              padding: "7px 14px",
              fontSize: 11,
              fontFamily: "var(--mono)",
              letterSpacing: "0.06em",
              border: "none",
              cursor: "pointer",
              background: tab === t ? "var(--bg)" : "transparent",
              color: tab === t ? "var(--purple)" : "var(--dim)",
              borderBottom:
                tab === t ? "2px solid var(--purple)" : "2px solid transparent",
            }}
          >
            {label}
          </button>
        ))}
      </div>

      <div style={{ flex: 1, overflowY: "auto", minHeight: 0 }}>

        {tab === "fields" && (
          <div
            style={{
              padding: "14px 18px",
              display: "flex",
              flexDirection: "column",
              gap: 10,
            }}
          >
            {tokens.length === 0 && (
              <div className="le-rpanel__hint">
                Заполняемых полей не найдено.
              </div>
            )}
            {tokens.map((key) => (
              <label
                key={key}
                style={{ display: "flex", flexDirection: "column", gap: 3 }}
              >
                <span
                  style={{
                    fontSize: 10,
                    fontFamily: "var(--mono)",
                    letterSpacing: "0.07em",
                    textTransform: "uppercase",
                    color: values[key]?.trim() ? "var(--purple)" : "var(--dim)",
                  }}
                >
                  {key.replace(/_/g, " ")}
                </span>
                <input
                  value={values[key] ?? ""}
                  onChange={(e) =>
                    setValues((p) => ({ ...p, [key]: e.target.value }))
                  }
                  placeholder={`{{${key}}}`}
                  style={{
                    background: "var(--bg)",
                    border: "1px solid",
                    borderColor: values[key]?.trim()
                      ? "var(--purple)"
                      : "var(--line)",
                    borderRadius: 6,
                    padding: "6px 10px",
                    fontSize: 12,
                    fontFamily: "var(--mono)",
                    color: "var(--ink)",
                    outline: "none",
                  }}
                />
              </label>
            ))}
            {tokens.length > 0 && (
              <div
                style={{
                  display: "flex",
                  gap: 8,
                  marginTop: 4,
                  flexWrap: "wrap",
                }}
              >
                <button
                  className="le-btn le-btn--primary"
                  style={{
                    fontSize: 11,
                    padding: "5px 14px",
                    opacity: saving ? 0.6 : 1,
                  }}
                  disabled={saving || filledCount === 0}
                  onClick={async () => {
                    const filled = substitute(markdown, values);
                    setMarkdown(filled);
                    setSaving(true);
                    try {
                      const updated = await generatedDocumentApi.updateBody(
                        document.id,
                        filled,
                      );
                      onRegenerated?.(updated);
                      setSaved(true);
                      setTimeout(() => setSaved(false), 2000);
                    } catch (e) {
                      console.error("save failed", e);
                    } finally {
                      setSaving(false);
                    }
                  }}
                >
                  {saving
                    ? "сохранение..."
                    : saved
                      ? "✓ сохранено"
                      : "сохранить"}
                </button>
                <button
                  className="le-btn le-btn--ghost"
                  style={{ fontSize: 11, padding: "5px 12px" }}
                  onClick={() => setTab("preview")}
                >
                  просмотр →
                </button>
                <button
                  className="le-btn le-btn--ghost"
                  style={{ fontSize: 11, padding: "5px 12px" }}
                  onClick={() => setValues({})}
                >
                  сбросить
                </button>
              </div>
            )}
          </div>
        )}

        {tab === "markdown" && (
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              height: "100%",
              padding: "12px 16px",
              gap: 8,
              boxSizing: "border-box",
            }}
          >
            <textarea
              value={markdown}
              onChange={(e) => setMarkdown(e.target.value)}
              style={{
                flex: 1,
                minHeight: 320,
                resize: "vertical",
                background: "var(--bg)",
                border: "1px solid var(--line)",
                borderRadius: 6,
                padding: "10px 12px",
                fontSize: 12,
                fontFamily: "var(--mono)",
                color: "var(--ink)",
                lineHeight: 1.65,
                outline: "none",
                boxSizing: "border-box",
                width: "100%",
              }}
            />
            <div style={{ display: "flex", gap: 8 }}>
              <button
                className="le-btn le-btn--primary"
                style={{
                  fontSize: 11,
                  padding: "5px 14px",
                  opacity: saving ? 0.6 : 1,
                }}
                onClick={handleSave}
                disabled={saving || !dirty}
              >
                {saving ? "сохранение..." : saved ? "✓ сохранено" : "сохранить"}
              </button>
              <button
                className="le-btn le-btn--ghost"
                style={{ fontSize: 11, padding: "5px 12px" }}
                onClick={() => setMarkdown(document.bodyMarkdown)}
                disabled={!dirty}
              >
                отменить
              </button>
              <button
                className="le-rpanel__analyze"
                style={{ marginLeft: "auto", fontSize: 11 }}
                onClick={() => setTab("preview")}
              >
                просмотр →
              </button>
            </div>
          </div>
        )}

        {tab === "preview" && (
          <div
            className="le-md"
            style={{
              padding: "16px 20px",
              lineHeight: 1.7,
              fontSize: 13,
              color: "var(--ink)",
            }}
          >
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{preview}</ReactMarkdown>
          </div>
        )}
      </div>

      {onDelete && (
        <div
          style={{
            display: "flex",
            justifyContent: "flex-end",
            padding: "8px 16px",
            borderTop: "1px dashed var(--line)",
            background: "var(--bg)",
            flexShrink: 0,
          }}
        >
          <button className="le-rpanel__remove" onClick={onDelete}>
            закрыть
          </button>
        </div>
      )}
    </div>
  );
}

function slugify(s: string) {
  return (
    s
      .toLowerCase()
      .replace(/[^a-zа-я0-9]+/gi, "-")
      .replace(/^-|-$/g, "") || "document"
  );
}
