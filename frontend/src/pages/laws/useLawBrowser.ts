import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { graphApi } from '@/features/graph/api/graphApi'
import { useBreadcrumbStore } from '@/shared/lib/useBreadcrumbStore'

export interface LawRow {
  code: string
  title: string
  type: string
  year?: number
  topic?: string
  summary?: string
  status?: string
}

export const LAW_TYPES = ['все типы', 'конституция', 'кодекс', 'закон', 'указ'] as const

export function normalizeType(raw: string | undefined): string {
  if (!raw) return 'закон'
  const t = raw.toLowerCase()
  if (t.includes('конституц')) return 'конституция'
  if (t.includes('кодекс') || t === 'code') return 'кодекс'
  if (t.includes('указ') || t === 'decree') return 'указ'
  return 'закон'
}

export function yearFromDate(d?: string): number | undefined {
  if (!d) return undefined
  const m = /^(\d{4})/.exec(d)
  return m ? Number(m[1]) : undefined
}

export function useLawBrowser() {
  const clearCrumbs = useBreadcrumbStore((s) => s.clear)
  const [q, setQ] = useState('')
  const [type, setType] = useState<string>('все типы')
  const [allLaws, setAllLaws] = useState<LawRow[]>([])
  const [selectedCode, setSelectedCode] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [submittedQuery, setSubmittedQuery] = useState('')

  useEffect(() => {
    clearCrumbs()
  }, [clearCrumbs])

  async function search(query: string) {
    setSubmittedQuery(query)
    setLoading(true)
    try {
      const res = await graphApi.searchLaws(query || ' ', 'RK')
      setAllLaws(
        res.map((l) => ({
          code: l.code,
          title: l.title,
          type: normalizeType(l.type),
          year: yearFromDate(l.adoptionDate ?? l.effectiveDate ?? undefined),
          summary: l.summary,
          status: l.status,
        })),
      )
    } catch (e) {
      console.error(e)
      toast.error('Не удалось загрузить законы')
      setAllLaws([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void search('')
  }, [])

  const typeCounts = useMemo(() => {
    const m: Record<string, number> = {}
    allLaws.forEach((l) => {
      m[l.type] = (m[l.type] || 0) + 1
    })
    return m
  }, [allLaws])

  const filtered = useMemo(() => {
    return allLaws.filter((l) => {
      const okType = type === 'все типы' || l.type === type
      const text = q.trim().toLowerCase()
      const okQ =
        !text ||
        l.title.toLowerCase().includes(text) ||
        l.code.toLowerCase().includes(text) ||
        (l.topic ?? '').toLowerCase().includes(text)
      return okType && okQ
    })
  }, [allLaws, type, q])

  const selected = filtered.find((l) => l.code === selectedCode) ?? null

  return {
    q,
    setQ,
    type,
    setType,
    allLaws,
    loading,
    submittedQuery,
    selectedCode,
    setSelectedCode,
    selected,
    filtered,
    typeCounts,
    search,
  }
}

export type LawBrowserState = ReturnType<typeof useLawBrowser>
