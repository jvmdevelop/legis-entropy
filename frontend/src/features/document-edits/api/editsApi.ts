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

export const editsApi = {
  apply(documentId: string, proposals: EditProposal[]) {
    return apiClient
      .post<EditApplyResponse>(
        `/brain/documents/${encodeURIComponent(documentId)}/edits/apply`,
        { proposals },
        { timeout: 60_000 },
      )
      .then((r) => r.data);
  },
};
