import { ListTree, Loader2 } from "lucide-react";
import type { DocumentStructureModel } from "../model/useDocumentStructure";
import { MONO } from "../lib/verdict";

export function StructurePanel({ structure }: { structure: DocumentStructureModel }) {
  const { loading, failed, clauses, pages, typeStats, typeFilter } = structure;

  return (
    <section
      className="le-dv-card flex flex-col gap-3 min-w-0"
    >
      <div className="flex items-center gap-2">
        <ListTree size={14} style={{ color: "var(--color-accent)", flexShrink: 0 }} />
        <span className="le-section-head__label" style={{ color: "var(--purple)" }}>
          Структура договора
        </span>
      </div>

      {loading ? (
        <span className="flex items-center gap-2" style={{ fontSize: "12px", color: "var(--color-text-muted)" }}>
          <Loader2 size={12} className="animate-spin" /> Разбираем пункты… это может занять до 30 секунд
        </span>
      ) : failed ? (
        <span style={{ fontSize: "12px", color: "var(--color-error)" }}>Не удалось получить структуру документа</span>
      ) : !structure.structure ? (
        <span style={{ fontSize: "12px", color: "var(--color-text-muted)" }}>Структура появится после загрузки текста</span>
      ) : (
        <>
          <span style={{ fontFamily: MONO, fontSize: "10px", color: "var(--color-text-muted)" }}>
            пунктов: {clauses.length} · страниц: {Math.max(pages.length, 1)}
          </span>
          {typeStats.length === 0 ? (
            <span style={{ fontSize: "12px", color: "var(--color-text-muted)" }}>Типы пунктов не определены</span>
          ) : (
            <ul className="flex flex-col gap-1 m-0 p-0" style={{ listStyle: "none" }}>
              {typeStats.map((t) => (
                <li key={t.code} className="flex items-center gap-2 min-w-0">
                  <label className="flex items-center gap-2 flex-1 min-w-0 cursor-pointer" style={{ fontSize: "12px" }}>
                    <input
                      type="checkbox"
                      checked={typeFilter.has(t.code)}
                      onChange={() => structure.toggleType(t.code)}
                      style={{ accentColor: "var(--color-accent)" }}
                    />
                    <span className="truncate" style={{ color: "var(--color-text)" }}>{t.label}</span>
                  </label>
                  <button
                    type="button"
                    onClick={() => structure.focusClause(t.first)}
                    title="Перейти к первому пункту"
                    className="rounded-md px-1.5 cursor-pointer shrink-0"
                    style={{
                      fontFamily: MONO,
                      fontSize: "10px",
                      lineHeight: "18px",
                      color: "var(--color-accent)",
                      background: "var(--purple-tint)",
                    }}
                  >
                    {t.count}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {typeFilter.size > 0 && (
            <button
              type="button"
              onClick={structure.clearFilter}
              className="self-start text-xs cursor-pointer"
              style={{ color: "var(--color-accent)" }}
            >
              Сбросить фильтр
            </button>
          )}
          <span style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
            Отметьте типы, чтобы приглушить остальные пункты. Нажмите на метку у пункта, чтобы увидеть его краткое содержание.
          </span>
        </>
      )}
    </section>
  );
}
