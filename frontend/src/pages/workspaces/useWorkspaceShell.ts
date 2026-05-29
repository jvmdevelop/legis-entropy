import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { apiClient } from "@/shared/api/client";
import { useAuthStore } from "@/features/auth/model/useAuthStore";
import { useBreadcrumbStore } from "@/shared/lib/useBreadcrumbStore";
import { graphApi } from "@/features/graph/api/graphApi";
import { userGraphApi } from "@/features/graph/api/userGraphApi";
import { documentApi } from "@/features/document/api/documentApi";
import { voiceApi, voiceEvidenceApi } from "@/features/voice/api/voiceApi";
import type {
  GraphContentResponse,
  LawDTO,
  UserGraphDTO,
  UserGraphNode,
} from "@/entities/graph";
import type { VoiceMessageDTO } from "@/entities/voice";

export interface Workspace {
  id: string;
  name: string;
  description?: string;
  createdAt: string;
}

export type WorkspaceTab = "laws" | "docs" | "ai" | "situations" | "voice";

export interface ChatMsg {
  role: "user" | "assistant";
  content: string;
}

export function formatRelative(s: string): string {
  const d = new Date(s);
  const diff = Date.now() - d.getTime();
  const min = Math.floor(diff / 60_000);
  const hr = Math.floor(diff / 3_600_000);
  const day = Math.floor(diff / 86_400_000);
  if (min < 1) return "только что";
  if (min < 60) return `${min} мин назад`;
  if (hr < 24) return `${hr} ч назад`;
  if (day < 7) return `${day} дн назад`;
  return d.toLocaleDateString("ru-RU");
}

export function useWorkspaceShell() {
  const { workspaceId, graphId } = useParams<{
    workspaceId: string;
    graphId?: string;
  }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const setCrumbWs = useBreadcrumbStore((s) => s.setWorkspace);
  const setCrumbGv = useBreadcrumbStore((s) => s.setGraph);

  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [graphs, setGraphs] = useState<UserGraphDTO[]>([]);
  const [content, setContent] = useState<GraphContentResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState("");

  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState("");
  const [creating, setCreating] = useState(false);

  const [tab, setTab] = useState<WorkspaceTab>("laws");
  const [selectedNode, setSelectedNode] = useState<UserGraphNode | null>(null);

  const [q, setQ] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [results, setResults] = useState<LawDTO[]>([]);
  const [searching, setSearching] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  const voiceFileInputRef = useRef<HTMLInputElement>(null);
  const [voices, setVoices] = useState<VoiceMessageDTO[]>([]);
  const [voiceLoading, setVoiceLoading] = useState(false);
  const [voiceUploading, setVoiceUploading] = useState(false);
  const [playingVoiceId, setPlayingVoiceId] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const voiceBlobCache = useRef<Map<string, string>>(new Map());

  const [chatInput, setChatInput] = useState("");
  const [chatMsgs, setChatMsgs] = useState<ChatMsg[]>([]);
  const [chatLoading, setChatLoading] = useState(false);

  const [compareNodes, setCompareNodes] = useState<UserGraphNode[]>([]);
  const [compareOpen, setCompareOpen] = useState(false);
  const [compareLoading, setCompareLoading] = useState(false);
  const [compareResult, setCompareResult] = useState("");

  useEffect(() => {
    if (!workspaceId) return;
    void loadShell();
  }, [workspaceId]);

  useEffect(() => {
    if (!graphId) {
      setContent(null);
      setSelectedNode(null);
      setCrumbGv(null);
      return;
    }
    void loadGraph();
  }, [graphId]);

  useEffect(() => {
    return () => {
      useBreadcrumbStore.getState().clear();
    };
  }, []);

  async function loadShell() {
    if (!workspaceId) return;
    setLoading(true);
    try {
      const [wsRes, gs] = await Promise.all([
        apiClient.get<Workspace>(`/v1/workspaces/${workspaceId}`),
        userGraphApi.list(workspaceId),
      ]);
      setWorkspace(wsRes.data);
      setGraphs(gs);
      setCrumbWs(wsRes.data.name);
    } catch (e) {
      console.error(e);
      toast.error("Не удалось загрузить workspace");
    } finally {
      setLoading(false);
    }
  }

  async function loadGraph() {
    if (!graphId) return;
    try {
      const c = await userGraphApi.getContent(graphId);
      setContent(c);
      setCrumbGv(c.graph.name);
    } catch (e) {
      console.error(e);
      toast.error("Не удалось загрузить graph_view");
      if (workspaceId) navigate(`/workspace/${workspaceId}`);
    }
  }

  const refreshContent = useCallback(async () => {
    if (!graphId) return;
    try {
      const c = await userGraphApi.getContent(graphId);
      setContent(c);
      setSelectedNode((prev) => {
        if (!prev) return prev;
        return c.nodes.find((n) => n.id === prev.id) ?? prev;
      });
    } catch (e) {
      console.error(e);
    }
  }, [graphId]);

  const reloadVoices = useCallback(async () => {
    if (!graphId) return;
    setVoiceLoading(true);
    try {
      setVoices(await voiceApi.list({ graphId }));
    } catch (e) {
      console.error(e);
    } finally {
      setVoiceLoading(false);
    }
  }, [graphId]);

  useEffect(() => {
    if (!graphId) {
      setVoices([]);
      return;
    }
    void reloadVoices();
  }, [graphId, reloadVoices]);

  useEffect(() => {
    const el = new Audio();
    el.preload = "metadata";
    const onEnded = () => setPlayingVoiceId(null);
    el.addEventListener("ended", onEnded);
    audioRef.current = el;
    return () => {
      el.pause();
      el.removeEventListener("ended", onEnded);
      audioRef.current = null;
      voiceBlobCache.current.forEach((url) => URL.revokeObjectURL(url));
      voiceBlobCache.current.clear();
    };
  }, []);

  async function handleVoiceAnalyzed(message: VoiceMessageDTO) {
    const targetGraphId = message.graphId ?? graphId;
    if (!targetGraphId) return;
    let label = (message.fileName ?? "").replace(
      /\.(webm|mp3|m4a|ogg|oga|wav)$/i,
      "",
    );
    if (!label) label = "Голосовое сообщение";
    let classification: string | undefined;
    let severity: string | undefined;
    let summary: string | undefined;
    if (message.analysisJson) {
      try {
        const a = JSON.parse(message.analysisJson) as {
          kind?: string;
          severity?: string;
          summary?: string;
        };
        classification = a.kind;
        severity = a.severity;
        summary = a.summary;
      } catch {

      }
    }
    try {
      await voiceEvidenceApi.upsert({
        graphId: targetGraphId,
        voiceId: message.id,
        label,
        classification,
        severity,
        summary,
      });
      if (targetGraphId === graphId) await refreshContent();
    } catch (e) {
      console.error("voice evidence upsert failed", e);
    }
  }

  async function uploadVoiceFile(file: File | Blob, fileName?: string) {
    if (!graphId) return;
    setVoiceUploading(true);
    const toastId = toast.loading("Загружаю голосовое…");
    try {
      const dto = await voiceApi.upload({
        file,
        fileName:
          fileName ??
          (file instanceof File ? file.name : `voice-${Date.now()}.webm`),
        graphId,
        language: "ru",
      });
      setVoices((prev) => [dto, ...prev]);
      setTab("voice");
      toast.success("Голосовое загружено · идёт транскрипция…", {
        id: toastId,
      });

      setTimeout(() => {
        void refreshContent();
      }, 2500);
      setTimeout(() => {
        void refreshContent();
      }, 8000);
    } catch (e) {
      console.error(e);
      toast.error("Не удалось загрузить голосовое", { id: toastId });
    } finally {
      setVoiceUploading(false);
      if (voiceFileInputRef.current) voiceFileInputRef.current.value = "";
    }
  }

  async function toggleVoicePlayback(node: { id: string }) {
    const audio = audioRef.current;
    if (!audio) return;
    if (playingVoiceId === node.id) {
      audio.pause();
      setPlayingVoiceId(null);
      return;
    }

    let url = voiceBlobCache.current.get(node.id);
    if (!url) {
      try {
        url = await voiceApi.fetchAudioObjectUrl(node.id);
        voiceBlobCache.current.set(node.id, url);
      } catch (e) {
        console.error("voice fetch failed", e);
        toast.error("Не удалось загрузить аудио");
        return;
      }
    }
    if (audio.src !== url) audio.src = url;
    audio
      .play()
      .then(() => setPlayingVoiceId(node.id))
      .catch((e: unknown) => {
        const name = (e as { name?: string } | null)?.name;
        if (name === "AbortError") return;
        console.error("voice play failed", e);
        toast.error("Не удалось воспроизвести аудио");
      });
  }

  async function removeVoice(voiceId: string) {
    if (!confirm("Удалить это голосовое сообщение?")) return;
    try {
      await voiceApi.remove(voiceId);
      setVoices((prev) => prev.filter((v) => v.id !== voiceId));
      if (playingVoiceId === voiceId) {
        audioRef.current?.pause();
        setPlayingVoiceId(null);
      }
      const cached = voiceBlobCache.current.get(voiceId);
      if (cached) {
        URL.revokeObjectURL(cached);
        voiceBlobCache.current.delete(voiceId);
      }
      toast.success("Голосовое удалено");
      await refreshContent();
    } catch (e) {
      console.error(e);
      toast.error("Не удалось удалить голосовое");
    }
  }

  async function createGraph(e?: React.FormEvent) {
    e?.preventDefault();
    const name = newName.trim();
    if (!name || !workspaceId) return;
    setCreating(true);
    try {
      const created = await userGraphApi.create(workspaceId, name);
      setGraphs((p) => [created, ...p]);
      setNewName("");
      setShowCreate(false);
      toast.success(`Граф «${created.name}» создан`);
      navigate(`/workspace/${workspaceId}/graph/${created.id}`);
    } catch (e) {
      console.error(e);
      toast.error("Не удалось создать graph_view");
    } finally {
      setCreating(false);
    }
  }

  async function deleteGraph(g: UserGraphDTO, ev?: React.MouseEvent) {
    ev?.stopPropagation();
    if (!window.confirm(`Удалить graph_view «${g.name}»?`)) return;
    try {
      await userGraphApi.remove(g.id);
      setGraphs((p) => p.filter((x) => x.id !== g.id));
      if (graphId === g.id && workspaceId)
        navigate(`/workspace/${workspaceId}`);
      toast.success("Граф удалён");
    } catch (e) {
      console.error(e);
      toast.error("Не удалось удалить graph_view");
    }
  }

  async function doSearch() {
    const text = q.trim();
    setSubmitted(true);
    if (!text) {
      setResults([]);
      return;
    }
    setSearching(true);
    try {
      const res = await graphApi.searchLaws(text, "RK");
      setResults(res);
    } catch (e) {
      console.error(e);
      toast.error("Ошибка поиска");
    } finally {
      setSearching(false);
    }
  }

  async function addLaw(code: string, title: string) {
    if (!graphId) return;
    try {
      await userGraphApi.addLaw(graphId, code, "RK");
      toast.success(`Добавлен: ${title}`);
      await refreshContent();
      if (workspaceId) {
        const gs = await userGraphApi.list(workspaceId);
        setGraphs(gs);
      }
    } catch (e) {
      console.error(e);
      toast.error("Не удалось добавить закон");
    }
  }

  async function removeSelected() {
    if (!selectedNode || !graphId) return;
    if (selectedNode.type === "GENERATED") return;
    try {
      if (selectedNode.kind === "LAW") {
        await userGraphApi.removeLaw(
          graphId,
          selectedNode.code ?? selectedNode.id,
          "RK",
        );
      } else {
        await userGraphApi.removeDocument(graphId, selectedNode.id);
      }
      setSelectedNode(null);
      toast.success("Убрано из графа");
      await refreshContent();
      if (workspaceId) {
        const gs = await userGraphApi.list(workspaceId);
        setGraphs(gs);
      }
    } catch (e) {
      console.error(e);
      toast.error("Не удалось убрать");
    }
  }

  async function handleFile(file: File) {
    if (!graphId) return;
    setUploading(true);
    try {
      const docId = await documentApi.upload(file);
      toast.success("Документ загружен, индексирую...");
      for (let i = 0; i < 30; i++) {
        await new Promise((r) => setTimeout(r, 1000));
        try {
          const s = await documentApi.getStatus(docId);
          if (s.status === "READY" || s.status === "ERROR") break;
        } catch {

        }
      }
      await userGraphApi.addDocument(graphId, docId, file.name, file.type);
      toast.success("Документ добавлен в граф");
      try {
        toast.message("ИИ ищет связанные законы...");
        await apiClient.post(
          "/brain/chat",
          {
            message: `Свяжи документ ${docId} с законами по смыслу, добавь топ-5 в граф.`,
            userId: user?.id ?? "",
            documentId: docId,
            graphId,
            history: [],
          },
          { timeout: 180_000 },
        );
      } catch (e) {
        console.warn("Auto-linking failed", e);
      }
      await refreshContent();
    } catch (e) {
      console.error(e);
      toast.error("Не удалось загрузить документ");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function sendChat(text?: string) {
    const msg = (text ?? chatInput).trim();
    if (!msg || !graphId) return;
    setChatInput("");
    setChatMsgs((m) => [...m, { role: "user", content: msg }]);
    setChatLoading(true);
    try {
      const activeDoc =
        selectedNode?.kind === "DOCUMENT"
          ? selectedNode
          : content?.nodes.find((n) => n.kind === "DOCUMENT");
      const activeSituation =
        selectedNode?.kind === "SITUATION"
          ? selectedNode
          : content?.nodes.find((n) => n.kind === "SITUATION");
      const activeVoice =
        selectedNode?.kind === "VOICE"
          ? selectedNode
          : content?.nodes.find((n) => n.kind === "VOICE");
      const res = await apiClient.post<{ answer?: string; content?: string }>(
        "/brain/chat",
        {
          message: msg,
          userId: user?.id ?? "",
          graphId,
          documentId: activeDoc?.id,
          situationId: activeSituation?.id,
          voiceId: activeVoice?.id,
          history: chatMsgs.map((m) => ({
            role: m.role.toUpperCase(),
            content: m.content,
          })),
        },
        { timeout: 180_000 },
      );
      const answer =
        res.data.content ?? res.data.answer ?? JSON.stringify(res.data);
      setChatMsgs((m) => [...m, { role: "assistant", content: answer }]);
      if (
        answer.includes("Шаблон сохранён") ||
        answer.includes("Документ подготовлен")
      ) {
        toast.success(
          "Документ создан — откройте раздел Шаблоны для редактирования",
          {
            action: { label: "Шаблоны", onClick: () => navigate("/templates") },
            duration: 8000,
          },
        );
      }
      await refreshContent();
    } catch (e) {
      console.error(e);
      setChatMsgs((m) => [
        ...m,
        { role: "assistant", content: "Произошла ошибка." },
      ]);
    } finally {
      setChatLoading(false);
    }
  }

  async function analyzeSelected(node?: UserGraphNode | null) {
    const target = node ?? selectedNode;
    if (!target || !graphId) return;
    const linkable =
      target.kind === "DOCUMENT" ||
      target.kind === "SITUATION" ||
      target.kind === "VOICE";
    if (!linkable) {
      toast.error("Выберите документ, ситуацию или ГС");
      return;
    }
    const noun =
      target.kind === "DOCUMENT"
        ? "документа"
        : target.kind === "SITUATION"
          ? "ситуации"
          : "голосового сообщения";
    const msg = `Проведи идеальный глубокий анализ ${noun}: подбери применимые нормы, разверни связанные законы и проверь на противоречия.`;
    setTab("ai");
    setChatMsgs((m) => [...m, { role: "user", content: msg }]);
    setChatLoading(true);
    const toastId = toast.loading(`Анализ ${noun.replace("а", "а")}…`);
    try {
      const res = await apiClient.post<{ answer?: string; content?: string }>(
        "/brain/chat",
        {
          message: msg,
          userId: user?.id ?? "",
          graphId,
          documentId: target.kind === "DOCUMENT" ? target.id : undefined,
          situationId: target.kind === "SITUATION" ? target.id : undefined,
          voiceId: target.kind === "VOICE" ? target.id : undefined,
          history: chatMsgs.map((m) => ({
            role: m.role.toUpperCase(),
            content: m.content,
          })),
        },
        { timeout: 300_000 },
      );
      const answer =
        res.data.content ?? res.data.answer ?? JSON.stringify(res.data);
      setChatMsgs((m) => [...m, { role: "assistant", content: answer }]);
      toast.success("Анализ завершён", { id: toastId });
      await refreshContent();
    } catch (e) {
      console.error(e);
      setChatMsgs((m) => [
        ...m,
        { role: "assistant", content: "Анализ прерван." },
      ]);
      toast.error("Не удалось провести анализ", { id: toastId });
    } finally {
      setChatLoading(false);
    }
  }

  const toggleCompare = useCallback((node: UserGraphNode) => {
    setCompareNodes((prev) => {
      if (prev.some((n) => n.id === node.id))
        return prev.filter((n) => n.id !== node.id);
      if (prev.length >= 2) return [prev[1], node];
      return [...prev, node];
    });
  }, []);

  async function runCompare() {
    if (compareNodes.length !== 2) return;
    setCompareOpen(true);
    setCompareLoading(true);
    setCompareResult("");
    try {
      const [a, b] = compareNodes;
      const res = await apiClient.post<{ content?: string; answer?: string }>(
        "/brain/compare",
        {
          leftKind: a.kind,
          leftId: a.kind === "LAW" ? (a.code ?? a.id) : a.id,
          leftLabel: a.label,
          rightKind: b.kind,
          rightId: b.kind === "LAW" ? (b.code ?? b.id) : b.id,
          rightLabel: b.label,
          userId: user?.id ?? "",
        },
        { timeout: 180_000 },
      );
      const text =
        res.data.content ?? res.data.answer ?? JSON.stringify(res.data);
      setCompareResult(text);
    } catch (e) {
      console.error(e);
      setCompareResult("Не удалось сравнить документы.");
    } finally {
      setCompareLoading(false);
    }
  }

  const filteredGraphs = useMemo(() => {
    const text = filter.trim().toLowerCase();
    if (!text) return graphs;
    return graphs.filter((g) => g.name.toLowerCase().includes(text));
  }, [graphs, filter]);

  const activeGraph = useMemo(
    () => graphs.find((g) => g.id === graphId) ?? null,
    [graphs, graphId],
  );

  return {
    workspaceId,
    graphId,
    navigate,
    user,

    workspace,
    graphs,
    filteredGraphs,
    activeGraph,
    content,
    loading,
    filter,
    setFilter,

    showCreate,
    setShowCreate,
    newName,
    setNewName,
    creating,
    createGraph,
    deleteGraph,

    tab,
    setTab,
    selectedNode,
    setSelectedNode,
    removeSelected,

    q,
    setQ,
    submitted,
    setSubmitted,
    results,
    searching,
    doSearch,
    addLaw,

    fileInputRef,
    uploading,
    handleFile,

    chatInput,
    setChatInput,
    chatMsgs,
    chatLoading,
    sendChat,
    analyzeSelected,

    voices,
    voiceLoading,
    voiceUploading,
    voiceFileInputRef,
    uploadVoiceFile,
    reloadVoices,
    playingVoiceId,
    toggleVoicePlayback,
    removeVoice,
    handleVoiceAnalyzed,

    refreshContent,

    compareNodes,
    setCompareNodes,
    compareOpen,
    setCompareOpen,
    compareLoading,
    compareResult,
    toggleCompare,
    runCompare,
  };
}

export type WorkspaceShellState = ReturnType<typeof useWorkspaceShell>;
