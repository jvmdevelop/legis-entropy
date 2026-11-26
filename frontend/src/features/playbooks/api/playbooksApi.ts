import { apiClient } from "@/shared/api/client";

export type RuleKind = "required" | "forbidden" | "max_days" | "text";
export type RuleModality = "RIGHT" | "OBLIGATION" | "PROHIBITION";
export type RuleStatus = "OK" | "VIOLATED" | "NOT_APPLICABLE" | "UNKNOWN";

export interface PlaybookRule {
  id: string;
  kind: RuleKind;
  text: string;
  clauseType?: string | null;
  party?: string | null;
  modality?: RuleModality | null;
  maxDays?: number | null;
}

export interface Playbook {
  id: string;
  name: string;
  rules: PlaybookRule[];
}

export interface PlaybookRuleResult {
  ruleId: string;
  rule: string;
  status: RuleStatus | string;
  clause: string | null;
  page: number | null;
  quote: string | null;
  explanation: string;
}

export interface PlaybookCheckResponse {
  playbookId: string;
  name: string;
  summary: string;
  results: PlaybookRuleResult[];
}

export function isTemplateId(id: string): boolean {
  return id.startsWith("template-");
}

export const playbooksApi = {
  list() {
    return apiClient.get<Playbook[]>("/brain/playbooks").then((r) => r.data ?? []);
  },

  templates() {
    return apiClient.get<Playbook[]>("/brain/playbooks/templates").then((r) => r.data ?? []);
  },

  save(playbook: Playbook) {
    return apiClient.post<Playbook>("/brain/playbooks", playbook).then((r) => r.data);
  },

  remove(id: string) {
    return apiClient.delete(`/brain/playbooks/${encodeURIComponent(id)}`);
  },

  check(documentId: string, playbookId: string) {
    return apiClient
      .post<PlaybookCheckResponse>(
        `/brain/documents/${encodeURIComponent(documentId)}/playbooks/${encodeURIComponent(playbookId)}/check`,
        undefined,
        { timeout: 180_000 },
      )
      .then((r) => r.data);
  },
};
