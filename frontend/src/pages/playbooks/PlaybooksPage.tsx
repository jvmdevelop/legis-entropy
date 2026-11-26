import { useEffect, useRef, useState, type ReactNode } from "react";
import { isAxiosError } from "axios";
import { toast } from "sonner";
import { BookCopy, ListChecks, Loader2, Plus } from "lucide-react";
import { PlaybookEditor, newRule, playbooksApi, type Playbook } from "@/features/playbooks";

const MONO = "'IBM Plex Mono', monospace";
const SANS = "'IBM Plex Sans', sans-serif";

interface Editing {
  key: string;
  playbook: Playbook;
}

function blank(): Playbook {
  return { id: "", name: "", rules: [newRule()] };
}

export default function PlaybooksPage() {
  const [own, setOwn] = useState<Playbook[]>([]);
  const [templates, setTemplates] = useState<Playbook[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<Editing | null>(null);
  const editorRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let alive = true;
    Promise.allSettled([playbooksApi.list(), playbooksApi.templates()]).then(([a, b]) => {
      if (!alive) return;
      if (a.status === "fulfilled") setOwn(a.value);
      else toast.error("Не удалось загрузить правила");
      if (b.status === "fulfilled") setTemplates(b.value);
      setLoading(false);
    });
    return () => {
      alive = false;
    };
  }, []);

  const open = (playbook: Playbook) => {
    setEditing({ key: `${playbook.id || "new"}-${Date.now()}`, playbook });
    requestAnimationFrame(() => editorRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
  };

  const copyTemplate = (t: Playbook) =>
    open({
      id: "",
      name: `${t.name} (копия)`,
      rules: t.rules.map((r) => ({ ...newRule(r.kind), ...r, id: newRule().id })),
    });

  const save = async (p: Playbook) => {
    try {
      const saved = await playbooksApi.save(p);
      setOwn((prev) =>
        prev.some((x) => x.id === saved.id) ? prev.map((x) => (x.id === saved.id ? saved : x)) : [saved, ...prev],
      );
      setEditing({ key: `${saved.id}-${Date.now()}`, playbook: saved });
      toast.success("Правила сохранены");
      return true;
    } catch (e) {
      toast.error(
        isAxiosError(e) && e.response?.status === 422
          ? "Проверьте название и правила"
          : "Не удалось сохранить правила",
      );
      return false;
    }
  };

  const remove = async (id: string) => {
    try {
      await playbooksApi.remove(id);
    } catch (e) {
      if (!(isAxiosError(e) && e.response?.status === 404)) {
        toast.error("Не удалось удалить");
        return false;
      }
    }
    setOwn((prev) => prev.filter((x) => x.id !== id));
    setEditing(null);
    toast.success("Набор правил удалён");
    return true;
  };

  const activeId = editing?.playbook.id;

  return (
    <div className="flex-1 min-w-0 overflow-y-auto overflow-x-hidden" style={{ fontFamily: SANS }}>
      <div className="mx-auto flex flex-col gap-4 min-w-0" style={{ maxWidth: 1100 }}>
        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex flex-col min-w-0">
            <h1 style={{ fontSize: 20, fontWeight: 600, margin: 0, color: "var(--color-text)" }}>
              Правила организации
            </h1>
            <span style={{ fontSize: 12, color: "var(--color-text-muted)" }}>
              Внутренние требования к договорам. Это не нормы закона — их проверяет ИИ на странице документа.
            </span>
          </div>
          <button
            type="button"
            onClick={() => open(blank())}
            className="ml-auto flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs cursor-pointer"
            style={{ background: "var(--color-accent)", color: "#fff" }}
          >
            <Plus size={12} /> Новый набор
          </button>
        </div>

        <div className="grid gap-4 min-w-0 lg:grid-cols-[320px_minmax(0,1fr)] lg:items-start">
          <div className="flex flex-col gap-4 min-w-0">
            {loading ? (
              <span className="flex items-center gap-2 text-xs" style={{ color: "var(--color-text-muted)" }}>
                <Loader2 size={14} className="animate-spin" /> Загрузка…
              </span>
            ) : (
              <>
                <PlaybookList
                  title="Мои правила"
                  icon={<ListChecks size={13} />}
                  items={own}
                  activeId={activeId}
                  empty="Пока нет своих наборов — создайте новый или скопируйте шаблон."
                  onOpen={open}
                />
                <PlaybookList
                  title="Шаблоны"
                  icon={<BookCopy size={13} />}
                  items={templates}
                  activeId={activeId}
                  empty="Шаблонов нет."
                  onOpen={open}
                  onCopy={copyTemplate}
                />
              </>
            )}
          </div>

          <div
            ref={editorRef}
            className="rounded-2xl p-4 min-w-0"
            style={{ background: "var(--color-bg, #fff)", border: "1px solid var(--color-border)", scrollMarginTop: 8 }}
          >
            {editing ? (
              <PlaybookEditor
                key={editing.key}
                initial={editing.playbook}
                onSave={save}
                onDelete={remove}
                onCopy={copyTemplate}
              />
            ) : (
              <span style={{ fontSize: 13, color: "var(--color-text-muted)" }}>
                Выберите набор правил слева или создайте новый.
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

interface ListProps {
  title: string;
  icon: ReactNode;
  items: Playbook[];
  activeId?: string;
  empty: string;
  onOpen: (p: Playbook) => void;
  onCopy?: (p: Playbook) => void;
}

function PlaybookList({ title, icon, items, activeId, empty, onOpen, onCopy }: ListProps) {
  return (
    <section className="flex flex-col gap-2 min-w-0">
      <span
        className="flex items-center gap-1.5"
        style={{ fontFamily: MONO, fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase", color: "var(--color-accent)" }}
      >
        {icon} {title} · {items.length}
      </span>
      {items.length === 0 ? (
        <span style={{ fontSize: 12, color: "var(--color-text-muted)" }}>{empty}</span>
      ) : (
        <ul className="flex flex-col gap-1.5 m-0 p-0" style={{ listStyle: "none" }}>
          {items.map((p) => (
            <li
              key={p.id}
              className="flex items-center gap-2 rounded-xl px-3 py-2 min-w-0"
              style={{
                background: "var(--color-surface)",
                border: `1px solid ${p.id === activeId ? "var(--color-accent)" : "var(--color-border)"}`,
              }}
            >
              <button
                type="button"
                onClick={() => onOpen(p)}
                className="flex flex-col min-w-0 flex-1 text-left cursor-pointer"
              >
                <span className="truncate" style={{ fontSize: 13, color: "var(--color-text)" }}>
                  {p.name || "Без названия"}
                </span>
                <span style={{ fontFamily: MONO, fontSize: 10, color: "var(--color-text-muted)" }}>
                  правил: {p.rules.length}
                </span>
              </button>
              {onCopy && (
                <button
                  type="button"
                  onClick={() => onCopy(p)}
                  className="shrink-0 rounded-lg px-2 py-1 text-xs cursor-pointer"
                  style={{ color: "var(--color-accent)", border: "1px solid rgba(139,92,246,0.3)" }}
                >
                  Создать из шаблона
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
