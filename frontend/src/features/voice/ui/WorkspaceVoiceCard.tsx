import { useEffect, useMemo, useRef, useState } from "react";
import {
  Play,
  Pause,
  Trash2,
  ChevronDown,
  ChevronRight,
  Loader2,
  AlertTriangle,
} from "lucide-react";
import type {
  VoiceAnalysis,
  VoiceMessageDTO,
  VoiceSegment,
} from "@/entities/voice";
import { voiceApi } from "@/features/voice/api/voiceApi";

interface Props {
  message: VoiceMessageDTO;
  isPlaying: boolean;
  onTogglePlay: () => void;
  onDelete?: () => void;

  onAnalyzed?: (message: VoiceMessageDTO) => void;
}

export function WorkspaceVoiceCard({
  message: initial,
  isPlaying,
  onTogglePlay,
  onDelete,
  onAnalyzed,
}: Props) {
  const [message, setMessage] = useState<VoiceMessageDTO>(initial);
  const [open, setOpen] = useState(false);
  const announcedRef = useRef<string | null>(null);

  useEffect(() => {
    setMessage(initial);
  }, [initial.id, initial.status, initial.updatedAt]);

  useEffect(() => {
    if (message.status === "ANALYZED" && announcedRef.current !== message.id) {
      announcedRef.current = message.id;
      onAnalyzed?.(message);
    }
  }, [message, onAnalyzed]);

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

  const segments = useMemo<VoiceSegment[]>(
    () =>
      message.transcriptJson
        ? (safeParse<VoiceSegment[]>(message.transcriptJson) ?? [])
        : [],
    [message.transcriptJson],
  );
  const analysis = useMemo<VoiceAnalysis | null>(
    () =>
      message.analysisJson
        ? safeParse<VoiceAnalysis>(message.analysisJson)
        : null,
    [message.analysisJson],
  );

  const transcriptText =
    message.transcript ??
    segments
      .map((s) => s.text)
      .join(" ")
      .trim();
  const inFlight = message.status !== "ANALYZED" && message.status !== "ERROR";

  return (
    <div className="le-voice-card">
      <div className="le-voice-card__row">
        <button
          type="button"
          onClick={onTogglePlay}
          className="le-voice-card__play"
          title={isPlaying ? "Пауза" : "Воспроизвести"}
        >
          {isPlaying ? (
            <Pause size={12} fill="currentColor" strokeWidth={0} />
          ) : (
            <Play
              size={12}
              fill="currentColor"
              strokeWidth={0}
              style={{ marginLeft: 1 }}
            />
          )}
        </button>

        <div className="le-voice-card__wave">
          <span
            className="le-voice-card__line"
            style={{
              background: isPlaying
                ? "linear-gradient(90deg, rgb(239,68,68) 0%, rgb(239,68,68) 50%, rgba(239,68,68,0.25) 50%, rgba(239,68,68,0.25) 100%)"
                : "rgba(239,68,68,0.45)",
            }}
          />
        </div>

        <span className="le-voice-card__meta">
          {formatDuration(message.durationMs)}
        </span>

        {onDelete && (
          <button
            type="button"
            onClick={onDelete}
            className="le-voice-card__del"
            title="Удалить"
          >
            <Trash2 size={11} />
          </button>
        )}
      </div>

      <div className="le-voice-card__head">
        <span className="le-voice-card__name">
          {message.fileName || "voice_message"}
        </span>
        <StatusChip status={message.status} />
      </div>

      {transcriptText && (
        <button
          type="button"
          className="le-voice-card__toggle"
          onClick={() => setOpen((o) => !o)}
        >
          {open ? <ChevronDown size={11} /> : <ChevronRight size={11} />}
          {open ? "свернуть транскрипт" : "показать транскрипт"}
        </button>
      )}

      {open && (
        <div className="le-voice-card__transcript">
          {segments.length > 0 ? (
            <div className="le-voice-card__segments">
              {segments.map((seg, i) => (
                <div key={i} className="le-voice-card__segment">
                  <span
                    className="le-voice-card__speaker"
                    style={{ color: speakerColor(seg.speaker) }}
                  >
                    {seg.speaker.replace("SPEAKER_", "#")}
                  </span>
                  <span className="le-voice-card__segment-text">
                    {seg.text}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="le-voice-card__plain">{transcriptText}</p>
          )}
        </div>
      )}

      {inFlight && !transcriptText && (
        <div className="le-voice-card__loading">
          <Loader2 size={12} className="le-spin" />
          транскрипция в процессе…
        </div>
      )}

      {analysis && analysis.kind !== "OTHER" && (
        <div
          className="le-voice-card__analysis"
          data-severity={analysis.severity?.toLowerCase()}
        >
          <div className="le-voice-card__analysis-head">
            <AlertTriangle size={12} />
            <span>
              {analysisLabel(analysis.kind)} ·{" "}
              {severityLabel(analysis.severity)}
            </span>
          </div>
          {analysis.summary && (
            <p className="le-voice-card__analysis-summary">
              {analysis.summary}
            </p>
          )}
          {analysis.articles?.length > 0 && (
            <div className="le-voice-card__chips">
              {analysis.articles.map((a, i) => (
                <span key={i} className="le-voice-card__chip" title={a.reason}>
                  {a.lawCode} ст. {a.number}
                </span>
              ))}
            </div>
          )}
        </div>
      )}

      {message.status === "ERROR" && (
        <p className="le-voice-card__error">
          {message.errorMessage || "Ошибка обработки"}
        </p>
      )}
    </div>
  );
}

function StatusChip({ status }: { status: VoiceMessageDTO["status"] }) {
  const label: Record<VoiceMessageDTO["status"], string> = {
    UPLOADED: "загружено",
    TRANSCRIBING: "транскрипция",
    TRANSCRIBED: "распознано",
    ANALYZING: "анализ",
    ANALYZED: "готово",
    ERROR: "ошибка",
  };
  return (
    <span
      className={`le-voice-card__status le-voice-card__status--${status.toLowerCase()}`}
    >
      {label[status]}
    </span>
  );
}

function formatDuration(ms: number | undefined): string {
  if (!ms || ms < 0) return "—";
  const total = Math.floor(ms / 1000);
  const m = Math.floor(total / 60)
    .toString()
    .padStart(1, "0");
  const s = (total % 60).toString().padStart(2, "0");
  return `${m}:${s}`;
}

function speakerColor(speaker: string): string {
  const palette = ["#7c3aed", "#14b8a6", "#f59e0b", "#ec4899", "#3b82f6"];
  let h = 0;
  for (const c of speaker) h = (h << 5) - h + c.charCodeAt(0);
  return palette[Math.abs(h) % palette.length];
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

function severityLabel(severity: string | undefined): string {
  if (!severity) return "";
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

function safeParse<T>(s: string): T | null {
  try {
    return JSON.parse(s) as T;
  } catch {
    return null;
  }
}
