import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { DocumentViewerModel, ViewerSuggestion } from "../model/useDocumentViewer";
import type { DocumentStructureModel } from "../model/useDocumentStructure";
import { layoutWithStructure, type LayoutItem } from "../lib/structureLayout";
import { TextSegment } from "./TextSegment";
import { ClauseAnchor, PageDivider } from "./StructureMarks";
import { ProposalCard } from "./ProposalCard";
import { SelectionToolbar } from "./SelectionToolbar";
import { markSelector, offsetOf, placeBelow, type Placement } from "./domRange";

interface Props {
  model: DocumentViewerModel;
  structure: DocumentStructureModel;
}

interface SelectionState {
  start: number;
  end: number;
  blocked: boolean;
  pos: Placement;
}

const MAX_SELECTION = 4000;

export function DocumentText({ model, structure }: Props) {
  const { text, layout, items: suggestions, activeId, setActiveId, rewriting, rewrite } = model;
  const rootRef = useRef<HTMLDivElement | null>(null);
  const popRef = useRef<HTMLDivElement | null>(null);
  const [selection, setSelection] = useState<SelectionState | null>(null);
  const [popPos, setPopPos] = useState<Placement | null>(null);

  const byId = useMemo(() => {
    const m = new Map<string, ViewerSuggestion>();
    suggestions.forEach((s) => m.set(s.proposal.id, s));
    return m;
  }, [suggestions]);

  const active = activeId ? byId.get(activeId) : undefined;
  const activePlaced = !!activeId && layout.ranges.has(activeId);

  useLayoutEffect(() => {
    const measure = () => {
      const root = rootRef.current;
      const el = activeId && root ? root.querySelector(markSelector(activeId)) : null;
      if (!root || !el) {
        setPopPos(null);
        return;
      }
      setPopPos(placeBelow(el.getBoundingClientRect(), root.getBoundingClientRect(), 360));
    };
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [activeId, layout, suggestions, structure.clauses, structure.openClause]);

  useEffect(() => {
    if (!activeId) return;
    const onDown = (e: MouseEvent) => {
      const t = e.target as Element | null;
      if (popRef.current?.contains(t as Node)) return;
      if (t?.closest?.("[data-mark]") || t?.closest?.("[data-dv-panel]")) return;
      setActiveId(null);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        setActiveId(null);
      }
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [activeId, setActiveId]);

  const evaluate = useCallback(() => {
    const root = rootRef.current;
    const sel = window.getSelection();
    if (!root || !sel || sel.rangeCount === 0 || sel.isCollapsed || text === null) return;
    const range = sel.getRangeAt(0);
    if (!root.contains(range.commonAncestorContainer)) return;
    let start = offsetOf(range.startContainer, range.startOffset, root);
    let end = offsetOf(range.endContainer, range.endOffset, root);
    if (start === null || end === null) {
      const str = sel.toString();
      const i = str ? text.indexOf(str) : -1;
      if (i < 0 || text.indexOf(str, i + 1) >= 0) return;
      start = i;
      end = i + str.length;
    }
    while (start < end && /\s/.test(text[start])) start++;
    while (end > start && /\s/.test(text[end - 1])) end--;
    if (end - start < 2 || end - start > MAX_SELECTION) return;
    const s = start;
    const e = end;
    const blocked = [...layout.ranges.values()].some((r) => s < r.end && r.start < e);
    const pos = placeBelow(range.getBoundingClientRect(), root.getBoundingClientRect(), 300);
    setSelection({ start: s, end: e, blocked, pos });
  }, [text, layout]);

  useEffect(() => {
    if (!window.matchMedia("(pointer: coarse)").matches) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const onChange = () => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(evaluate, 450);
    };
    document.addEventListener("selectionchange", onChange);
    return () => {
      if (timer) clearTimeout(timer);
      document.removeEventListener("selectionchange", onChange);
    };
  }, [evaluate]);

  const submit = async (instruction: string) => {
    if (!selection) return;
    const ok = await rewrite(selection.start, selection.end, instruction);
    if (ok) {
      setSelection(null);
      window.getSelection()?.removeAllRanges();
    }
  };

  const items = useMemo(
    () =>
      layoutWithStructure(
        layout.segments,
        text?.length ?? 0,
        structure.pages,
        structure.clauses,
      ),
    [layout, text, structure.pages, structure.clauses],
  );

  const openMark = (id: string) => setActiveId(id === activeId ? null : id);

  const renderItem = (it: LayoutItem) => {
    if (it.kind === "page") return <PageDivider key={it.key} page={it.page} />;
    if (it.kind === "clause") {
      const clause = structure.clauses[it.index];
      return (
        <ClauseAnchor
          key={it.key}
          index={it.index}
          clause={clause}
          open={structure.openClause === it.index}
          dim={!structure.matches(it.index)}
          onToggle={() =>
            structure.setOpenClause(structure.openClause === it.index ? null : it.index)
          }
        />
      );
    }
    const seg = it.seg;
    return (
      <TextSegment
        key={it.key}
        seg={seg}
        item={seg.kind === "mark" ? byId.get(seg.id) : undefined}
        active={seg.kind === "mark" && seg.id === activeId}
        dim={!structure.matches(it.clause)}
        flash={it.clause >= 0 && structure.flash === it.clause}
        onOpen={openMark}
        onUndo={model.reset}
      />
    );
  };

  return (
    <div
      ref={rootRef}
      data-dv-text=""
      className="le-dv-paper"
      onMouseUp={() => setTimeout(evaluate, 0)}
      onPointerDown={() => setSelection(null)}
    >
      {text ? items.map(renderItem) : (
        <span style={{ color: "var(--color-text-muted)" }}>Текст документа пуст.</span>
      )}

      {selection && (
        <SelectionToolbar
          key={`${selection.start}-${selection.end}`}
          style={{ top: selection.pos.top, left: selection.pos.left, width: selection.pos.width }}
          blocked={selection.blocked}
          busy={rewriting}
          onSubmit={(instr) => void submit(instr)}
        />
      )}

      {active && activePlaced && popPos && (
        <div
          ref={popRef}
          className="absolute z-20 rounded-2xl p-4"
          style={{
            top: popPos.top,
            left: popPos.left,
            width: popPos.width,
            whiteSpace: "normal",
            background: "var(--color-bg)",
            border: "1px solid var(--color-border)",
            boxShadow: "0 12px 32px rgba(30,27,75,0.16)",
          }}
          onPointerDown={(e) => e.stopPropagation()}
          onMouseUp={(e) => e.stopPropagation()}
        >
          <ProposalCard
            item={active}
            onAccept={() => {
              model.accept(active.proposal.id);
              setActiveId(null);
            }}
            onReject={() => {
              model.reject(active.proposal.id);
              setActiveId(null);
            }}
            onReset={() => model.reset(active.proposal.id)}
            onClose={() => setActiveId(null)}
          />
        </div>
      )}
    </div>
  );
}
