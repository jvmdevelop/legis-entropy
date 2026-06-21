import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { apiClient } from '@/shared/api/client'
import { useBreadcrumbStore } from '@/shared/lib/useBreadcrumbStore'

export interface Workspace {
  id: string
  name: string
  description?: string
  createdAt: string
  updatedAt?: string
}

export function formatRelative(s: string): string {
  const d = new Date(s)
  const diffMs = Date.now() - d.getTime()
  const min = Math.floor(diffMs / 60_000)
  const hr = Math.floor(diffMs / 3_600_000)
  const day = Math.floor(diffMs / 86_400_000)
  if (min < 1) return 'только что'
  if (min < 60) return `${min} мин назад`
  if (hr < 24) return `${hr} ч назад`
  if (day < 7) return `${day} дн назад`
  return d.toLocaleDateString('ru-RU')
}

export function useWorkspacesPage() {
  const navigate = useNavigate()
  const clearCrumbs = useBreadcrumbStore((s) => s.clear)

  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const [newDesc, setNewDesc] = useState('')
  const [creating, setCreating] = useState(false)
  const nameInputRef = useRef<HTMLInputElement>(null)

  async function load() {
    setLoading(true)
    try {
      const { data } = await apiClient.get('/v1/workspaces?page=0&size=100')
      setWorkspaces(data.content || [])
    } catch (e) {
      console.error(e)
      toast.error('Не удалось загрузить workspace-ы')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    clearCrumbs()
    void load()
  }, [clearCrumbs])

  async function create(e?: React.FormEvent) {
    e?.preventDefault()
    const name = newName.trim()
    if (!name) return
    setCreating(true)
    try {
      const { data } = await apiClient.post<Workspace>('/v1/workspaces', {
        name,
        description: newDesc.trim(),
      })
      setWorkspaces((prev) => [data, ...prev])
      setNewName('')
      setNewDesc('')
      setShowCreate(false)
      toast.success(`Workspace «${data.name}» создан`)
      navigate(`/workspace/${data.id}`)
    } catch (e) {
      console.error(e)
      toast.error('Не удалось создать workspace')
    } finally {
      setCreating(false)
    }
  }

  async function remove(w: Workspace) {
    if (!window.confirm(`Удалить «${w.name}» со всеми graph_views?`)) return
    try {
      await apiClient.delete(`/v1/workspaces/${w.id}`)
      setWorkspaces((prev) => prev.filter((x) => x.id !== w.id))
      toast.success('Workspace удалён')
    } catch (e) {
      console.error(e)
      toast.error('Не удалось удалить workspace')
    }
  }

  useEffect(() => {
    if (showCreate) setTimeout(() => nameInputRef.current?.focus(), 30)
  }, [showCreate])

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase()
    if (!q) return workspaces
    return workspaces.filter(
      (w) =>
        w.name.toLowerCase().includes(q) || (w.description ?? '').toLowerCase().includes(q),
    )
  }, [workspaces, filter])

  return {
    navigate,
    workspaces,
    filtered,
    loading,
    filter,
    setFilter,
    showCreate,
    setShowCreate,
    newName,
    setNewName,
    newDesc,
    setNewDesc,
    creating,
    nameInputRef,
    create,
    remove,
  }
}

export type WorkspacesPageState = ReturnType<typeof useWorkspacesPage>
