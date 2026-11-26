import type { CSSProperties, KeyboardEvent } from "react";
import { Undo2 } from "lucide-react";
import type { Segment } from "../lib/segmentText";
import type { ViewerSuggestion } from "../model/useDocumentViewer";
import { verdictTone } from "../lib/verdict";

interface Props {
  seg: Segment;
  item?: ViewerSuggestion;
  active: boolean;
  dim: boolean;
  flash: boolean;
  onOpen: (id: string) => void;
  onUndo: (id: string) => void;
}

export function TextSegment({ seg, item, active, dim, flash, onOpen, onUndo }: Props) {
  const ambient: CSSProperties = {
    opacity: dim ? 0.35 : undefined,
    transition: "opacity 160ms, background-color 300ms",
  };

  if (seg.kind === "text" || !item) {
    return (
      <span
        data-start={seg.start}
        style={{ ...ambient, backgroundColor: flash ? "rgba(59,130,246,0.14)" : undefined }}
      >
        {seg.text}
      </span>
    );
  }

  const id = seg.id;
  const open = () => onOpen(id);
  const onKey = (e: KeyboardEvent) => {
    if (e.key === "Enter") open();
  };

  if (item.status === "accepted") {
    return (
      <span style={ambient}>
        <span
          data-mark={id}
          data-kind="accepted"
          role="button"
          tabIndex={0}
          onClick={open}
          onKeyDown={onKey}
          title={`Было: ${item.proposal.original}`}
          style={{
            cursor: "pointer",
            color: "var(--color-success)",
            background: "rgba(16,185,129,0.12)",
            borderBottom: "2px solid var(--color-success)",
            borderRadius: 3,
            outline: active ? "2px solid rgba(16,185,129,0.35)" : undefined,
          }}
        >
          {item.proposal.replacement}
        </span>
        <button
          type="button"
          onClick={() => onUndo(id)}
          aria-label="Отменить правку"
          title="Отменить правку"
          className="inline-flex align-middle mx-0.5 cursor-pointer"
          style={{ color: "var(--color-text-muted)" }}
        >
          <Undo2 size={12} />
        </button>
      </span>
    );
  }

  const tone = verdictTone(item.proposal.verdict);
  return (
    <span
      data-mark={id}
      data-start={seg.start}
      role="button"
      tabIndex={0}
      onClick={open}
      onKeyDown={onKey}
      style={{
        ...ambient,
        cursor: "pointer",
        background: tone.background,
        borderBottom: `2px solid ${tone.color}`,
        borderRadius: 3,
        outline: active ? `2px solid ${tone.color}` : undefined,
        outlineOffset: 1,
      }}
    >
      {seg.text}
    </span>
  );
}
