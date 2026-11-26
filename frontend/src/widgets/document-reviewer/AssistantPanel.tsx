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
  { id: "edits", label: "правки ИИ" },
  { id: "structure", label: "структура" },
  { id: "policy", label: "правила организации" },
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
      <div role="tablist" className="le-chips">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={"le-chip" + (tab === t.id ? " le-chip--on" : "")}
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
