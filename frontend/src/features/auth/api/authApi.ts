import { apiClient } from '@/shared/api/client'

export interface LoginRequest  { username: string; password: string }
export interface RegisterRequest { username: string; email: string; password: string; firstName?: string; lastName?: string }

export type PlanType = 'FREE' | 'BASIC' | 'PRO'

export interface AuthResponse {
  token: string
  refreshToken: string
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
  role: string
  planType: PlanType
}

export interface UserInfo {
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
  role: string
  planType: PlanType
}

export const authApi = {
  login(data: LoginRequest) {
    return apiClient.post<AuthResponse>('/auth/login', data).then(r => r.data)
  },
  register(data: RegisterRequest) {
    return apiClient.post<{ message: string }>('/auth/register', data).then(r => r.data)
  },
  me() {
    return apiClient.get<UserInfo>('/auth/me').then(r => r.data)
  },
  refresh(refreshToken: string) {
    return apiClient.post<AuthResponse>('/auth/refresh', { refreshToken }).then(r => r.data)
  },
}
