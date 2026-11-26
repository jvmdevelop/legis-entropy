import { apiClient } from "@/shared/api/client";

export type Modality = "RIGHT" | "OBLIGATION" | "PROHIBITION";

export interface StructurePage {
  page: number;
  start: number;
  end: number;
}

export interface StructureSection {
  number: string;
  title: string;
  start: number;
  end: number;
  page: number;
}

export interface ClauseType {
  code: string;
  label: string;
  source: "rule" | "llm" | string;
}

export interface StructureClause {
  number: string;
  start: number;
  end: number;
  page: number;
  section?: string | null;
  types: ClauseType[];
  party: string | null;
  modality: Modality | null;
  summary: string;
  paragraphs: StructurePage[];
}

export interface DocumentStructure {
  documentId: string;
  pages: StructurePage[];
  sections: StructureSection[];
  clauses: StructureClause[];
}

export const structureApi = {
  get(documentId: string, llm = true) {
    return apiClient
      .get<DocumentStructure>(
        `/brain/documents/${encodeURIComponent(documentId)}/structure`,
        { params: { llm }, timeout: 120_000 },
      )
      .then((r) => r.data);
  },
};
