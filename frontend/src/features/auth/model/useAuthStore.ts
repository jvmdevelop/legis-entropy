import { create } from 'zustand'
import { authApi, type LoginRequest, type RegisterRequest, type UserInfo } from '../api/authApi'
import { tokenStore, refreshStore } from '@/shared/api/client'

interface AuthState {
  user: UserInfo | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null

  login(credentials: LoginRequest): Promise<void>
  register(data: RegisterRequest): Promise<void>
  logout(): void
  refresh(): Promise<void>
  fetchMe(): Promise<void>
  clearError(): void
  bootstrapFromStorage(): Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,

  clearError() {
    set({ error: null })
  },

  async login(credentials) {
    set({ isLoading: true, error: null })
    try {
      const data = await authApi.login(credentials)
      tokenStore.set(data.token)
      refreshStore.set(data.refreshToken)
      set({
        user: {
          id: data.id,
          username: data.username,
          email: data.email,
          firstName: data.firstName,
          lastName: data.lastName,
          role: data.role ?? 'ROLE_USER',
          planType: data.planType ?? 'FREE',
        },
        isAuthenticated: true,
        isLoading: false,
      })
    } catch (err: unknown) {
      const msg = extractMessage(err) ?? 'Login failed. Check your credentials.'
      set({ error: msg, isLoading: false })
      throw err
    }
  },

  async register(data) {
    set({ isLoading: true, error: null })
    try {
      await authApi.register(data)
      set({ isLoading: false })
    } catch (err: unknown) {
      const msg = extractMessage(err) ?? 'Registration failed.'
      set({ error: msg, isLoading: false })
      throw err
    }
  },

  logout() {
    tokenStore.clear()
    refreshStore.clear()
    set({ user: null, isAuthenticated: false, error: null })
  },

  async refresh() {
    const rt = refreshStore.get()
    if (!rt) { get().logout(); return }
    try {
      const data = await authApi.refresh(rt)
      tokenStore.set(data.token)
      refreshStore.set(data.refreshToken)
      set({
        user: {
          id: data.id,
          username: data.username,
          email: data.email,
          firstName: data.firstName,
          lastName: data.lastName,
          role: data.role ?? 'ROLE_USER',
          planType: data.planType ?? 'FREE',
        },
        isAuthenticated: true,
      })
    } catch {
      get().logout()
    }
  },

  async fetchMe() {
    try {
      const user = await authApi.me()
      set({ user, isAuthenticated: true })
    } catch {
      get().logout()
    }
  },

  async bootstrapFromStorage() {
    const rt = refreshStore.get()
    if (!rt) { set({ isLoading: false }); return }
    set({ isLoading: true })
    try {
      await get().refresh()
    } finally {
      set({ isLoading: false })
    }
  },
}))

function extractMessage(err: unknown): string | null {
  if (!err || typeof err !== 'object') return null
  const e = err as Record<string, unknown>
  const resp = e['response'] as Record<string, unknown> | undefined
  if (resp) {
    const data = resp['data'] as Record<string, unknown> | undefined
    if (typeof data?.['message'] === 'string') return data['message']
    if (typeof data?.['error'] === 'string') return data['error']
  }
  if (typeof e['message'] === 'string') return e['message']
  return null
}
