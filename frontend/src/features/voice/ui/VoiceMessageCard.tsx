import { useEffect, useMemo, useRef, useState } from "react";
import { Mic, AlertTriangle, Play, Pause, FileText, Scale } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import type {
  VoiceMessageDTO,
  VoiceAnalysis,
  VoiceSegment,
} from "@/entities/voice";
import { voiceApi } from "@/features/voice/api/voiceApi";

interface Props {
  message: VoiceMessageDTO;
  onGenerateClaim?: (analysis: VoiceAnalysis, quote: string | null) => void;
  onCiteArticle?: (lawCode: string, number: string) => void;
}

export function VoiceMessageCard({
  message: initial,
  onGenerateClaim,
  onCiteArticle,
}: Props) {
  const [message, setMessage] = useState<VoiceMessageDTO>(initial);

  useEffect(() => {
    setMessage(initial);
  }, [initial.id]);

  useEffect(() => {
    if (message.status === "ANALYZED" || message.status === "ERROR") return;
    let cancelled = false;
    let delay = 1500;
    async function tick() {
      try {
        const next = await voiceApi.get(message.id);
        if (cancelled) return;
        setMessage(next);
        if (next.status !== "ANALYZED" && next.status !== "ERROR") {
          delay = Math.min(delay * 1.4, 8000);
          setTimeout(tick, delay);
        }
      } catch {
        if (!cancelled) setTimeout(tick, 4000);
      }
    }
    const handle = setTimeout(tick, delay);
    return () => {
      cancelled = true;
      clearTimeout(handle);
    };
  }, [message.id, message.status]);

  const analysis = useMemo<VoiceAnalysis | null>(
    () =>
      message.analysisJson
        ? safeParse<VoiceAnalysis>(message.analysisJson)
        : null,
    [message.analysisJson],
  );
  const segments = useMemo<VoiceSegment[]>(
    () =>
      message.transcriptJson
        ? (safeParse<VoiceSegment[]>(message.transcriptJson) ?? [])
        : [],
    [message.transcriptJson],
  );

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [playing, setPlaying] = useState(false);
  const [currentMs, setCurrentMs] = useState(0);
  const [selectedQuote, setSelectedQuote] = useState<string | null>(null);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    const onTime = () => setCurrentMs(audio.currentTime * 1000);
    const onEnd = () => setPlaying(false);
    audio.addEventListener("timeupdate", onTime);
    audio.addEventListener("ended", onEnd);
    return () => {
      audio.removeEventListener("timeupdate", onTime);
      audio.removeEventListener("ended", onEnd);
    };
  }, []);

  function togglePlay() {
    const audio = audioRef.current;
    if (!audio) return;
    if (playing) audio.pause();
    else audio.play();
    setPlaying(!playing);
  }

  return (
    <div className="flex flex-col gap-3 p-4 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]">

      <div className="flex items-center gap-2">
        <div
          className="w-8 h-8 rounded-full flex items-center justify-center"
          style={{ background: "var(--color-accent-glow)" }}
        >
          <Mic size={14} className="text-[var(--color-accent)]" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-sm font-medium text-[var(--color-text)] truncate">
            {message.fileName || "Голосовое сообщение"}
          </div>
          <div className="text-[10px] text-[var(--color-text-muted)] uppercase tracking-wider">
            <StatusPill status={message.status} /> ·{" "}
            {new Date(message.createdAt).toLocaleString("ru")}
          </div>
        </div>
        <button
          onClick={togglePlay}
          className="w-9 h-9 rounded-full inline-flex items-center justify-center bg-[var(--color-accent)] text-white hover:bg-[var(--color-accent-hover)] transition-colors cursor-pointer"
        >
          {playing ? <Pause size={14} /> : <Play size={14} />}
        </button>
        <audio
          ref={audioRef}
          src={voiceApi.audioUrl(message.id)}
          preload="none"
        />
      </div>

      {segments.length > 0 ? (
        <div className="flex flex-col gap-1.5 text-sm leading-relaxed">
          {segments.map((seg, i) => {
            const active = currentMs >= seg.startMs && currentMs <= seg.endMs;
            const isSelected = selectedQuote === seg.text;
            return (
              <div
                key={i}
                onClick={() => setSelectedQuote(isSelected ? null : seg.text)}
                className={cn(
                  "flex gap-2 px-2 py-1.5 rounded-lg cursor-pointer transition-colors",
                  active && "bg-[var(--color-accent-glow)]",
                  isSelected && "ring-1 ring-[var(--color-accent)]",
                )}
              >
                <span
                  className="shrink-0 font-mono text-[10px] uppercase tracking-wider mt-0.5"
                  style={{ color: speakerColor(seg.speaker) }}
                >
                  {seg.speaker.replace("SPEAKER_", "")}
                </span>
                <span className="flex-1 text-[var(--color-text)]">
                  {seg.text}
                </span>
              </div>
            );
          })}
        </div>
      ) : (
        <p className="text-sm text-[var(--color-text-muted)] italic">
          {message.transcript || "Транскрипт ещё не готов."}
        </p>
      )}

      {analysis && analysis.kind !== "OTHER" && (
        <div
          className="flex flex-col gap-2 p-3 rounded-xl"
          style={{
            background: severityBackground(analysis.severity),
            border: `1px solid ${severityBorder(analysis.severity)}`,
          }}
        >
          <div className="flex items-center gap-2">
            <AlertTriangle
              size={14}
              style={{ color: severityFg(analysis.severity) }}
            />
            <span
              className="text-sm font-semibold"
              style={{ color: severityFg(analysis.severity) }}
            >
              {analysisLabel(analysis.kind)} —{" "}
              {severityLabel(analysis.severity)}
            </span>
          </div>
          <p className="text-xs text-[var(--color-text-muted)]">
            {analysis.summary}
          </p>

          {analysis.articles?.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {analysis.articles.map((a, i) => (
                <button
                  key={i}
                  onClick={() => onCiteArticle?.(a.lawCode, a.number)}
                  className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-mono bg-[var(--color-surface)] text-[var(--color-accent)] hover:bg-[var(--color-accent)] hover:text-white transition-colors cursor-pointer"
                  title={a.reason}
                >
                  <Scale size={10} />
                  {a.lawCode} ст. {a.number}
                </button>
              ))}
            </div>
          )}

          {onGenerateClaim && (
            <button
              onClick={() =>
                onGenerateClaim(
                  analysis,
                  selectedQuote ?? analysis.quotes?.[0] ?? null,
                )
              }
              className="self-start inline-flex items-center gap-1.5 h-8 px-3 rounded-lg text-xs font-medium bg-[var(--color-accent)] text-white hover:bg-[var(--color-accent-hover)] transition-colors cursor-pointer"
            >
              <FileText size={12} />
              Сформировать заявление
            </button>
          )}
        </div>
      )}

      {message.status === "ERROR" && (
        <p className="text-xs text-[var(--color-error)]">
          {message.errorMessage}
        </p>
      )}
    </div>
  );
}

function StatusPill({ status }: { status: VoiceMessageDTO["status"] }) {
  const label = (
    {
      UPLOADED: "загружено",
      TRANSCRIBING: "транскрипция…",
      TRANSCRIBED: "распознано",
      ANALYZING: "анализ…",
      ANALYZED: "готово",
      ERROR: "ошибка",
    } as const
  )[status];
  return <span>{label}</span>;
}

function safeParse<T>(s: string): T | null {
  try {
    return JSON.parse(s) as T;
  } catch {
    return null;
  }
}

function speakerColor(speaker: string): string {
  const palette = ["#8b5cf6", "#22c55e", "#f59e0b", "#ec4899"];
  const idx = Math.abs(hashCode(speaker)) % palette.length;
  return palette[idx];
}

function hashCode(s: string) {
  let h = 0;
  for (const c of s) h = (h << 5) - h + c.charCodeAt(0);
  return h;
}

function analysisLabel(kind: string): string {
  return (
    (
      {
        THREAT: "Угроза",
        EXTORTION: "Вымогательство",
        FRAUD: "Признаки мошенничества",
        INSULT: "Оскорбление",
        DEFAMATION: "Клевета",
        OTHER: "Иное",
      } as Record<string, string>
    )[kind] ?? kind
  );
}

function severityLabel(severity: string): string {
  return (
    (
      {
        CRITICAL: "критично",
        HIGH: "высокая",
        MEDIUM: "средняя",
        LOW: "низкая",
      } as Record<string, string>
    )[severity] ?? severity
  );
}

function severityBackground(s: string): string {
  if (s === "CRITICAL") return "rgba(239,68,68,0.08)";
  if (s === "HIGH") return "rgba(249,115,22,0.08)";
  return "var(--color-accent-glow)";
}
function severityBorder(s: string): string {
  if (s === "CRITICAL") return "rgba(239,68,68,0.25)";
  if (s === "HIGH") return "rgba(249,115,22,0.25)";
  return "rgba(139,92,246,0.18)";
}
function severityFg(s: string): string {
  if (s === "CRITICAL") return "var(--color-error)";
  if (s === "HIGH") return "#ea580c";
  return "var(--color-accent)";
}
