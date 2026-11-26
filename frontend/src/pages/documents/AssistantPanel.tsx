import { useState } from "react";
import {
  StructurePanel,
  SuggestionsPanel,
  type DocumentStructureModel,
  type DocumentViewerModel,
} from "@/features/document-viewer";
import { PlaybookCheckPanel } from "@/features/playbooks";

type Tab = "edits" | "structure" | "policy";

const TABS: { id: Tab; label: string }[] = [
  { id: "edits", label: "Правки ИИ" },
  { id: "structure", label: "Структура" },
  { id: "policy", label: "Правила организации" },
];

interface Props {
  documentId: string;
  model: DocumentViewerModel;
  structure: DocumentStructureModel;
  onFocus: (id: string) => void;
  onApply: () => void;
  onLocate: (clause: string | null, page: number | null) => void;
}

export function AssistantPanel({ documentId, model, structure, onFocus, onApply, onLocate }: Props) {
  const [tab, setTab] = useState<Tab>("edits");

  return (
    <div data-dv-panel="" className="flex flex-col gap-2 min-w-0">
      <div
        role="tablist"
        className="flex flex-wrap gap-1 rounded-xl p-1"
        style={{ background: "var(--color-surface)", border: "1px solid var(--color-border)" }}
      >
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className="flex-1 rounded-lg px-2 py-1.5 text-xs cursor-pointer whitespace-nowrap"
            style={{
              fontFamily: "'IBM Plex Sans', sans-serif",
              background: tab === t.id ? "var(--color-bg, #fff)" : "transparent",
              color: tab === t.id ? "var(--color-accent)" : "var(--color-text-muted)",
              boxShadow: tab === t.id ? "0 1px 3px rgba(30,27,75,0.08)" : undefined,
            }}
          >
            {t.label}
            {t.id === "edits" && model.items.length > 0 ? ` · ${model.items.length}` : ""}
          </button>
        ))}
      </div>
      <div hidden={tab !== "edits"}>
        <SuggestionsPanel model={model} onFocus={onFocus} onApply={onApply} />
      </div>
      <div hidden={tab !== "structure"}>
        <StructurePanel structure={structure} />
      </div>
      <div hidden={tab !== "policy"}>
        <PlaybookCheckPanel documentId={documentId} onLocate={onLocate} />
      </div>
    </div>
  );
}
