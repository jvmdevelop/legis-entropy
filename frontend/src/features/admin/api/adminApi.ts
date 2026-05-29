import { apiClient } from '@/shared/api/client'

export interface AdminUser {
  id: number
  username: string
  email: string
  firstName: string
  lastName: string
  active: boolean
  role: string
  planType: string
  createdAt: string
}

export interface PagedUsers {
  content: AdminUser[]
  page: {
    totalElements: number
    totalPages: number
    number: number
    size: number
  }
}

export interface AdminStats {
  totalUsers: number
  activeUsers: number
  adminUsers: number
}

export interface CreateUserRequest {
  username: string
  email: string
  password: string
  firstName?: string
  lastName?: string
  role?: string
  planType?: string
}

export const adminApi = {
  getUsers(page = 0, size = 20) {
    return apiClient.get<PagedUsers>(`/admin/users?page=${page}&size=${size}`).then(r => r.data)
  },

  toggleActive(id: number) {
    return apiClient.patch<AdminUser>(`/admin/users/${id}/toggle-active`).then(r => r.data)
  },

  updateRole(id: number, role: string) {
    return apiClient.patch<AdminUser>(`/admin/users/${id}/role`, { role }).then(r => r.data)
  },

  updatePlan(id: number, planType: string) {
    return apiClient.patch<AdminUser>(`/admin/users/${id}/plan`, { planType }).then(r => r.data)
  },

  deleteUser(id: number) {
    return apiClient.delete(`/admin/users/${id}`)
  },

  getStats() {
    return apiClient.get<AdminStats>('/admin/stats').then(r => r.data)
  },

  createUser(req: CreateUserRequest) {
    return apiClient.post<AdminUser>('/admin/users', req).then(r => r.data)
  },
}
