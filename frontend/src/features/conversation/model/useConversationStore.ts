import { create } from "zustand";
import { conversationApi } from "../api/conversationApi";
import type { ConversationResponse } from "@/entities/conversation/types";

interface ConversationState {
  conversations: ConversationResponse[];
  activeId: string | null;
  isLoading: boolean;
  error: string | null;

  fetchConversations(): Promise<void>;
  createConversation(title?: string): Promise<ConversationResponse>;
  deleteConversation(id: string): Promise<void>;
  setActive(id: string | null): void;
  toggleActive(id: string): Promise<void>;
  reset(): void;
}

export const useConversationStore = create<ConversationState>((set) => ({
  conversations: [],
  activeId: null,
  isLoading: false,
  error: null,

  async fetchConversations() {
    set({ isLoading: true, error: null });
    try {
      const page = await conversationApi.listActive(0, 50);
      set({ conversations: page.content, isLoading: false });
    } catch {
      set({ error: "Failed to load conversations", isLoading: false });
    }
  },

  async createConversation(title) {
    const finalTitle =
      title?.trim() || `Conversation ${new Date().toLocaleDateString()}`;
    const conv = await conversationApi.create(finalTitle);
    set((s) => ({
      conversations: [conv, ...s.conversations],
      activeId: conv.id,
    }));
    return conv;
  },

  async deleteConversation(id) {
    await conversationApi.delete(id);
    set((s) => {
      const convs = s.conversations.filter((c) => c.id !== id);
      return {
        conversations: convs,
        activeId: s.activeId === id ? (convs[0]?.id ?? null) : s.activeId,
      };
    });
  },

  setActive(id) {
    set({ activeId: id });
  },

  async toggleActive(id) {
    const updated = await conversationApi.toggleActive(id);
    set((s) => ({
      conversations: s.conversations.map((c) => (c.id === id ? updated : c)),
    }));
  },

  reset() {
    set({ conversations: [], activeId: null, isLoading: false, error: null });
  },
}));

export const selectActiveConversation = (s: ConversationState) =>
  s.conversations.find((c) => c.id === s.activeId) ?? null;
