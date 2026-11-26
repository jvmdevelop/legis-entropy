import { apiClient } from "@/shared/api/client";
import type { EditProposal } from "@/features/message/model/useStreamingMessage";

export interface EditSkip {
  id: string;
  reason: string;
}

export interface EditApplyResponse {
  documentId: string | null;
  fileName: string | null;
  applied: string[];
  skipped: EditSkip[];
}

export interface EditSuggestResponse {
  documentId: string;
  summary?: string;
  markdown?: string;
  problems?: number;
  proposals: EditProposal[];
}

export interface EditRewriteRequest {
  original: string;
  instruction?: string;
}

const base = (documentId: string) =>
  `/brain/documents/${encodeURIComponent(documentId)}/edits`;

export const editsApi = {
  suggest(documentId: string) {
    return apiClient
      .post<EditSuggestResponse>(`${base(documentId)}/suggest`, undefined, {
        timeout: 300_000,
      })
      .then((r) => r.data);
  },

  rewrite(documentId: string, body: EditRewriteRequest) {
    return apiClient
      .post<EditProposal>(`${base(documentId)}/rewrite`, body, { timeout: 120_000 })
      .then((r) => r.data);
  },

  apply(documentId: string, proposals: EditProposal[]) {
    return apiClient
      .post<EditApplyResponse>(
        `${base(documentId)}/apply`,
        { proposals },
        { timeout: 120_000 },
      )
      .then((r) => r.data);
  },
};
