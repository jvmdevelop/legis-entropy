import { useCallback, useEffect, useMemo, useState } from "react";
import { isAxiosError } from "axios";
import { toast } from "sonner";
import { documentApi } from "@/features/document/api/documentApi";
import { editsApi, type ApplyOptions } from "@/features/document-edits/api/editsApi";
import type { DocumentStatusResponse } from "@/entities/document/types";
import type { EditProposal } from "@/features/message/model/useStreamingMessage";
import { segmentText } from "../lib/segmentText";

export type SuggestionStatus = "pending" | "accepted" | "rejected";

export interface ViewerSuggestion {
  proposal: EditProposal;
  status: SuggestionStatus;
  anchor?: number;
  fromSelection?: boolean;
}

export interface AppliedResult {
  documentId: string;
  fileName: string | null;
  message: string;
  inPlace: boolean;
}

const PROCESSING = new Set(["UPLOADING", "INDEXING"]);

function uniqueId(id: string | undefined, taken: Set<string>): string {
  const baseId = id || `sel-${Date.now()}`;
  let candidate = baseId;
  let n = 1;
  while (taken.has(candidate)) candidate = `${baseId}-${n++}`;
  return candidate;
}

export function useDocumentViewer(documentId: string, applyOptions: ApplyOptions = {}) {
  const inPlace = applyOptions.inPlace ?? false;
  const graphId = applyOptions.graphId;
  const [doc, setDoc] = useState<DocumentStatusResponse | null>(null);
  const [text, setText] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [items, setItems] = useState<ViewerSuggestion[]>([]);
  const [summary, setSummary] = useState<string | null>(null);
  const [problems, setProblems] = useState<number | null>(null);
  const [checking, setChecking] = useState(false);
  const [checked, setChecked] = useState(false);
  const [applying, setApplying] = useState(false);
  const [rewriting, setRewriting] = useState(false);
  const [activeId, setActiveId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const load = async () => {
      try {
        const status = await documentApi.getStatus(documentId);
        if (cancelled) return;
        setDoc(status);
        if (PROCESSING.has(status.status)) {
          timer = setTimeout(load, 4000);
          return;
        }
        if (status.status === "ERROR") {
          setLoadError(status.errorMessage || "Документ не удалось обработать");
          return;
        }
        const body = await documentApi.text(documentId);
        if (!cancelled) setText(body);
      } catch {
        if (!cancelled) setLoadError("Не удалось загрузить документ");
      }
    };
    void load();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [documentId]);

  const layout = useMemo(
    () =>
      segmentText(
        text ?? "",
        items
          .filter((s) => s.status !== "rejected")
          .map((s) => ({
            id: s.proposal.id,
            clause: s.proposal.clause,
            original: s.proposal.original,
            anchor: s.anchor,
          })),
      ),
    [text, items],
  );

  const check = useCallback(async () => {
    setChecking(true);
    try {
      const res = await editsApi.suggest(documentId);
      setSummary(res.summary ?? null);
      setProblems(typeof res.problems === "number" ? res.problems : null);
      setItems((prev) => {
        const kept = prev.filter((s) => s.fromSelection);
        const taken = new Set(kept.map((s) => s.proposal.id));
        const fresh = (res.proposals ?? []).map((p) => {
          const id = uniqueId(p.id, taken);
          taken.add(id);
          return { proposal: { ...p, id }, status: "pending" as const };
        });
        return [...fresh, ...kept];
      });
      setChecked(true);
      const count = res.proposals?.length ?? 0;
      if (count) toast.success(`Найдено предложений: ${count}`);
      else toast.success("Замечаний не найдено");
    } catch {
      toast.error("Не удалось проверить документ");
    } finally {
      setChecking(false);
    }
  }, [documentId]);

  const setStatus = useCallback((id: string, status: SuggestionStatus) => {
    setItems((prev) => prev.map((s) => (s.proposal.id === id ? { ...s, status } : s)));
  }, []);

  const rewrite = useCallback(
    async (start: number, end: number, instruction?: string) => {
      if (text === null) return false;
      const original = text.slice(start, end);
      setRewriting(true);
      try {
        const p = await editsApi.rewrite(documentId, {
          original,
          instruction: instruction?.trim() || undefined,
        });
        if (!p || !p.replacement) {
          toast.info("ИИ не предложил правку для этого фрагмента");
          return false;
        }
        const newId = `${p.id || "sel"}-${Date.now().toString(36)}`;
        setItems((prev) => [
          ...prev,
          {
            proposal: { ...p, id: newId, original: p.original || original },
            status: "pending",
            anchor: p.original && p.original !== original ? undefined : start,
            fromSelection: true,
          },
        ]);
        setActiveId(newId);
        return true;
      } catch (e) {
        if (isAxiosError(e) && e.response?.status === 422) {
          toast.info("ИИ не предложил правку для этого фрагмента");
        } else {
          toast.error("Не удалось получить правку");
        }
        return false;
      } finally {
        setRewriting(false);
      }
    },
    [documentId, text],
  );

  const accepted = useMemo(() => items.filter((s) => s.status === "accepted"), [items]);

  const apply = useCallback(async (): Promise<AppliedResult | null> => {
    if (!accepted.length) return null;
    setApplying(true);
    try {
      const res = await editsApi.apply(
        documentId,
        accepted.map((s) => s.proposal),
        { inPlace, graphId },
      );
      if (!res.documentId) {
        toast.error("Ни одна правка не применена");
        return null;
      }
      const skipped = res.skipped?.length ?? 0;
      const message = res.inPlace
        ? `Документ обновлён: применено правок ${res.applied?.length ?? 0}` +
          (skipped ? `, пропущено ${skipped}` : "") +
          (res.graphLinksRemoved ? `. Устаревших связей в графе убрано: ${res.graphLinksRemoved}` : "")
        : `Применено правок: ${res.applied?.length ?? 0}` +
          (skipped ? `, пропущено: ${skipped}` : "") +
          (res.fileName ? `. Создан «${res.fileName}»` : "");
      return { documentId: res.documentId, fileName: res.fileName, message, inPlace: !!res.inPlace };
    } catch {
      toast.error("Не удалось применить правки");
      return null;
    } finally {
      setApplying(false);
    }
  }, [accepted, documentId, inPlace, graphId]);

  return {
    inPlace,
    doc,
    text,
    loadError,
    items,
    layout,
    summary,
    problems,
    checking,
    checked,
    applying,
    rewriting,
    activeId,
    setActiveId,
    accepted,
    check,
    rewrite,
    apply,
    accept: (id: string) => setStatus(id, "accepted"),
    reject: (id: string) => setStatus(id, "rejected"),
    reset: (id: string) => setStatus(id, "pending"),
  };
}

export type DocumentViewerModel = ReturnType<typeof useDocumentViewer>;
