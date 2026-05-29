import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Clock,
  ChevronLeft,
  ExternalLink,
  AlertCircle,
  Loader2,
  History,
  Bell,
  BellOff,
} from "lucide-react";
import { articleApi } from "@/features/graph/api/articleApi";
import { subscriptionApi } from "@/features/subscription/api/subscriptionApi";
import type { ArticleHistoryDTO, AmendmentEdgeDTO } from "@/entities/graph";

type Status = "loading" | "ready" | "notfound" | "error";

export default function ArticlePage() {
  const params = useParams<{ lawCode: string; number: string }>();
  const navigate = useNavigate();
  const lawCode = params.lawCode ?? "";
  const number = params.number ?? "";

  const [data, setData] = useState<ArticleHistoryDTO | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [subId, setSubId] = useState<string | null>(null);
  const [subBusy, setSubBusy] = useState(false);

  useEffect(() => {
    if (!lawCode || !number) return;
    setStatus("loading");
    setSubId(null);
    articleApi
      .history(lawCode, number)
      .then((res) => {
        setData(res);
        setStatus("ready");
      })
      .catch((err) => {
        if (err?.response?.status === 404) setStatus("notfound");
        else setStatus("error");
      });
    subscriptionApi
      .list()
      .then((list) => {
        const existing = list.find(
          (s) => s.lawCode === lawCode && s.articleNumber === number,
        );
        setSubId(existing?.id ?? null);
      })
      .catch(() => {});
  }, [lawCode, number]);

  const toggleSubscription = async () => {
    if (subBusy) return;
    setSubBusy(true);
    try {
      if (subId) {
        await subscriptionApi.remove(subId);
        setSubId(null);
      } else {
        const created = await subscriptionApi.create(lawCode, number);
        setSubId(created.id);
      }
    } catch {

    } finally {
      setSubBusy(false);
    }
  };

  const orderedAmendments = useMemo(() => {
    if (!data?.amendments) return [];
    return [...data.amendments].sort((a, b) => {
      const da = bestDate(a);
      const db = bestDate(b);
      if (!da && !db) return 0;
      if (!da) return 1;
      if (!db) return -1;
      return db.localeCompare(da);
    });
  }, [data]);

  const allDates = useMemo(() => {
    const dates = orderedAmendments
      .map((a) => bestDate(a))
      .filter((d): d is string => !!d);
    return dates;
  }, [orderedAmendments]);

  const [sliderIdx, setSliderIdx] = useState(0);
  useEffect(() => {
    setSliderIdx(0);
  }, [allDates.length]);

  const selectedDate = allDates[sliderIdx] ?? null;
  const cardRefs = useRef<Record<string, HTMLDivElement | null>>({});

  useEffect(() => {
    if (!selectedDate) return;
    const card = cardRefs.current[selectedDate];
    if (card) card.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [selectedDate]);

  return (
    <div
      className="flex-1 flex flex-col h-full overflow-hidden"
      style={{ background: "var(--color-bg)" }}
    >
      <div
        className="shrink-0 border-b"
        style={{ borderColor: "var(--color-border)" }}
      >
        <div className="max-w-[1100px] mx-auto px-6 pt-8 pb-5">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-1.5 mb-4 cursor-pointer transition-colors"
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "10px",
              letterSpacing: "0.1em",
              color: "var(--color-text-muted)",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = "var(--color-accent)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = "var(--color-text-muted)";
            }}
          >
            <ChevronLeft size={12} /> НАЗАД
          </button>

          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div className="flex items-center gap-3 min-w-0">
              <div
                className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0"
                style={{
                  background: "var(--color-accent-glow)",
                  border: "1px solid rgba(139,92,246,0.18)",
                }}
              >
                <Clock size={16} style={{ color: "var(--color-accent)" }} />
              </div>
              <div className="min-w-0">
                <div
                  style={{
                    fontFamily: "'IBM Plex Mono', monospace",
                    fontSize: "10px",
                    letterSpacing: "0.12em",
                    color: "var(--color-accent)",
                    textTransform: "uppercase",
                    fontWeight: 600,
                  }}
                >
                  Машина времени · ст. {number} · {lawCode}
                </div>
                <h1
                  style={{
                    fontFamily: "'IBM Plex Serif', serif",
                    fontSize: "22px",
                    fontWeight: 500,
                    margin: "2px 0 0 0",
                    color: "var(--color-text)",
                    letterSpacing: "-0.01em",
                  }}
                >
                  {data?.article?.title || `Статья ${number}`}
                </h1>
              </div>
            </div>

            {status === "ready" && (
              <button
                onClick={toggleSubscription}
                disabled={subBusy}
                className="flex items-center gap-2 px-3.5 py-2 rounded-xl cursor-pointer transition-all duration-150"
                style={{
                  fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: "10px",
                  letterSpacing: "0.08em",
                  fontWeight: 600,
                  background: subId
                    ? "var(--color-accent-glow)"
                    : "var(--color-surface)",
                  border: `1px solid ${subId ? "rgba(139,92,246,0.35)" : "var(--color-border)"}`,
                  color: subId
                    ? "var(--color-accent)"
                    : "var(--color-text-muted)",
                  opacity: subBusy ? 0.6 : 1,
                }}
                title={
                  subId
                    ? "Отписаться · больше не получать уведомления"
                    : "Подписаться · уведомим при изменении"
                }
              >
                {subBusy ? (
                  <Loader2 size={12} className="animate-spin" />
                ) : subId ? (
                  <Bell size={12} />
                ) : (
                  <BellOff size={12} />
                )}
                {subId ? "ОТСЛЕЖИВАЕТСЯ" : "ОТСЛЕЖИВАТЬ"}
              </button>
            )}
          </div>

          {status === "ready" && data && (
            <Timeline
              amendments={orderedAmendments}
              selectedIdx={sliderIdx}
              onSelect={setSliderIdx}
              lastAmendmentDate={data.lastAmendmentDate}
            />
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-[1100px] mx-auto px-6 py-8 grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-8">
          <section>
            {status === "loading" && (
              <div className="flex items-center gap-2 py-12 text-[var(--color-text-muted)]">
                <Loader2 size={16} className="animate-spin" /> загрузка...
              </div>
            )}
            {status === "notfound" && (
              <NotFound code={lawCode} number={number} />
            )}
            {status === "error" && <ErrorState />}
            {status === "ready" && data && (
              <ArticleBody article={data.article} />
            )}
          </section>

          {status === "ready" && data && (
            <aside className="lg:sticky lg:top-4 lg:self-start flex flex-col gap-3">
              <AmendmentList
                amendments={orderedAmendments}
                cardRefs={cardRefs}
                selectedDate={selectedDate}
                onClick={(d) => {
                  const idx = allDates.indexOf(d);
                  if (idx >= 0) setSliderIdx(idx);
                }}
              />
            </aside>
          )}
        </div>
      </div>
    </div>
  );
}

function Timeline({
  amendments,
  selectedIdx,
  onSelect,
  lastAmendmentDate,
}: {
  amendments: AmendmentEdgeDTO[];
  selectedIdx: number;
  onSelect: (idx: number) => void;
  lastAmendmentDate?: string;
}) {
  if (amendments.length === 0) {
    return (
      <div
        className="mt-5 px-4 py-3 rounded-xl"
        style={{
          background: "var(--color-surface)",
          border: "1px dashed var(--color-border)",
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "11px",
          color: "var(--color-text-light)",
          letterSpacing: "0.04em",
        }}
      >
        Поправок не зафиксировано
        {lastAmendmentDate &&
          ` · в редакции с ${formatDate(lastAmendmentDate)}`}
      </div>
    );
  }

  const dates = amendments.map(bestDate).filter((d): d is string => !!d);
  const min = dates[dates.length - 1];
  const max = dates[0];

  return (
    <div className="mt-6">
      <div className="flex items-center justify-between mb-2">
        <span
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "10px",
            letterSpacing: "0.08em",
            color: "var(--color-text-muted)",
          }}
        >
          ТАЙМЛАЙН ПОПРАВОК · {amendments.length}
        </span>
        <span
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "10px",
            color: "var(--color-accent)",
          }}
        >
          {formatDate(dates[selectedIdx] ?? max)}
        </span>
      </div>
      <input
        type="range"
        min={0}
        max={Math.max(0, amendments.length - 1)}
        value={selectedIdx}
        onChange={(e) => onSelect(parseInt(e.target.value, 10))}
        className="w-full cursor-pointer"
        style={{ accentColor: "var(--color-accent)" }}
      />
      <div
        className="flex items-center justify-between mt-1"
        style={{
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "9px",
          color: "var(--color-text-light)",
          letterSpacing: "0.04em",
        }}
      >
        <span>самая ранняя · {min ? formatDate(min) : "—"}</span>
        <span>последняя · {max ? formatDate(max) : "—"}</span>
      </div>
    </div>
  );
}

function ArticleBody({ article }: { article: ArticleHistoryDTO["article"] }) {
  return (
    <article
      className="rounded-2xl p-6"
      style={{
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
      }}
    >
      <div
        className="flex items-center gap-2 mb-3"
        style={{
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "10px",
          letterSpacing: "0.1em",
          color: "var(--color-text-muted)",
          textTransform: "uppercase",
        }}
      >
        <span>текущая редакция</span>
        {article.lastAmendmentDate && (
          <>
            <span>·</span>
            <span style={{ color: "var(--color-accent)" }}>
              изменена {formatDate(article.lastAmendmentDate)}
            </span>
          </>
        )}
      </div>

      {article.body ? (
        <div
          style={{
            fontFamily: "'IBM Plex Serif', serif",
            fontSize: "14.5px",
            lineHeight: 1.7,
            color: "var(--color-text)",
            whiteSpace: "pre-wrap",
          }}
        >
          {article.body}
        </div>
      ) : (
        <div
          style={{
            fontFamily: "'IBM Plex Sans', sans-serif",
            fontSize: "13px",
            color: "var(--color-text-muted)",
          }}
        >
          Текст статьи не извлечён.
        </div>
      )}

      {article.sourceUri && (
        <div
          className="mt-5 pt-4"
          style={{ borderTop: "1px solid var(--color-border)" }}
        >
          <a
            href={
              article.sourceUri + (article.anchor ? `#${article.anchor}` : "")
            }
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 cursor-pointer"
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "10px",
              letterSpacing: "0.08em",
              color: "var(--color-accent)",
            }}
          >
            ОТКРЫТЬ НА ADILET <ExternalLink size={11} />
          </a>
        </div>
      )}
    </article>
  );
}

function AmendmentList({
  amendments,
  cardRefs,
  selectedDate,
  onClick,
}: {
  amendments: AmendmentEdgeDTO[];
  cardRefs: React.MutableRefObject<Record<string, HTMLDivElement | null>>;
  selectedDate: string | null;
  onClick: (date: string) => void;
}) {
  return (
    <>
      <div
        className="flex items-center gap-2 px-1"
        style={{
          color: "var(--color-text-muted)",
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "10px",
          letterSpacing: "0.12em",
          fontWeight: 600,
        }}
      >
        <History size={12} /> ИСТОРИЯ ИЗМЕНЕНИЙ · {amendments.length}
      </div>
      {amendments.length === 0 && (
        <div
          className="px-4 py-6 rounded-2xl text-center"
          style={{
            background: "var(--color-surface)",
            border: "1px dashed var(--color-border)",
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "11px",
            color: "var(--color-text-light)",
          }}
        >
          поправок не зафиксировано
        </div>
      )}
      {amendments.map((a, i) => {
        const date = bestDate(a);
        const isSelected = date && date === selectedDate;
        return (
          <div
            key={`${date ?? "undated"}-${i}`}
            ref={(el) => {
              if (date) cardRefs.current[date] = el;
            }}
            onClick={() => date && onClick(date)}
            className="rounded-xl p-3.5 cursor-pointer transition-all duration-150"
            style={{
              background: "var(--color-surface)",
              border: `1px solid ${isSelected ? "rgba(139,92,246,0.45)" : "var(--color-border)"}`,
              boxShadow: isSelected
                ? "0 0 0 3px rgba(139,92,246,0.08)"
                : undefined,
            }}
          >
            <div className="flex items-center justify-between gap-2 mb-1.5">
              <span
                style={{
                  fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: "10px",
                  letterSpacing: "0.06em",
                  color: "var(--color-accent)",
                  fontWeight: 600,
                }}
              >
                {date ? formatDate(date) : "дата не указана"}
              </span>
              {a.byLawSourceUri && (
                <a
                  href={a.byLawSourceUri + (a.anchor ? `#${a.anchor}` : "")}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={(e) => e.stopPropagation()}
                  className="shrink-0"
                  style={{ color: "var(--color-text-muted)" }}
                  title="Открыть текст изменения на adilet"
                >
                  <ExternalLink size={11} />
                </a>
              )}
            </div>
            <div
              style={{
                fontFamily: "'IBM Plex Sans', sans-serif",
                fontSize: "12.5px",
                lineHeight: 1.45,
                color: "var(--color-text)",
              }}
            >
              {a.byLawTitle ||
                a.byLawCode ||
                a.byInternalNumber ||
                "изменяющий акт"}
            </div>
            {a.contextSnippet && (
              <p
                style={{
                  fontFamily: "'IBM Plex Sans', sans-serif",
                  fontSize: "11.5px",
                  lineHeight: 1.5,
                  color: "var(--color-text-muted)",
                  margin: "4px 0 0 0",
                }}
              >
                {truncate(a.contextSnippet, 160)}
              </p>
            )}
          </div>
        );
      })}
    </>
  );
}

function NotFound({ code, number }: { code: string; number: string }) {
  return (
    <div
      className="rounded-2xl p-6 text-center"
      style={{
        background: "var(--color-surface)",
        border: "1px dashed var(--color-border)",
      }}
    >
      <div
        style={{
          fontFamily: "'IBM Plex Serif', serif",
          fontSize: "16px",
          color: "var(--color-text)",
          marginBottom: "4px",
        }}
      >
        Статья не найдена
      </div>
      <div
        style={{
          fontFamily: "'IBM Plex Sans', sans-serif",
          fontSize: "13px",
          color: "var(--color-text-muted)",
        }}
      >
        ст. {number} · {code} — нет в графе. Возможно, не прошла индексацию.
      </div>
    </div>
  );
}

function ErrorState() {
  return (
    <div
      className="flex items-center gap-2 px-4 py-3 rounded-xl"
      style={{
        background: "rgba(239,68,68,0.05)",
        border: "1px solid rgba(239,68,68,0.15)",
        color: "var(--color-error)",
      }}
    >
      <AlertCircle size={16} />
      <span
        style={{ fontFamily: "'IBM Plex Sans', sans-serif", fontSize: "13px" }}
      >
        Не удалось загрузить историю
      </span>
    </div>
  );
}

function bestDate(a: AmendmentEdgeDTO): string | undefined {
  return a.amendmentDate || a.effectiveFrom;
}

const MONTHS = [
  "янв",
  "фев",
  "мар",
  "апр",
  "мая",
  "июн",
  "июл",
  "авг",
  "сен",
  "окт",
  "ноя",
  "дек",
];

function formatDate(iso: string): string {
  const [y, m, d] = iso.split("-");
  if (!y || !m || !d) return iso;
  const month = MONTHS[parseInt(m, 10) - 1] ?? m;
  return `${parseInt(d, 10)} ${month} ${y}`;
}

function truncate(s: string, max: number): string {
  if (s.length <= max) return s;
  return s.slice(0, max).trimEnd() + "…";
}
