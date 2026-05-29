import { create } from 'zustand'

interface BreadcrumbState {
  workspaceName: string | null
  graphName: string | null
  setWorkspace: (name: string | null) => void
  setGraph: (name: string | null) => void
  clear: () => void
}

export const useBreadcrumbStore = create<BreadcrumbState>((set) => ({
  workspaceName: null,
  graphName: null,
  setWorkspace: (name) => set({ workspaceName: name }),
  setGraph: (name) => set({ graphName: name }),
  clear: () => set({ workspaceName: null, graphName: null }),
}))
