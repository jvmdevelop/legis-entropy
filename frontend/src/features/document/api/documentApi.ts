import { apiClient } from "@/shared/api/client";
import type { DocumentStatusResponse, Page } from "@/entities/document/types";

export type { DocumentStatusResponse };
export type { Page };

export const documentApi = {
  list(page = 0, size = 20) {
    return apiClient
      .get<
        Page<DocumentStatusResponse>
      >("/user-documents", { params: { page, size } })
      .then((r) => r.data);
  },

  upload(file: File, onProgress?: (pct: number) => void) {
    const form = new FormData();
    form.append("file", file);
    return apiClient
      .post<{ documentId: string } | string>("/user-documents/upload", form, {
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (e) => {
          if (onProgress && e.total) {
            onProgress(Math.round((e.loaded * 100) / e.total));
          }
        },
      })
      .then((r) => {
        const data = r.data;
        if (typeof data === "string") return data;
        return data.documentId;
      });
  },

  getStatus(documentId: string) {
    return apiClient
      .get<DocumentStatusResponse>(`/user-documents/${documentId}/status`)
      .then((r) => r.data);
  },

  delete(documentId: string) {
    return apiClient.delete(`/user-documents/${documentId}`);
  },

  reindex(documentId: string) {
    return apiClient.post(`/user-documents/${documentId}/reindex`);
  },

  text(documentId: string) {
    return apiClient
      .get<{ text: string }>(`/user-documents/${documentId}/text`)
      .then((r) => r.data?.text ?? "");
  },
};
