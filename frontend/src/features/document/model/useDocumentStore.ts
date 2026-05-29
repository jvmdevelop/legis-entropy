import { create } from "zustand";
import { documentApi } from "../api/documentApi";
import type { DocumentStatusResponse } from "@/entities/document/types";

interface DocumentState {
  documents: DocumentStatusResponse[];
  selectedDocumentId: string | null;
  isLoading: boolean;
  isUploading: boolean;
  uploadProgress: number;
  error: string | null;

  fetchDocuments(): Promise<void>;
  uploadDocument(file: File): Promise<string>;
  deleteDocument(id: string): Promise<void>;
  reindexDocument(id: string): Promise<void>;
  selectDocument(id: string | null): void;
  pollStatus(id: string): Promise<void>;
  reset(): void;
}

export const useDocumentStore = create<DocumentState>((set, get) => ({
  documents: [],
  selectedDocumentId: null,
  isLoading: false,
  isUploading: false,
  uploadProgress: 0,
  error: null,

  async fetchDocuments() {
    set({ isLoading: true, error: null });
    try {
      const page = await documentApi.list(0, 50);
      set({ documents: page.content, isLoading: false });
    } catch {
      set({ error: "Failed to load documents", isLoading: false });
    }
  },

  async uploadDocument(file) {
    set({ isUploading: true, uploadProgress: 0, error: null });
    try {
      const docId = await documentApi.upload(file, (pct) => {
        set({ uploadProgress: pct });
      });
      set({ isUploading: false, uploadProgress: 0 });
      await get().fetchDocuments();
      get().pollStatus(docId);
      return docId;
    } catch (err) {
      set({ isUploading: false, uploadProgress: 0, error: "Upload failed" });
      throw err;
    }
  },

  async deleteDocument(id) {
    await documentApi.delete(id);
    set((s) => ({
      documents: s.documents.filter((d) => d.id !== id),
      selectedDocumentId:
        s.selectedDocumentId === id ? null : s.selectedDocumentId,
    }));
  },

  async reindexDocument(id) {
    await documentApi.reindex(id);
    set((s) => ({
      documents: s.documents.map((d) =>
        d.id === id ? { ...d, status: "INDEXING" as const } : d,
      ),
    }));
    get().pollStatus(id);
  },

  selectDocument(id) {
    set({ selectedDocumentId: id });
  },

  async pollStatus(id) {
    const maxAttempts = 60;
    let attempt = 0;

    const poll = async () => {
      if (attempt++ > maxAttempts) return;
      try {
        const doc = await documentApi.getStatus(id);
        set((s) => ({
          documents: s.documents.map((d) => (d.id === id ? doc : d)),
        }));
        if (doc.status === "INDEXING" || doc.status === "UPLOADING") {
          setTimeout(poll, 3_000);
        }
      } catch {

      }
    };

    setTimeout(poll, 2_000);
  },

  reset() {
    set({
      documents: [],
      selectedDocumentId: null,
      isLoading: false,
      isUploading: false,
      uploadProgress: 0,
      error: null,
    });
  },
}));
