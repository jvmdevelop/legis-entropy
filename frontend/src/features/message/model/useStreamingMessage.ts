import { useState, useRef, useCallback } from "react";
import { tokenStore } from "@/shared/api/client";
import type { ChatRequest } from "../api/messageApi";

export interface ThinkingStep {
  step: string;
  task?: string;
  retrieval?: string;
  profession?: string;
  found?: number;
  depth?: number;
  message?: string;
}

export interface DeepAnalysisProgress {
  phase: string;
  done: number;
  total: number;
  message: string;
}

export interface DocumentDraftResult {
  templateId?: string;
  generatedDocId?: string;
  situationId?: string;
  lawCodes?: string[];
}

export interface StreamState {
  streamingContent: string;
  isStreaming: boolean;
  error: string | null;
  thinkingSteps: ThinkingStep[];
  deepProgress: DeepAnalysisProgress[];
  draftResult: DocumentDraftResult | null;
}

export interface UseStreamingMessageReturn extends StreamState {
  startStream(req: ChatRequest): Promise<string>;
  cancelStream(): void;
  reset(): void;
}

export function useStreamingMessage(): UseStreamingMessageReturn {
  const [streamingContent, setStreamingContent] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [thinkingSteps, setThinkingSteps] = useState<ThinkingStep[]>([]);
  const [deepProgress, setDeepProgress] = useState<DeepAnalysisProgress[]>([]);
  const [draftResult, setDraftResult] = useState<DocumentDraftResult | null>(
    null,
  );
  const abortRef = useRef<AbortController | null>(null);

  const cancelStream = useCallback(() => {
    abortRef.current?.abort();
    setIsStreaming(false);
  }, []);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    setStreamingContent("");
    setIsStreaming(false);
    setError(null);
    setThinkingSteps([]);
    setDeepProgress([]);
    setDraftResult(null);
  }, []);

  const startStream = useCallback(async (req: ChatRequest): Promise<string> => {
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;

    setStreamingContent("");
    setIsStreaming(true);
    setError(null);
    setThinkingSteps([]);
    setDeepProgress([]);

    const token = tokenStore.get();
    let fullContent = "";

    try {
      const response = await fetch("/api/messages/chat/stream", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(req),
        signal: ctrl.signal,
      });

      if (!response.ok) {
        throw new Error(`Stream failed: ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error("No readable stream body");

      const decoder = new TextDecoder();
      let eventBuf = "";
      let currentEventType = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const raw = decoder.decode(value, { stream: true });
        const lines = raw.split("\n");

        for (const line of lines) {
          if (line.startsWith("event:")) {
            currentEventType = line.slice(6).trim();
          } else if (line.startsWith("data:")) {
            const text = line.slice(5);
            if (text === "[DONE]") break;
            if (eventBuf.length > 0) eventBuf += "\n";
            eventBuf += text;
          } else if (line === "" && eventBuf.length > 0) {
            if (currentEventType === "thinking") {
              tryAddThinkingStep(eventBuf, setThinkingSteps);
            } else if (currentEventType === "deep-analysis-progress") {
              tryAddDeepProgress(eventBuf, setDeepProgress);
            } else if (currentEventType === "document-draft-result") {
              trySetDraftResult(eventBuf, setDraftResult);
            } else if (currentEventType === "redaction") {
              fullContent = eventBuf;
              setStreamingContent(eventBuf);
            } else {
              fullContent += eventBuf;
              setStreamingContent((prev) => prev + eventBuf);
            }
            eventBuf = "";
            currentEventType = "";
          }
        }
      }

      if (eventBuf.length > 0) {
        if (currentEventType === "thinking") {
          tryAddThinkingStep(eventBuf, setThinkingSteps);
        } else if (currentEventType === "deep-analysis-progress") {
          tryAddDeepProgress(eventBuf, setDeepProgress);
        } else if (currentEventType === "document-draft-result") {
          trySetDraftResult(eventBuf, setDraftResult);
        } else if (currentEventType === "redaction") {
          fullContent = eventBuf;
          setStreamingContent(eventBuf);
        } else {
          fullContent += eventBuf;
          setStreamingContent((prev) => prev + eventBuf);
        }
      }
    } catch (err) {
      if ((err as Error).name === "AbortError") {

      } else {
        const msg = (err as Error).message ?? "Streaming error";
        setError(msg);
        throw err;
      }
    } finally {
      setIsStreaming(false);
    }

    return fullContent;
  }, []);

  return {
    streamingContent,
    isStreaming,
    error,
    thinkingSteps,
    deepProgress,
    draftResult,
    startStream,
    cancelStream,
    reset,
  };
}

function trySetDraftResult(
  raw: string,
  setResult: React.Dispatch<React.SetStateAction<DocumentDraftResult | null>>,
) {
  try {
    const result: DocumentDraftResult = JSON.parse(raw.trim());
    setResult(result);
  } catch {

  }
}

function tryAddThinkingStep(
  raw: string,
  setSteps: React.Dispatch<React.SetStateAction<ThinkingStep[]>>,
) {
  try {
    const step: ThinkingStep = JSON.parse(raw.trim());
    setSteps((prev) => [...prev, step]);
  } catch {

  }
}

function tryAddDeepProgress(
  raw: string,
  setProgress: React.Dispatch<React.SetStateAction<DeepAnalysisProgress[]>>,
) {
  try {
    const ev: DeepAnalysisProgress = JSON.parse(raw.trim());
    setProgress((prev) => [...prev, ev]);
  } catch {

  }
}
