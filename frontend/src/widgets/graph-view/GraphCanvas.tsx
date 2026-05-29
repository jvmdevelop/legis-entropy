import {
  Component,
  useEffect,
  useMemo,
  useRef,
  useState,
  useCallback,
  type ReactNode,
} from "react";
import ForceGraph2D from "react-force-graph-2d";
import { Download, Maximize2 } from "lucide-react";
import { toast } from "sonner";
import type { GraphContentResponse, UserGraphNode } from "@/entities/graph";

export const NODE_COLOR = {
  LAW: { fill: "rgb(139, 92, 246)", ring: "rgba(139, 92, 246, 0.18)" },
  DOCUMENT: { fill: "rgb(251, 191, 36)", ring: "rgba(251, 191, 36, 0.22)" },
  ARTICLE: { fill: "rgb(20, 184, 166)", ring: "rgba(20, 184, 166, 0.22)" },
  SITUATION: { fill: "rgb(236, 72, 153)", ring: "rgba(236, 72, 153, 0.22)" },
  VOICE: { fill: "rgb(239, 68, 68)", ring: "rgba(239, 68, 68, 0.22)" },
  COMMENT: { fill: "rgb(67, 56, 202)", ring: "rgba(67, 56, 202, 0.22)" },
} as const;

export const EDGE_COLOR: Record<string, string> = {
  REFERENCES: "rgba(59, 130, 246, 0.55)",
  REFERENCES_LAW: "rgba(67, 56, 202, 0.55)",
  REFERENCES_ARTICLE: "rgba(67, 56, 202, 0.65)",
  ANALYZES: "rgba(67, 56, 202, 0.55)",
  AMENDS: "rgba(139, 92, 246, 0.55)",
  DEFINES: "rgba(34, 197, 94, 0.55)",
  RELATED: "rgba(168, 85, 247, 0.55)",
  SUPERSEDES: "rgba(220, 38, 38, 0.55)",
  IMPLEMENTS: "rgba(251, 146, 60, 0.55)",
  RELATES_TO: "rgba(236, 72, 153, 0.6)",
  CITES: "rgba(20, 184, 166, 0.7)",
  HAS_ARTICLE: "rgba(120, 113, 108, 0.45)",
  CONFLICTS_WITH: "rgba(220, 38, 38, 0.85)",
  INVOLVES_DOCUMENT: "rgba(236, 72, 153, 0.55)",
  INVOLVES_ARTICLE: "rgba(236, 72, 153, 0.55)",
  INVOLVES_VOICE: "rgba(236, 72, 153, 0.7)",
  EVIDENCES_VIOLATION: "rgba(239, 68, 68, 0.7)",
};

interface GraphCanvasProps {
  content: GraphContentResponse;
  onSelectNode?: (node: UserGraphNode | null) => void;
  compareIds?: string[];
  onToggleCompare?: (node: UserGraphNode) => void;

  onEditSituation?: (node: UserGraphNode) => void;

  onToggleVoicePlayback?: (node: UserGraphNode) => void;

  playingVoiceId?: string | null;

  onOpenComment?: (node: UserGraphNode) => void;
}

class CanvasErrorBoundary extends Component<
  { children: ReactNode },
  { error: Error | null }
> {
  state = { error: null as Error | null };
  static getDerivedStateFromError(error: Error) {
    return { error };
  }
  componentDidCatch(error: Error) {
    console.error("GraphCanvas crashed:", error);
  }
  render() {
    if (this.state.error) {
      return (
        <div className="flex items-center justify-center h-full p-8 text-center">
          <div>
            <div
              className="text-sm font-semibold text-[var(--color-error)] mb-2"
              style={{ fontFamily: "'IBM Plex Mono', monospace" }}
            >
              Канвас упал
            </div>
            <pre
              className="text-xs text-[var(--color-text-muted)] whitespace-pre-wrap max-w-xl text-left bg-[var(--color-surface)] p-3 rounded border border-[var(--color-border)]"
              style={{ fontFamily: "'IBM Plex Mono', monospace" }}
            >
              {this.state.error.message}
              {"\n"}
              {(this.state.error.stack ?? "")
                .split("\n")
                .slice(0, 5)
                .join("\n")}
            </pre>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

export function GraphCanvas(props: GraphCanvasProps) {
  return (
    <CanvasErrorBoundary>
      <GraphCanvasInner {...props} />
    </CanvasErrorBoundary>
  );
}

function GraphCanvasInner({
  content,
  onSelectNode,
  compareIds = [],
  onToggleCompare,
  onEditSituation,
  onToggleVoicePlayback,
  playingVoiceId,
  onOpenComment,
}: GraphCanvasProps) {
  const compareSet = useMemo(() => new Set(compareIds), [compareIds]);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const fgRef = useRef<any>(null);
  const [hoverId, setHoverId] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [size, setSize] = useState({ w: 0, h: 0 });

  useEffect(() => {
    if (!wrapperRef.current) return;
    const el = wrapperRef.current;
    const update = () => setSize({ w: el.clientWidth, h: el.clientHeight });
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const data = useMemo(() => {
    const degree = new Map<string, number>();
    for (const e of content.edges) {
      degree.set(e.source, (degree.get(e.source) ?? 0) + 1);
      degree.set(e.target, (degree.get(e.target) ?? 0) + 1);
    }
    return {
      nodes: content.nodes.map((n) => ({
        id: n.id,
        label: n.label,
        kind: n.kind,
        code: n.code,
        type: n.type,
        summary: n.summary,
        severity: n.severity,
        generatedDocId: n.generatedDocId,
        generatedDocTitle: n.generatedDocTitle,
        degree: degree.get(n.id) ?? 0,
      })),
      links: content.edges.map((e) => ({
        source: e.source,
        target: e.target,
        relationType: e.relationType,
        kind: e.kind,
        reason: e.reason,
        confidence: e.confidence,
        clauseRef: e.clauseRef,
      })),
    };
  }, [content]);

  useEffect(() => {
    const fg = fgRef.current;
    if (!fg) return;
    try {
      fg.d3Force("charge")?.strength(-180);
      fg.d3Force("link")?.distance(70);
    } catch {

    }
    const t = setTimeout(() => {
      try {
        fg.zoomToFit(400, 80);
      } catch {

      }
    }, 400);
    return () => clearTimeout(t);
  }, [data]);

  const handleNodeHover = useCallback((node: any) => {
    setHoverId(node ? String(node.id) : null);
    if (wrapperRef.current)
      wrapperRef.current.style.cursor = node ? "pointer" : "default";
  }, []);

  const handleNodeClick = useCallback(
    (node: any, event: MouseEvent) => {
      const id = String(node.id);
      const userNode: UserGraphNode = {
        id,
        label: node.label,
        kind: node.kind,
        code: node.code,
        type: node.type,
        summary: node.summary,
        severity: node.severity,
        generatedDocId: node.generatedDocId,
        generatedDocTitle: node.generatedDocTitle,
      };
      if (event && (event.ctrlKey || event.metaKey)) {
        onToggleCompare?.(userNode);
        return;
      }
      setSelectedId(id);
      onSelectNode?.(userNode);
      if (node.kind === "SITUATION" && onEditSituation) {
        onEditSituation(userNode);
        return;
      }
      if (node.kind === "VOICE" && onToggleVoicePlayback) {
        onToggleVoicePlayback(userNode);
        return;
      }
      if (node.kind === "COMMENT" && onOpenComment) {
        onOpenComment(userNode);
        return;
      }
      const fg = fgRef.current;
      if (fg && typeof node.x === "number" && typeof node.y === "number") {
        fg.centerAt(node.x, node.y, 600);
        fg.zoom(2.2, 600);
      }
    },
    [
      onSelectNode,
      onToggleCompare,
      onEditSituation,
      onToggleVoicePlayback,
      onOpenComment,
    ],
  );

  const handleBackgroundClick = useCallback(() => {
    setSelectedId(null);
    onSelectNode?.(null);
  }, [onSelectNode]);

  function handleExport() {
    const canvas = wrapperRef.current?.querySelector(
      "canvas",
    ) as HTMLCanvasElement | null;
    if (!canvas) return;
    const a = document.createElement("a");
    a.href = canvas.toDataURL("image/png");
    a.download = `graph-${content.graph.id}.png`;
    a.click();
    toast.success("Граф скачан");
  }

  function handleRecenter() {
    try {
      fgRef.current?.zoomToFit(600, 80);
    } catch {

    }
  }

  const isEmpty = content.nodes.length === 0;

  return (
    <div className="flex flex-col h-full bg-white">
      <div
        ref={wrapperRef}
        className="flex-1 relative overflow-hidden"
        style={{
          background:
            "radial-gradient(ellipse at center, #fafafa 0%, #f3f4f6 100%)",
        }}
      >
        {isEmpty ? (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className="text-center px-6">
              <div
                className="text-[var(--color-text-muted)] mb-2"
                style={{
                  fontFamily: "'IBM Plex Mono', monospace",
                  fontSize: "14px",
                }}
              >
                Граф пуст
              </div>
              <div
                className="text-xs text-[var(--color-text-muted)]"
                style={{ fontFamily: "'IBM Plex Sans', sans-serif" }}
              >
                Добавьте закон, загрузите документ или попросите ИИ собрать
                связанные нормы
              </div>
            </div>
          </div>
        ) : size.w > 0 && size.h > 0 ? (
          <ForceGraph2D
            ref={fgRef}
            graphData={data}
            width={size.w}
            height={size.h}
            backgroundColor="rgba(0,0,0,0)"
            nodeRelSize={5}
            nodeLabel={() => ""}
            cooldownTicks={120}
            warmupTicks={40}
            d3VelocityDecay={0.32}
            linkColor={(l: any) =>
              EDGE_COLOR[l.relationType] ?? "rgba(156,163,175,0.45)"
            }
            linkWidth={(l: any) =>
              l.relationType === "CONFLICTS_WITH"
                ? 2.4
                : l.relationType === "CITES"
                  ? 1.8
                  : l.kind === "DOCUMENT_LAW" || l.relationType === "RELATES_TO"
                    ? 1.6
                    : l.relationType === "HAS_ARTICLE"
                      ? 0.7
                      : 1.1
            }
            linkLineDash={(l: any) =>
              l.relationType === "CONFLICTS_WITH"
                ? [8, 3]
                : l.relationType === "CITES"
                  ? [6, 3]
                  : l.kind === "DOCUMENT_LAW" || l.relationType === "RELATES_TO"
                    ? [4, 4]
                    : l.relationType === "HAS_ARTICLE"
                      ? [2, 3]
                      : null
            }
            onNodeHover={handleNodeHover}
            onNodeClick={handleNodeClick}
            onBackgroundClick={handleBackgroundClick}
            nodeCanvasObjectMode={() => "replace"}
            nodeCanvasObject={(
              node: any,
              ctx: CanvasRenderingContext2D,
              globalScale: number,
            ) => {
              const id = String(node.id);
              const isHover = hoverId === id;
              const isSelected = selectedId === id;
              const isCompare = compareSet.has(id);
              const color =
                NODE_COLOR[node.kind as keyof typeof NODE_COLOR] ??
                NODE_COLOR.LAW;
              const isVoice = node.kind === "VOICE";
              const isSituation = node.kind === "SITUATION";
              const isVoicePlaying = isVoice && playingVoiceId === id;

              const baseR =
                node.kind === "ARTICLE"
                  ? 3 + Math.min(4, Math.sqrt(node.degree ?? 0) * 1.8)
                  : 4 + Math.min(6, Math.sqrt(node.degree ?? 0) * 2.4);
              const r = isHover || isSelected || isCompare ? baseR + 2 : baseR;

              if (isCompare) {
                ctx.beginPath();
                ctx.arc(node.x, node.y, r + 7, 0, 2 * Math.PI, false);
                ctx.fillStyle = "rgba(20, 184, 166, 0.22)";
                ctx.fill();
              } else if (isVoicePlaying) {
                ctx.beginPath();
                ctx.arc(node.x, node.y, r + 6, 0, 2 * Math.PI, false);
                ctx.fillStyle = "rgba(239, 68, 68, 0.30)";
                ctx.fill();
              } else if (isVoice && node.severity) {
                const sev = severityHalo(node.severity);
                if (sev) {
                  ctx.beginPath();
                  ctx.arc(node.x, node.y, r + 5, 0, 2 * Math.PI, false);
                  ctx.fillStyle = sev;
                  ctx.fill();
                }
              } else if (isHover || isSelected) {
                ctx.beginPath();
                ctx.arc(node.x, node.y, r + 5, 0, 2 * Math.PI, false);
                ctx.fillStyle = color.ring;
                ctx.fill();
              }

              ctx.beginPath();
              ctx.arc(node.x, node.y, r, 0, 2 * Math.PI, false);
              ctx.fillStyle = color.fill;
              ctx.fill();

              if (isCompare) {
                ctx.lineWidth = 2 / globalScale;
                ctx.strokeStyle = "rgb(13, 148, 136)";
                ctx.stroke();
              } else if (isVoicePlaying) {
                ctx.lineWidth = 1.5 / globalScale;
                ctx.strokeStyle = "rgb(239, 68, 68)";
                ctx.stroke();
              } else if (isSelected) {
                ctx.lineWidth = 1.5 / globalScale;
                ctx.strokeStyle = "rgba(15, 23, 42, 0.7)";
                ctx.stroke();
              }

              if (isSituation && onEditSituation) {
                drawPencil(
                  ctx,
                  node.x,
                  node.y,
                  r * 0.85,
                  "#ffffff",
                  globalScale,
                );
              }
              if (isVoice && onToggleVoicePlayback) {
                if (isVoicePlaying) {
                  drawPauseBars(ctx, node.x, node.y, r * 0.85, "#ffffff");
                } else {
                  drawMic(
                    ctx,
                    node.x,
                    node.y,
                    r * 0.85,
                    "#ffffff",
                    globalScale,
                  );
                }
              }
              if (node.kind === "COMMENT" && onOpenComment) {
                drawSpeechBubble(
                  ctx,
                  node.x,
                  node.y,
                  r * 0.85,
                  "#ffffff",
                  globalScale,
                );
              }

              const showLabel =
                isHover || isSelected || isCompare || globalScale > 2.2;
              if (showLabel && node.label) {
                const label = String(node.label).slice(0, 80);
                const fontSize =
                  isHover || isSelected ? 12 / globalScale : 10 / globalScale;
                ctx.font = `${isHover || isSelected ? 600 : 500} ${fontSize}px 'IBM Plex Sans', sans-serif`;
                const padding = 4 / globalScale;
                const textWidth = ctx.measureText(label).width;
                const boxW = textWidth + padding * 2;
                const boxH = fontSize + padding * 1.5;
                const ly = node.y - r - boxH / 2 - 2 / globalScale;
                ctx.fillStyle = "rgba(255,255,255,0.96)";
                ctx.strokeStyle = "rgba(15,23,42,0.12)";
                ctx.lineWidth = 0.8 / globalScale;
                roundRect(
                  ctx,
                  node.x - boxW / 2,
                  ly - boxH / 2,
                  boxW,
                  boxH,
                  3 / globalScale,
                );
                ctx.fill();
                ctx.stroke();
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillStyle = "#0f172a";
                ctx.fillText(label, node.x, ly);
              }
            }}
            nodePointerAreaPaint={(
              node: any,
              color: string,
              ctx: CanvasRenderingContext2D,
            ) => {
              ctx.fillStyle = color;
              ctx.beginPath();
              ctx.arc(
                node.x,
                node.y,
                8 + Math.min(6, Math.sqrt(node.degree ?? 0) * 2.4),
                0,
                2 * Math.PI,
                false,
              );
              ctx.fill();
            }}
          />
        ) : null}

        <div className="absolute top-3 right-3 flex flex-col gap-1.5">
          <button
            onClick={handleRecenter}
            className="p-2 bg-white border border-[var(--color-border)] rounded-lg shadow-sm hover:bg-[var(--color-surface)] transition-colors"
            title="Центрировать"
          >
            <Maximize2 size={14} className="text-[var(--color-text-muted)]" />
          </button>
          <button
            onClick={handleExport}
            className="p-2 bg-white border border-[var(--color-border)] rounded-lg shadow-sm hover:bg-[var(--color-surface)] transition-colors"
            title="Скачать PNG"
          >
            <Download size={14} className="text-[var(--color-text-muted)]" />
          </button>
        </div>
      </div>
    </div>
  );
}

function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
) {
  const radius = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.lineTo(x + w - radius, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + radius);
  ctx.lineTo(x + w, y + h - radius);
  ctx.quadraticCurveTo(x + w, y + h, x + w - radius, y + h);
  ctx.lineTo(x + radius, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - radius);
  ctx.lineTo(x, y + radius);
  ctx.quadraticCurveTo(x, y, x + radius, y);
  ctx.closePath();
}

function drawPencil(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  r: number,
  fill: string,
  globalScale: number,
) {
  const len = r * 1.4;
  const w = Math.max(r * 0.42, 1.6 / globalScale);
  const cos = Math.SQRT1_2;
  const sin = Math.SQRT1_2;

  const toWorld = (lx: number, ly: number): [number, number] => [
    cx + lx * cos - ly * sin,
    cy + lx * sin + ly * cos,
  ];

  const tipLocal = len / 2;
  const shaftBack = -len / 2;
  const shaftFront = tipLocal - w * 1.1;
  const a = toWorld(shaftBack, -w / 2);
  const b = toWorld(shaftFront, -w / 2);
  const c = toWorld(shaftFront, w / 2);
  const d = toWorld(shaftBack, w / 2);
  ctx.beginPath();
  ctx.moveTo(a[0], a[1]);
  ctx.lineTo(b[0], b[1]);
  ctx.lineTo(c[0], c[1]);
  ctx.lineTo(d[0], d[1]);
  ctx.closePath();
  ctx.fillStyle = fill;
  ctx.fill();

  const t1 = toWorld(shaftFront, -w / 2);
  const t2 = toWorld(tipLocal, 0);
  const t3 = toWorld(shaftFront, w / 2);
  ctx.beginPath();
  ctx.moveTo(t1[0], t1[1]);
  ctx.lineTo(t2[0], t2[1]);
  ctx.lineTo(t3[0], t3[1]);
  ctx.closePath();
  ctx.fill();

  const e1 = toWorld(shaftBack, -w / 2);
  const e2 = toWorld(shaftBack + w * 0.6, -w / 2);
  const e3 = toWorld(shaftBack + w * 0.6, w / 2);
  const e4 = toWorld(shaftBack, w / 2);
  ctx.beginPath();
  ctx.moveTo(e1[0], e1[1]);
  ctx.lineTo(e2[0], e2[1]);
  ctx.lineTo(e3[0], e3[1]);
  ctx.lineTo(e4[0], e4[1]);
  ctx.closePath();
  ctx.fillStyle = "rgba(255, 255, 255, 0.65)";
  ctx.fill();
  ctx.fillStyle = fill;
}

function severityHalo(severity?: string): string | null {
  switch ((severity ?? "").toUpperCase()) {
    case "CRITICAL":
      return "rgba(220, 38, 38, 0.42)";
    case "HIGH":
      return "rgba(239, 68, 68, 0.32)";
    case "MEDIUM":
      return "rgba(245, 158, 11, 0.32)";
    default:
      return null;
  }
}

function drawMic(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  r: number,
  fill: string,
  globalScale: number,
) {
  const capW = r * 0.62;
  const capH = r * 0.9;
  const stroke = Math.max(0.9 / globalScale, r * 0.14);
  const headY = cy - r * 0.1;

  ctx.beginPath();
  roundRect(ctx, cx - capW / 2, headY - capH / 2, capW, capH, capW / 2);
  ctx.fillStyle = fill;
  ctx.fill();

  ctx.lineWidth = stroke;
  ctx.lineCap = "round";
  ctx.strokeStyle = fill;
  ctx.beginPath();
  ctx.arc(cx, headY + capH * 0.18, capW * 0.85, 0.15 * Math.PI, 0.85 * Math.PI);
  ctx.stroke();

  const baseY = cy + r * 0.85;
  const standY = headY + capH * 0.18 + capW * 0.85;
  ctx.beginPath();
  ctx.moveTo(cx, standY);
  ctx.lineTo(cx, baseY);
  ctx.stroke();

  ctx.beginPath();
  ctx.moveTo(cx - capW * 0.55, baseY);
  ctx.lineTo(cx + capW * 0.55, baseY);
  ctx.stroke();
}

function drawSpeechBubble(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  r: number,
  fill: string,
  globalScale: number,
) {
  const w = r * 1.55;
  const h = r * 1.15;
  const left = cx - w / 2;
  const top = cy - h / 2 - r * 0.08;
  const radius = h * 0.35;
  ctx.beginPath();
  roundRect(ctx, left, top, w, h, radius);
  ctx.fillStyle = fill;
  ctx.fill();
  ctx.beginPath();
  ctx.moveTo(left + w * 0.3, top + h - 0.1 / globalScale);
  ctx.lineTo(left + w * 0.2, top + h + r * 0.45);
  ctx.lineTo(left + w * 0.42, top + h - 0.1 / globalScale);
  ctx.closePath();
  ctx.fill();
  const dotR = Math.max(0.6 / globalScale, r * 0.1);
  const dotY = top + h / 2;
  ctx.beginPath();
  ctx.arc(cx - w * 0.25, dotY, dotR, 0, 2 * Math.PI, false);
  ctx.arc(cx, dotY, dotR, 0, 2 * Math.PI, false);
  ctx.arc(cx + w * 0.25, dotY, dotR, 0, 2 * Math.PI, false);
  ctx.fillStyle = "rgba(67, 56, 202, 0.85)";
  ctx.fill();
}

function drawPauseBars(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  r: number,
  fill: string,
) {
  const bw = r * 0.32;
  const bh = r * 1.05;
  const gap = r * 0.2;
  ctx.fillStyle = fill;
  ctx.fillRect(cx - gap / 2 - bw, cy - bh / 2, bw, bh);
  ctx.fillRect(cx + gap / 2, cy - bh / 2, bw, bh);
}
