import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { Building2, Loader2, MapPin, ShieldCheck } from "lucide-react";
import { playbooksApi, type Playbook, type PlaybookCheckResponse, type PlaybookRuleResult } from "../api/playbooksApi";
import { STATUS_META, STATUS_ORDER } from "../lib/options";
import { MONO, SANS, fieldStyle } from "./fieldStyle";

interface Props {
  documentId: string;
  onLocate: (clause: string | null, page: number | null) => void;
}

const POLICY = "var(--color-info)";

export function PlaybookCheckPanel({ documentId, onLocate }: Props) {
  const [own, setOwn] = useState<Playbook[]>([]);
  const [templates, setTemplates] = useState<Playbook[]>([]);
  const [picked, setPicked] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [checking, setChecking] = useState(false);
  const [result, setResult] = useState<PlaybookCheckResponse | null>(null);

  useEffect(() => {
    let alive = true;
    Promise.allSettled([playbooksApi.list(), playbooksApi.templates()]).then(([a, b]) => {
      if (!alive) return;
      setOwn(a.status === "fulfilled" ? a.value : []);
      setTemplates(b.status === "fulfilled" ? b.value : []);
      setLoaded(true);
    });
    return () => {
      alive = false;
    };
  }, []);

  const selected = picked || own[0]?.id || templates[0]?.id || "";

  const groups = useMemo(() => {
    const map = new Map<string, PlaybookRuleResult[]>();
    result?.results.forEach((r) => {
      const key = STATUS_META[r.status] ? r.status : "UNKNOWN";
      map.set(key, [...(map.get(key) ?? []), r]);
    });
    return STATUS_ORDER.filter((s) => map.has(s)).map((s) => ({ status: s, items: map.get(s)! }));
  }, [result]);

  const check = async () => {
    if (!selected) return;
    setChecking(true);
    try {
      setResult(await playbooksApi.check(documentId, selected));
    } catch {
      toast.error("Не удалось проверить документ по правилам");
    } finally {
      setChecking(false);
    }
  };

  return (
    <section
      className="flex flex-col gap-3 rounded-2xl p-4 min-w-0"
      style={{ fontFamily: SANS, background: "rgba(59,130,246,0.05)", border: "1px solid rgba(59,130,246,0.25)" }}
    >
      <div className="flex items-center gap-2 flex-wrap">
        <Building2 size={14} style={{ color: POLICY, flexShrink: 0 }} />
        <span style={{ fontFamily: MONO, fontSize: "10px", letterSpacing: "0.1em", color: POLICY, textTransform: "uppercase" }}>
          Внутренняя политика
        </span>
        <Link to="/playbooks" className="ml-auto text-xs" style={{ color: POLICY }}>
          правила
        </Link>
      </div>
      <span style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
        Проверка по правилам вашей организации — это не нарушения закона, а отклонения от внутренних требований.
      </span>

      {!loaded ? (
        <span className="flex items-center gap-2 text-xs" style={{ color: "var(--color-text-muted)" }}>
          <Loader2 size={12} className="animate-spin" /> Загрузка правил…
        </span>
      ) : own.length + templates.length === 0 ? (
        <span style={{ fontSize: "12px", color: "var(--color-text-muted)" }}>
          Наборов правил нет. <Link to="/playbooks" style={{ color: POLICY }}>Создайте первый</Link>.
        </span>
      ) : (
        <div className="flex flex-col gap-2">
          <select value={selected} onChange={(e) => setPicked(e.target.value)} style={fieldStyle}>
            {own.length > 0 && (
              <optgroup label="Мои правила">
                {own.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </optgroup>
            )}
            {templates.length > 0 && (
              <optgroup label="Шаблоны">
                {templates.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </optgroup>
            )}
          </select>
          <button
            type="button"
            onClick={() => void check()}
            disabled={checking || !selected}
            className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl text-xs cursor-pointer disabled:opacity-60"
            style={{ background: POLICY, color: "#fff" }}
          >
            {checking ? <Loader2 size={12} className="animate-spin" /> : <ShieldCheck size={12} />}
            {checking ? "Проверяем… до 3 минут" : "Проверить"}
          </button>
        </div>
      )}

      {result && (
        <div className="flex flex-col gap-3">
          {result.summary && (
            <p className="m-0" style={{ fontSize: "12px", lineHeight: 1.5, whiteSpace: "pre-wrap", color: "var(--color-text)" }}>
              {result.summary}
            </p>
          )}
          {groups.length === 0 && (
            <span style={{ fontSize: "12px", color: "var(--color-text-muted)" }}>Нет результатов</span>
          )}
          {groups.map((g) => (
            <div key={g.status} className="flex flex-col gap-1.5">
              <span style={{ fontFamily: MONO, fontSize: "10px", color: STATUS_META[g.status].color }}>
                {STATUS_META[g.status].label} · {g.items.length}
              </span>
              {g.items.map((r, i) => (
                <ResultRow key={`${r.ruleId}-${i}`} r={r} onLocate={onLocate} />
              ))}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function ResultRow({ r, onLocate }: { r: PlaybookRuleResult; onLocate: Props["onLocate"] }) {
  const meta = STATUS_META[r.status] ?? STATUS_META.UNKNOWN;
  const where = [r.clause ? `п. ${r.clause}` : null, r.page ? `стр. ${r.page}` : null].filter(Boolean).join(" · ");

  return (
    <div
      className="flex flex-col gap-1 rounded-xl p-2.5 min-w-0"
      style={{ background: meta.background, borderLeft: `3px solid ${meta.color}`, overflowWrap: "anywhere" }}
    >
      <span style={{ fontSize: "12px", fontWeight: 500, color: "var(--color-text)" }}>{r.rule}</span>
      {r.explanation && (
        <span style={{ fontSize: "11.5px", lineHeight: 1.45, color: "var(--color-text-muted)" }}>{r.explanation}</span>
      )}
      {r.quote && (
        <span
          style={{
            fontSize: "11.5px",
            lineHeight: 1.45,
            fontStyle: "italic",
            color: "var(--color-text)",
            borderLeft: "2px solid var(--color-border)",
            paddingLeft: 8,
          }}
        >
          «{r.quote}»
        </span>
      )}
      {where && (
        <button
          type="button"
          onClick={() => onLocate(r.clause, r.page)}
          className="self-start flex items-center gap-1 cursor-pointer"
          style={{ fontFamily: MONO, fontSize: "10px", color: POLICY }}
        >
          <MapPin size={10} /> {where}
        </button>
      )}
    </div>
  );
}
