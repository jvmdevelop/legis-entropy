import { tokenStore } from "@/shared/api/client";

export interface BrainProgress {
  phase: string;
  done: number;
  total: number;
  message: string;
}

export interface BrainStreamHandlers {
  onChunk?: (text: string, full: string) => void;
  onProgress?: (p: BrainProgress) => void;
  onThinking?: (step: Record<string, unknown>) => void;
}

export class BrainStreamError extends Error {}

export async function streamBrain(
  body: Record<string, unknown>,
  handlers: BrainStreamHandlers = {},
  signal?: AbortSignal,
): Promise<string> {
  const token = tokenStore.get();
  const response = await fetch("/api/brain/chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal,
  });
  if (!response.ok || !response.body) {
    throw new BrainStreamError(`Сервис анализа недоступен (${response.status})`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let event = "";
  let data: string[] = [];
  let full = "";

  const dispatch = () => {
    if (!data.length) {
      event = "";
      return;
    }
    const payload = data.join("\n");
    data = [];
    const name = event;
    event = "";
    if (name === "error") throw new BrainStreamError(payload || "Ошибка анализа");
    if (name === "done") return;
    if (name === "redaction") {
      full = payload;
      handlers.onChunk?.("", full);
      return;
    }
    if (name === "deep-analysis-progress" || name === "thinking") {
      try {
        const parsed = JSON.parse(payload);
        if (name === "thinking") handlers.onThinking?.(parsed);
        else handlers.onProgress?.(parsed as BrainProgress);
      } catch {
        return;
      }
      return;
    }
    if (name) return;
    full += payload;
    handlers.onChunk?.(payload, full);
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let nl: number;
    while ((nl = buffer.indexOf("\n")) >= 0) {
      const line = buffer.slice(0, nl).replace(/\r$/, "");
      buffer = buffer.slice(nl + 1);
      if (line === "") dispatch();
      else if (line.startsWith("event:")) event = line.slice(6).trim();
      else if (line.startsWith("data:")) data.push(line.slice(5));
    }
  }
  if (buffer.startsWith("data:")) data.push(buffer.slice(5));
  dispatch();
  return full;
}
