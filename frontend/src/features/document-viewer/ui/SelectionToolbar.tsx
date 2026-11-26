import { useState, type CSSProperties } from "react";
import { Loader2, Sparkles } from "lucide-react";
import { SANS } from "../lib/verdict";

interface Props {
  style: CSSProperties;
  blocked: boolean;
  busy: boolean;
  onSubmit: (instruction: string) => void;
}

export function SelectionToolbar({ style, blocked, busy, onSubmit }: Props) {
  const [instruction, setInstruction] = useState("");

  return (
    <div
      data-dv-toolbar=""
      className="absolute z-30 flex flex-col gap-2 rounded-xl p-2"
      style={{
        ...style,
        fontFamily: SANS,
        background: "var(--color-bg, #fff)",
        border: "1px solid var(--color-border)",
        boxShadow: "0 8px 24px rgba(30,27,75,0.14)",
      }}
      onPointerDown={(e) => e.stopPropagation()}
      onMouseUp={(e) => e.stopPropagation()}
    >
      {blocked ? (
        <span style={{ fontSize: "12px", color: "var(--color-text-muted)", padding: "4px 6px" }}>
          Фрагмент пересекается с другой правкой
        </span>
      ) : (
        <form
          className="flex flex-col gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (!busy) onSubmit(instruction);
          }}
        >
          <input
            value={instruction}
            onChange={(e) => setInstruction(e.target.value)}
            placeholder="Что исправить? (необязательно)"
            maxLength={300}
            className="w-full rounded-lg px-2 py-1.5 text-xs outline-none"
            style={{
              fontFamily: SANS,
              background: "var(--color-surface)",
              border: "1px solid var(--color-border)",
              color: "var(--color-text)",
            }}
          />
          <button
            type="submit"
            disabled={busy}
            className="flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg text-xs cursor-pointer disabled:opacity-60 disabled:cursor-wait"
            style={{ fontFamily: SANS, background: "var(--color-accent)", color: "#fff" }}
          >
            {busy ? <Loader2 size={12} className="animate-spin" /> : <Sparkles size={12} />}
            {busy ? "ИИ думает…" : "Предложить правку ИИ"}
          </button>
        </form>
      )}
    </div>
  );
}
