import { useEffect, useRef, useState, type ReactNode } from "react";
import { isAxiosError } from "axios";
import { toast } from "sonner";
import { BookCopy, ListChecks, Loader2, Plus } from "lucide-react";
import { PlaybookEditor, newRule, playbooksApi, type Playbook } from "@/features/playbooks";


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
    <div className="le-dv-scroll">
      <div className="le-dv le-dv-page" style={{ maxWidth: 1100 }}>
        <div className="le-cpage__hero">
          <div className="le-cpage__hero-icon">
            <ListChecks />
          </div>
          <div className="le-cpage__hero-text">
            <div className="le-cpage__eyebrow">внутренняя политика</div>
            <h1 className="le-cpage__title">Правила организации</h1>
            <p className="le-cpage__sub">
              внутренние требования к договорам — это не нормы закона; их проверяет ИИ на странице документа
            </p>
          </div>
          <div className="le-cpage__hero-actions">
            <button type="button" className="le-btn le-btn--primary" onClick={() => open(blank())}>
              <Plus /> новый набор
            </button>
          </div>
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
            className="le-dv-card min-w-0"
            style={{ scrollMarginTop: 8 }}
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
      <div className="le-section-head le-section-head--accent">
        {icon}
        <span className="le-section-head__label">{title}</span>
        <span className="le-section-head__count">{items.length}</span>
      </div>
      {items.length === 0 ? (
        <span style={{ fontSize: 12, color: "var(--color-text-muted)" }}>{empty}</span>
      ) : (
        <ul className="le-pb-list">
          {items.map((p) => (
            <li key={p.id} className={"le-pb-row" + (p.id === activeId ? " le-pb-row--active" : "")}>
              <button type="button" onClick={() => onOpen(p)} className="le-pb-row__main">
                <span className="le-pb-row__name">{p.name || "Без названия"}</span>
                <span className="le-pb-row__meta">правил: {p.rules.length}</span>
              </button>
              {onCopy && (
                <button
                  type="button"
                  onClick={() => onCopy(p)}
                  className="le-btn le-btn--ghost le-pb-row__action"
                >
                  из шаблона
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
