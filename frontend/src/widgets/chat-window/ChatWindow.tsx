import { useState, useEffect, useRef, useCallback } from "react";
import {
  Send,
  Square,
  Paperclip,
  X,
  ChevronDown,
  Bot,
  AlertCircle,
  Loader2,
  Microscope,
  Download,
  FileText,
  ExternalLink,
} from "lucide-react";
import { VoiceRecorderButton, VoiceMessageCard } from "@/features/voice";
import type { VoiceMessageDTO } from "@/entities/voice";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useConversationStore } from "@/features/conversation/model/useConversationStore";
import { useStreamingMessage } from "@/features/message/model/useStreamingMessage";
import type {
  ThinkingStep,
  DeepAnalysisProgress,
  DocumentDraftResult,
} from "@/features/message/model/useStreamingMessage";
import { messageApi } from "@/features/message/api/messageApi";
import { documentApi } from "@/features/document/api/documentApi";
import { useExtractGraphsFromContent } from "@/features/graph/hooks/useExtractGraphsFromContent";
import { Spinner } from "@/shared/ui/Spinner";
import { MessageSkeleton } from "@/shared/ui/Skeleton";
import { cn } from "@/shared/lib/cn";
import type { MessageResponse } from "@/entities/message/types";
import { GraphView } from "@/widgets/graph-view";

type AttachedFileStatus = "READY" | "ERROR";

interface AttachedFile {
  documentId: string;
  name: string;
  status: AttachedFileStatus;
}

export function ChatWindow() {
  const { activeId, conversations, createConversation, setActive } =
    useConversationStore();
  const {
    streamingContent,
    isStreaming,
    error: streamError,
    thinkingSteps,
    deepProgress,
    draftResult,
    startStream,
    cancelStream,
    reset: resetStream,
  } = useStreamingMessage();
  const extractGraphs = useExtractGraphsFromContent();

  const activeConv = conversations.find((c) => c.id === activeId);

  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [input, setInput] = useState("");
  const [isLoadingMsgs, setIsLoading] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [showScrollBtn, setScrollBtn] = useState(false);
  const [attachedFile, setAttachedFile] = useState<AttachedFile | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [streamMsgId] = useState(() => `stream-${Date.now()}`);
  const [voiceMessages, setVoiceMessages] = useState<VoiceMessageDTO[]>([]);

  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const prevActiveId = useRef<string | null>(null);

  useEffect(() => {
    if (!activeId) {
      setMessages([]);
      return;
    }
    if (prevActiveId.current === activeId) return;
    prevActiveId.current = activeId;
    setIsLoading(true);
    resetStream();
    messageApi
      .list(activeId, 0, 50)
      .then((page) => setMessages(page.content))
      .catch(() => {})
      .finally(() => setIsLoading(false));
  }, [activeId, resetStream]);

  useEffect(() => {
    const lastMessage = messages[messages.length - 1];
    if (lastMessage?.role === "ASSISTANT" && lastMessage.content) {
      extractGraphs(lastMessage.content);
    }
  }, [messages, extractGraphs]);

  useEffect(() => {
    if (!isStreaming) return;
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [streamingContent, isStreaming]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  function handleScroll(e: React.UIEvent<HTMLDivElement>) {
    const el = e.currentTarget;
    setScrollBtn(el.scrollHeight - el.scrollTop - el.clientHeight > 200);
  }

  function handleInput(e: React.ChangeEvent<HTMLTextAreaElement>) {
    setInput(e.target.value);
    const el = e.target;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 180) + "px";
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = "";
    setIsUploading(true);
    try {
      const documentId = await documentApi.upload(file);
      setAttachedFile({ documentId, name: file.name, status: "READY" });
    } catch {

    } finally {
      setIsUploading(false);
    }
  }

  const handleSend = useCallback(
    async (overrideText?: string) => {
      const text = (overrideText ?? input).trim();
      if (!text || isSending || isStreaming) return;
      if (attachedFile && attachedFile.status === "ERROR") return;

      let convId = activeId;
      if (!convId) {
        try {
          const conv = await createConversation(
            text.split(/\s+/).slice(0, 6).join(" "),
          );
          convId = conv.id;
          setActive(conv.id);
        } catch {
          return;
        }
      }

      const userMsg: MessageResponse = {
        id: `local-${Date.now()}`,
        conversationId: convId,
        role: "USER",
        content: text,
        createdAt: new Date().toISOString(),
      };

      setMessages((prev) => [...prev, userMsg]);
      setInput("");
      if (inputRef.current) inputRef.current.style.height = "auto";

      const docId = attachedFile?.documentId ?? null;
      setAttachedFile(null);

      setIsSending(true);
      resetStream();

      try {
        const fullContent = await startStream({
          conversationId: convId,
          content: text,
          documentId: docId,
        });
        setMessages((prev) => [
          ...prev,
          {
            id: streamMsgId,
            conversationId: convId!,
            role: "ASSISTANT",
            content: fullContent,
            createdAt: new Date().toISOString(),
          },
        ]);
      } catch {

      } finally {
        setIsSending(false);
      }
    },
    [
      input,
      isSending,
      isStreaming,
      activeId,
      attachedFile,
      createConversation,
      setActive,
      startStream,
      resetStream,
      streamMsgId,
    ],
  );

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  const handleDeepAnalysis = useCallback(() => {
    if (
      !attachedFile ||
      attachedFile.status !== "READY" ||
      isSending ||
      isStreaming
    )
      return;
    handleSend(
      "Проведи идеальный глубокий анализ документа: подбери применимые нормы, разверни связанные законы и проверь на противоречия.",
    );
  }, [attachedFile, isSending, isStreaming, handleSend]);

  const showEmpty = !activeConv && !isLoadingMsgs;

  return (
    <div className="flex-1 flex flex-col h-full relative overflow-hidden bg-[var(--color-bg)]">

      <input
        ref={fileInputRef}
        type="file"
        accept=".pdf,.txt,.docx,.doc"
        className="hidden"
        onChange={handleFileChange}
      />

      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto flex flex-col"
      >
        {showEmpty ? (
          <EmptyState
            onAttachFile={() => fileInputRef.current?.click()}
            onSuggestion={(text) => {
              setInput(text);
              setTimeout(() => inputRef.current?.focus(), 0);
            }}
          />
        ) : (
          <div className="max-w-[760px] mx-auto w-full px-5 pt-10 pb-4 flex flex-col gap-7 lg:px-5 md:px-4 sm:px-3 xs:px-2 lg:pt-10 md:pt-8 sm:pt-6 xs:pt-4">
            {isLoadingMsgs && (
              <div className="space-y-4 py-6">
                {[...Array(3)].map((_, i) => (
                  <MessageSkeleton key={i} />
                ))}
              </div>
            )}

            {!isLoadingMsgs && messages.length === 0 && !isStreaming && (
              <div className="py-16 flex items-center justify-center">
                <p
                  className="label-mono text-center"
                  style={{
                    color: "var(--color-text-muted)",
                    letterSpacing: "0.1em",
                  }}
                >
                  Задайте первый вопрос
                </p>
              </div>
            )}

            {messages.map((msg, i) => {
              const prev = i > 0 ? messages[i - 1] : null;
              const question =
                msg.role === "ASSISTANT" && prev?.role === "USER"
                  ? prev.content
                  : undefined;
              return (
                <MessageBubble
                  key={msg.id}
                  message={msg}
                  animIndex={i}
                  question={question}
                  conversationTitle={activeConv?.title}
                />
              );
            })}

            {thinkingSteps.length > 0 && (
              <ThinkingIndicator
                steps={thinkingSteps}
                isStreaming={isStreaming}
              />
            )}

            {deepProgress.length > 0 && (
              <DeepAnalysisIndicator
                events={deepProgress}
                isStreaming={isStreaming}
              />
            )}

            {isStreaming && (
              <MessageBubble
                key="streaming"
                message={{
                  id: "streaming",
                  conversationId: activeId ?? "",
                  role: "ASSISTANT",
                  content: streamingContent,
                  createdAt: new Date().toISOString(),
                }}
                isStreaming
                animIndex={messages.length}
              />
            )}

            {draftResult && !isStreaming && (
              <DocumentDraftCard result={draftResult} />
            )}

            {voiceMessages.length > 0 && (
              <div className="flex flex-col gap-3">
                {voiceMessages.map((v) => (
                  <VoiceMessageCard key={v.id} message={v} />
                ))}
              </div>
            )}

            {streamError && !isStreaming && (
              <div
                className="flex items-center gap-2 text-[var(--color-error)] text-sm px-4 py-3 rounded-xl animate-fade-in"
                style={{
                  background: "rgba(239,68,68,0.05)",
                  border: "1px solid rgba(239,68,68,0.15)",
                }}
              >
                <AlertCircle size={16} className="shrink-0" />
                <span style={{ fontFamily: "'IBM Plex Sans', sans-serif" }}>
                  {streamError}
                </span>
              </div>
            )}

            <div ref={bottomRef} />
          </div>
        )}
      </div>

      {showScrollBtn && (
        <button
          onClick={() =>
            bottomRef.current?.scrollIntoView({ behavior: "smooth" })
          }
          className="absolute bottom-28 right-6 lg:right-6 md:right-4 sm:right-3 xs:right-3 w-9 h-9 rounded-full flex items-center justify-center text-[var(--color-text-muted)] hover:text-[var(--color-accent)] transition-colors cursor-pointer animate-fade-in"
          style={{
            background: "var(--color-surface)",
            border: "1px solid var(--color-border)",
            boxShadow: "0 2px 10px rgba(0,0,0,0.18)",
          }}
        >
          <ChevronDown size={16} />
        </button>
      )}

      <GraphView />

      <div
        className="px-4 pb-5 pt-3 shrink-0 lg:px-4 md:px-3 sm:px-2 xs:px-2"
        style={{ background: "var(--color-bg)" }}
      >
        <div style={{ maxWidth: "760px", margin: "0 auto" }}>

          {attachedFile && (
            <div
              className="flex items-center gap-2 mb-3 px-4 py-2 rounded-2xl animate-fade-in"
              style={{
                fontFamily: "'IBM Plex Mono', monospace",
                fontSize: "10px",
                letterSpacing: "0.06em",
                background:
                  attachedFile.status === "ERROR"
                    ? "rgba(239,68,68,0.1)"
                    : "var(--color-accent-glow)",
                border: `1px solid ${attachedFile.status === "ERROR" ? "rgba(239,68,68,0.25)" : "rgba(139,92,246,0.15)"}`,
                color:
                  attachedFile.status === "ERROR"
                    ? "var(--color-error)"
                    : "var(--color-accent)",
              }}
            >
              {attachedFile.status === "ERROR" ? (
                <AlertCircle size={11} className="shrink-0" />
              ) : (
                <Paperclip size={11} className="shrink-0" />
              )}
              <span className="truncate flex-1 max-w-[300px]">
                {attachedFile.name}
                {attachedFile.status === "ERROR" && " · ошибка"}
              </span>
              <button
                onClick={() => setAttachedFile(null)}
                className="ml-auto p-0.5 hover:opacity-70 transition-opacity cursor-pointer"
              >
                <X size={11} />
              </button>
            </div>
          )}

          <div
            className={cn(
              "flex items-end gap-3 rounded-3xl px-6 py-4",
              "border border-[var(--color-border)]",
              "transition-all duration-200",
              "focus-within:border-[var(--color-accent)] focus-within:shadow-[0_0_0_3px_rgba(139,92,246,0.08)]",
            )}
            style={{
              background: "var(--color-surface)",
              boxShadow: "0 4px 20px rgba(0,0,0,0.12)",
            }}
          >
            <textarea
              ref={inputRef}
              value={input}
              onChange={handleInput}
              onKeyDown={handleKeyDown}
              placeholder="Спросите, составьте или вставьте текст..."
              rows={1}
              className="flex-1 bg-transparent text-[var(--color-text)] leading-relaxed resize-none focus:outline-none py-1 min-h-[28px] max-h-[160px] overflow-y-auto"
              style={{
                fontFamily: "'IBM Plex Sans', sans-serif",
                fontSize: "15px",
              }}
            />

            <div className="flex items-center gap-2 shrink-0">
              <button
                onClick={() => !isUploading && fileInputRef.current?.click()}
                disabled={isUploading}
                className="w-10 h-10 flex items-center justify-center rounded-2xl text-[var(--color-text-muted)] hover:text-[var(--color-accent)] hover:bg-[var(--color-accent-glow)] transition-colors cursor-pointer"
                title="Прикрепить документ"
              >
                {isUploading ? (
                  <Loader2
                    size={16}
                    className="animate-spin"
                    style={{ color: "var(--color-accent)" }}
                  />
                ) : (
                  <Paperclip size={16} />
                )}
              </button>

              <VoiceRecorderButton
                conversationId={activeId ?? undefined}
                onUploaded={(v) => setVoiceMessages((prev) => [...prev, v])}
              />

              <button
                onClick={handleDeepAnalysis}
                disabled={
                  !attachedFile ||
                  attachedFile.status !== "READY" ||
                  isSending ||
                  isStreaming
                }
                className={cn(
                  "w-10 h-10 flex items-center justify-center rounded-2xl transition-colors cursor-pointer",
                  attachedFile?.status === "READY" && !isSending && !isStreaming
                    ? "text-[var(--color-accent)] hover:bg-[var(--color-accent-glow)]"
                    : "text-[var(--color-text-light)] cursor-not-allowed",
                )}
                title={
                  !attachedFile
                    ? "Сначала прикрепите документ"
                    : "Глубокий анализ: применимые нормы + связанные законы + проверка конфликтов"
                }
              >
                <Microscope size={16} />
              </button>

              {isStreaming ? (
                <button
                  onClick={cancelStream}
                  className="w-10 h-10 flex items-center justify-center rounded-2xl cursor-pointer shrink-0 transition-colors"
                  style={{
                    background: "rgba(239,68,68,0.1)",
                    color: "var(--color-error)",
                  }}
                  title="Остановить генерацию"
                >
                  <Square size={14} fill="currentColor" />
                </button>
              ) : (
                (() => {
                  const blockedByDoc = attachedFile?.status === "ERROR";
                  const canSend = !!input.trim() && !blockedByDoc;
                  return (
                    <button
                      onClick={() => handleSend()}
                      disabled={!canSend || isSending}
                      className={cn(
                        "w-10 h-10 flex items-center justify-center rounded-2xl transition-colors cursor-pointer shrink-0",
                        canSend
                          ? "bg-[var(--color-accent)] text-white hover:bg-[var(--color-accent-hover)]"
                          : "bg-[var(--color-surface-2)] text-[var(--color-text-light)] cursor-not-allowed",
                      )}
                      title={
                        blockedByDoc
                          ? "Не удалось обработать документ"
                          : "Отправить (Enter)"
                      }
                    >
                      {isSending ? <Spinner size="sm" /> : <Send size={16} />}
                    </button>
                  );
                })()
              )}
            </div>
          </div>

          <p
            className="text-center mt-2 label-mono"
            style={{ letterSpacing: "0.08em", opacity: 0.45, fontSize: "9px" }}
          >
            Проверяйте ссылки · не является юридической консультацией · Enter
            для отправки
          </p>
        </div>
      </div>
    </div>
  );
}

const PROFESSION_LABELS: Record<string, string> = {
  FAMILY_LAWYER: "Семейный юрист",
  REAL_ESTATE_LAWYER: "Юрист по недвижимости",
  CRIMINAL_LAWYER: "Уголовный адвокат",
  CORPORATE_LAWYER: "Корпоративный юрист",
  LABOR_LAWYER: "Юрист по трудовым спорам",
  GENERAL_LAWYER: "Юридический ассистент",
};

const TASK_LABELS: Record<string, string> = {
  DOCUMENT_AUDIT: "Аудит документа",
  DOCUMENT_QA: "Вопрос по документу",
  LAW_SEARCH: "Поиск по законодательству",
  SUMMARY: "Резюмирование",
  GENERAL_LEGAL_ADVICE: "Юридическая консультация",
  DOCUMENT_DRAFT: "Составление документа",
};

const DRAFT_STEP_LABELS: Record<string, string> = {
  "draft-laws": "Ищу применимые нормы",
  "draft-situation": "Создаю ситуацию в графе",
  "draft-link": "Привязываю законы к ситуации",
  "draft-generate": "Генерирую текст документа",
  "draft-template": "Создаю шаблон",
  "draft-save": "Сохраняю документ",
};

function ThinkingIndicator({
  steps,
  isStreaming,
}: {
  steps: ThinkingStep[];
  isStreaming: boolean;
}) {
  const classify = steps.find((s) => s.step === "classify");
  const retrieve = steps.find((s) => s.step === "retrieve");
  const draftSteps = steps.filter((s) => s.step in DRAFT_STEP_LABELS);

  return (
    <div className="flex gap-3 animate-fade-in">
      <div
        className="shrink-0 flex items-center justify-center w-7 h-7 rounded-full mt-0.5"
        style={{
          background: "var(--color-accent-glow)",
          border: "1px solid rgba(139,92,246,0.2)",
          flexShrink: 0,
        }}
      >
        <Bot size={13} style={{ color: "var(--color-accent)" }} />
      </div>
      <div className="flex flex-col gap-1.5 py-1.5">
        {classify && (
          <ThinkingRow
            label={
              classify.profession
                ? (PROFESSION_LABELS[classify.profession] ??
                  classify.profession)
                : ""
            }
            subLabel={
              classify.task ? (TASK_LABELS[classify.task] ?? classify.task) : ""
            }
            done={true}
          />
        )}
        {retrieve && (
          <ThinkingRow
            label={
              retrieve.found != null && retrieve.found > 0
                ? `Найдено ${retrieve.found} фрагментов`
                : "Контекст не требуется"
            }
            done={true}
          />
        )}
        {draftSteps.map((s, i) => (
          <ThinkingRow
            key={s.step + i}
            label={DRAFT_STEP_LABELS[s.step]}
            done={isStreaming ? i < draftSteps.length - 1 : true}
          />
        ))}
        {isStreaming && draftSteps.length === 0 && (
          <ThinkingRow label="Формирую ответ..." done={false} />
        )}
      </div>
    </div>
  );
}

const DEEP_PHASE_LABELS: Record<string, string> = {
  start: "Подготовка",
  primary_laws: "Прямые применимые нормы",
  articles: "Привязка статей к документу",
  related_laws_2: "Связанные нормы 2-го уровня",
  related_laws_3: "Связанные нормы 3-го уровня",
  conflicts: "Поиск противоречий",
  done: "Готово",
};

function DeepAnalysisIndicator({
  events,
  isStreaming,
}: {
  events: DeepAnalysisProgress[];
  isStreaming: boolean;
}) {
  const phaseOrder: string[] = [];
  const byPhase: Record<string, DeepAnalysisProgress> = {};
  for (const ev of events) {
    if (!(ev.phase in byPhase)) phaseOrder.push(ev.phase);
    byPhase[ev.phase] = ev;
  }
  const last = events[events.length - 1];
  const finished = last?.phase === "done";

  return (
    <div className="flex gap-3 animate-fade-in">
      <div
        className="shrink-0 flex items-center justify-center w-7 h-7 rounded-full mt-0.5"
        style={{
          background: "var(--color-accent-glow)",
          border: "1px solid rgba(139,92,246,0.2)",
          flexShrink: 0,
        }}
      >
        <Microscope size={13} style={{ color: "var(--color-accent)" }} />
      </div>
      <div className="flex flex-col gap-1.5 py-1.5 flex-1 max-w-[480px]">
        <span
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "10px",
            color: "var(--color-accent)",
            letterSpacing: "0.08em",
            textTransform: "uppercase",
          }}
        >
          Глубокий анализ
        </span>
        {phaseOrder
          .filter((p) => p !== "start" && p !== "done")
          .map((phase) => {
            const ev = byPhase[phase];
            const label = DEEP_PHASE_LABELS[phase] ?? phase;
            const ratio = ev.total > 0 ? Math.min(1, ev.done / ev.total) : 0;
            const phaseDone = finished || ev.done >= ev.total;
            return (
              <div key={phase} className="flex flex-col gap-1">
                <div className="flex items-center justify-between gap-2">
                  <span
                    style={{
                      fontFamily: "'IBM Plex Mono', monospace",
                      fontSize: "10px",
                      color: "var(--color-text-muted)",
                      letterSpacing: "0.04em",
                    }}
                  >
                    {label}
                  </span>
                  <span
                    style={{
                      fontFamily: "'IBM Plex Mono', monospace",
                      fontSize: "10px",
                      color: phaseDone
                        ? "var(--color-accent)"
                        : "var(--color-text-muted)",
                    }}
                  >
                    {ev.done}/{ev.total}
                  </span>
                </div>
                <div
                  style={{
                    height: "3px",
                    background: "var(--color-surface-2)",
                    borderRadius: "2px",
                    overflow: "hidden",
                  }}
                >
                  <div
                    style={{
                      height: "100%",
                      width: `${ratio * 100}%`,
                      background: "var(--color-accent)",
                      transition: "width 200ms ease",
                    }}
                  />
                </div>
                {ev.message && (
                  <span
                    style={{
                      fontFamily: "'IBM Plex Mono', monospace",
                      fontSize: "9px",
                      color: "var(--color-text-light)",
                      letterSpacing: "0.04em",
                    }}
                  >
                    {ev.message}
                  </span>
                )}
              </div>
            );
          })}
        {isStreaming && !finished && (
          <span
            style={{
              fontFamily: "'IBM Plex Mono', monospace",
              fontSize: "10px",
              color: "var(--color-text-muted)",
            }}
          >
            обход графа…
          </span>
        )}
      </div>
    </div>
  );
}

function ThinkingRow({
  label,
  subLabel,
  done,
}: {
  label: string;
  subLabel?: string;
  done: boolean;
}) {
  return (
    <div className="flex items-center gap-2">
      <span
        style={{
          width: "5px",
          height: "5px",
          borderRadius: "50%",
          flexShrink: 0,
          background: done ? "var(--color-accent)" : "var(--color-text-muted)",
          opacity: done ? 1 : 0.5,
        }}
      />
      <span
        style={{
          fontFamily: "'IBM Plex Mono', monospace",
          fontSize: "10px",
          color: "var(--color-text-muted)",
          letterSpacing: "0.04em",
        }}
      >
        {label}
        {subLabel && (
          <span style={{ color: "var(--color-accent)", marginLeft: "6px" }}>
            {subLabel}
          </span>
        )}
      </span>
    </div>
  );
}

function MessageBubble({
  message,
  isStreaming = false,
  animIndex,
  question,
  conversationTitle,
}: {
  message: MessageResponse;
  isStreaming?: boolean;
  animIndex: number;
  question?: string;
  conversationTitle?: string;
}) {
  const isUser = message.role === "USER";

  if (isUser) {
    return (
      <div
        className="flex justify-end animate-fade-in"
        style={{ animationDelay: `${Math.min(animIndex * 15, 150)}ms` }}
      >
        <div
          className="max-w-[72%] lg:max-w-[72%] md:max-w-[85%] sm:max-w-[90%] xs:max-w-[95%] px-6 py-4 lg:px-6 md:px-5 sm:px-4 xs:px-4 text-white"
          style={{
            background: "var(--color-accent)",
            borderRadius: "20px 20px 6px 20px",
            fontFamily: "'IBM Plex Sans', sans-serif",
            fontSize: "15px",
            lineHeight: "1.6",
          }}
        >
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
        </div>
      </div>
    );
  }

  const showExport = !isStreaming && !!message.content?.trim();

  async function handleExport() {
    const { exportAnswerToPdf } =
      await import("@/shared/lib/exportAnswerToPdf");
    exportAnswerToPdf({
      content: message.content,
      question,
      conversationTitle,
    });
  }

  return (
    <div
      className="flex gap-4 animate-fade-in"
      style={{
        animationDelay: `${Math.min(animIndex * 15, 150)}ms`,
        paddingLeft: "24px",
      }}
    >
      <div
        className="shrink-0 w-8 h-8 rounded-full flex items-center justify-center mt-1"
        style={{
          background: "var(--color-accent-glow)",
          border: "1px solid rgba(139,92,246,0.2)",
          flexShrink: 0,
        }}
      >
        <Bot size={14} style={{ color: "var(--color-accent)" }} />
      </div>
      <div
        className="flex-1 min-w-0 pt-1"
        style={{
          fontFamily: "'IBM Plex Sans', sans-serif",
          fontSize: "15px",
          lineHeight: "1.7",
          color: "var(--color-text)",
        }}
      >
        <MarkdownContent content={message.content} isStreaming={isStreaming} />
        {showExport && (
          <div className="mt-2.5 flex items-center gap-1.5">
            <button
              onClick={handleExport}
              className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg cursor-pointer transition-colors group"
              style={{
                fontFamily: "'IBM Plex Mono', monospace",
                fontSize: "10px",
                letterSpacing: "0.08em",
                color: "var(--color-text-muted)",
                background: "transparent",
                border: "1px solid transparent",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.color = "var(--color-accent)";
                e.currentTarget.style.background = "var(--color-accent-glow)";
                e.currentTarget.style.borderColor = "rgba(139,92,246,0.18)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.color = "var(--color-text-muted)";
                e.currentTarget.style.background = "transparent";
                e.currentTarget.style.borderColor = "transparent";
              }}
              title="Сформировать PDF-обоснование со ссылками и шапкой"
            >
              <Download size={11} />
              <span>СКАЧАТЬ PDF</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function MarkdownContent({
  content,
  isStreaming = false,
}: {
  content: string;
  isStreaming?: boolean;
}) {
  if (!content && isStreaming) {
    return (
      <span
        className="flex items-center gap-2 text-[var(--color-text-muted)] italic"
        style={{ fontSize: "13px" }}
      >
        <span className="w-1 h-1 rounded-full bg-[var(--color-accent)] pulse" />
        Думаю...
      </span>
    );
  }

  return (
    <div
      className={cn(
        "prose-legal markdown-body",
        isStreaming && "streaming-cursor",
      )}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }: { children?: React.ReactNode }) => (
            <h1 className="text-xl font-semibold text-[var(--color-text)] mt-4 mb-2 leading-tight">
              {children}
            </h1>
          ),
          h2: ({ children }: { children?: React.ReactNode }) => (
            <h2 className="text-lg font-semibold text-[var(--color-text)] mt-3 mb-2 leading-tight">
              {children}
            </h2>
          ),
          h3: ({ children }: { children?: React.ReactNode }) => (
            <h3 className="text-base font-semibold text-[var(--color-text)] mt-3 mb-1 leading-tight">
              {children}
            </h3>
          ),
          p: ({ children }: { children?: React.ReactNode }) => (
            <p className="my-2 leading-relaxed">{children}</p>
          ),
          ul: ({ children }: { children?: React.ReactNode }) => (
            <ul className="my-2 pl-5 list-disc space-y-1">{children}</ul>
          ),
          ol: ({ children }: { children?: React.ReactNode }) => (
            <ol className="my-2 pl-5 list-decimal space-y-1">{children}</ol>
          ),
          li: ({ children }: { children?: React.ReactNode }) => (
            <li className="leading-relaxed">{children}</li>
          ),
          strong: ({ children }: { children?: React.ReactNode }) => (
            <strong className="font-semibold text-[var(--color-text)]">
              {children}
            </strong>
          ),
          em: ({ children }: { children?: React.ReactNode }) => (
            <em className="italic text-[var(--color-accent)]">{children}</em>
          ),
          code: ({
            children,
            className,
          }: {
            children?: React.ReactNode;
            className?: string;
          }) => {
            const isInline = !className;
            return isInline ? (
              <code className="px-1.5 py-0.5 text-sm font-mono bg-[var(--color-surface-2)] text-[var(--color-accent)] rounded">
                {children}
              </code>
            ) : (
              <pre className="my-3 p-3 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl overflow-x-auto">
                <code className="text-sm font-mono text-[var(--color-text)]">
                  {children}
                </code>
              </pre>
            );
          },
          blockquote: ({ children }: { children?: React.ReactNode }) => (
            <blockquote className="my-3 pl-4 border-l-2 border-[var(--color-accent)] text-[var(--color-text-muted)] italic">
              {children}
            </blockquote>
          ),
          a: ({
            children,
            href,
          }: {
            children?: React.ReactNode;
            href?: string;
          }) => (
            <a
              href={href}
              className="text-[var(--color-accent)] hover:underline"
              target="_blank"
              rel="noopener noreferrer"
            >
              {children}
            </a>
          ),
          hr: () => <hr className="my-4 border-[var(--color-border)]" />,
          table: ({ children }: { children?: React.ReactNode }) => (
            <table className="my-3 w-full border-collapse border border-[var(--color-border)]">
              {children}
            </table>
          ),
          thead: ({ children }: { children?: React.ReactNode }) => (
            <thead className="bg-[var(--color-surface)]">{children}</thead>
          ),
          th: ({ children }: { children?: React.ReactNode }) => (
            <th className="px-3 py-2 text-left text-sm font-semibold border border-[var(--color-border)]">
              {children}
            </th>
          ),
          td: ({ children }: { children?: React.ReactNode }) => (
            <td className="px-3 py-2 text-sm border border-[var(--color-border)]">
              {children}
            </td>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}

function DocumentDraftCard({ result }: { result: DocumentDraftResult }) {
  return (
    <div
      className="animate-fade-in rounded-2xl p-4 flex flex-col gap-3"
      style={{
        background: "var(--color-accent-glow)",
        border: "1px solid rgba(139,92,246,0.2)",
      }}
    >
      <div className="flex items-center gap-2">
        <FileText
          size={14}
          style={{ color: "var(--color-accent)", flexShrink: 0 }}
        />
        <span
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: "10px",
            letterSpacing: "0.1em",
            color: "var(--color-accent)",
            textTransform: "uppercase",
          }}
        >
          Документ создан
        </span>
      </div>

      {result.lawCodes && result.lawCodes.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {result.lawCodes.map((code) => (
            <span
              key={code}
              style={{
                fontFamily: "'IBM Plex Mono', monospace",
                fontSize: "9px",
                padding: "2px 8px",
                borderRadius: "4px",
                background: "rgba(139,92,246,0.12)",
                color: "var(--color-accent)",
                border: "1px solid rgba(139,92,246,0.2)",
              }}
            >
              {code}
            </span>
          ))}
        </div>
      )}

      <div className="flex gap-2 flex-wrap">
        {result.templateId && (
          <a
            href="/templates"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs cursor-pointer transition-colors"
            style={{
              fontFamily: "'IBM Plex Sans', sans-serif",
              background: "var(--color-accent)",
              color: "#fff",
              textDecoration: "none",
            }}
          >
            <FileText size={11} />
            Открыть шаблон
          </a>
        )}
        {result.generatedDocId && (
          <a
            href="/templates"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs cursor-pointer transition-colors"
            style={{
              fontFamily: "'IBM Plex Sans', sans-serif",
              background: "transparent",
              color: "var(--color-accent)",
              border: "1px solid rgba(139,92,246,0.3)",
              textDecoration: "none",
            }}
          >
            <ExternalLink size={11} />
            Мои документы
          </a>
        )}
      </div>
    </div>
  );
}

function EmptyState({
  onAttachFile,
  onSuggestion,
}: {
  onAttachFile: () => void;
  onSuggestion: (text: string) => void;
}) {
  return (
    <div
      className="flex-1 flex flex-col items-center justify-center px-6 gap-9 animate-fade-in lg:px-6 md:px-4 sm:px-3 xs:px-2"
      style={{ minHeight: "400px" }}
    >
      <div className="text-center flex flex-col items-center">
        <div
          className="w-14 h-14 mb-5 rounded-2xl flex items-center justify-center"
          style={{
            background: "var(--color-accent-glow)",
            border: "1px solid rgba(139,92,246,0.15)",
          }}
        >
          <img
            src="/logo.png"
            alt="LE"
            style={{ width: "32px", height: "32px", objectFit: "contain" }}
          />
        </div>
        <h2
          style={{
            fontFamily: "'IBM Plex Serif', serif",
            fontSize: "28px",
            fontWeight: 500,
            color: "var(--color-text)",
            margin: 0,
            marginBottom: "8px",
            letterSpacing: "-0.02em",
          }}
        >
          Что сегодня в повестке?
        </h2>
        <p
          style={{
            fontFamily: "'IBM Plex Sans', sans-serif",
            fontSize: "14px",
            color: "var(--color-text-muted)",
            maxWidth: "360px",
            margin: "0 auto",
            lineHeight: 1.5,
          }}
        >
          Начните вопрос или загрузите документ для анализа.
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-w-[600px] w-full px-2">
        {SUGGESTIONS.map((s, i) => (
          <button
            key={i}
            onClick={() => onSuggestion(s.text)}
            className="text-left p-4 rounded-xl cursor-pointer transition-all duration-150 animate-fade-in"
            style={{
              background: "var(--color-surface)",
              border: "1px solid var(--color-border)",
              animationDelay: `${i * 50}ms`,
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = "var(--color-accent)";
              e.currentTarget.style.background = "var(--color-accent-glow)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = "var(--color-border)";
              e.currentTarget.style.background = "var(--color-surface)";
            }}
          >
            <div
              className="label-mono mb-1.5"
              style={{
                color: "var(--color-accent)",
                letterSpacing: "0.1em",
                fontSize: "9px",
              }}
            >
              {s.kind}
            </div>
            <p
              style={{
                fontFamily: "'IBM Plex Serif', serif",
                fontSize: "13px",
                lineHeight: 1.5,
                color: "var(--color-text)",
                margin: 0,
              }}
            >
              {s.text}
            </p>
          </button>
        ))}
      </div>

      <button
        onClick={onAttachFile}
        className="flex items-center gap-2 h-9 px-5 rounded-xl text-sm transition-colors cursor-pointer"
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          color: "var(--color-text-muted)",
          fontFamily: "'IBM Plex Sans', sans-serif",
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.borderColor = "var(--color-accent)";
          e.currentTarget.style.color = "var(--color-accent)";
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.borderColor = "var(--color-border)";
          e.currentTarget.style.color = "var(--color-text-muted)";
        }}
      >
        <Paperclip size={14} />
        Загрузить документ
      </button>
    </div>
  );
}

const SUGGESTIONS = [
  {
    kind: "Составить",
    text: "Изложите условия возмещения убытков простым языком.",
  },
  {
    kind: "Анализ",
    text: "Сравните лимиты ответственности во всех загруженных договорах.",
  },
  {
    kind: "Исследование",
    text: "Найдите прецеденты нарушения фидуциарных обязанностей.",
  },
  {
    kind: "Проверка",
    text: "Отметьте необычные условия расторжения в этом соглашении.",
  },
];
