import { useState, useCallback, useRef, useEffect } from 'react'
import { Search, Loader2, X } from 'lucide-react'
import { graphApi } from '@/features/graph'
import { useGraphStore } from '@/features/graph'
import type { LawDTO } from '@/entities/graph'
import { toast } from 'sonner'

export function LawSearch() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<LawDTO[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [showResults, setShowResults] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const { addGraph } = useGraphStore()

  const searchLaws = useCallback(async (q: string) => {
    if (q.length < 2) {
      setResults([])
      return
    }

    setIsLoading(true)
    try {
      const laws = await graphApi.searchLaws(q)
      setResults(laws.slice(0, 6))
    } catch (err) {
      toast.error('Ошибка поиска')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const timer = setTimeout(() => {
      searchLaws(query)
    }, 300)
    return () => clearTimeout(timer)
  }, [query, searchLaws])

  const handleSelect = async (law: LawDTO) => {
    try {
      const graph = await graphApi.getLawWithRelationships(law.code)
      addGraph(graph)
      toast.success(`"${law.title}" добавлен в граф`)
      setQuery('')
      setResults([])
      setShowResults(false)
    } catch {
      toast.error('Ошибка загрузки графа')
    }
  }

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setShowResults(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <div ref={containerRef} className="relative">
      <div className="flex items-center gap-3 px-4 h-11 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-2xl focus-within:border-[var(--color-accent)] focus-within:shadow-[0_0_0_3px_rgba(139,92,246,0.08)] transition-all">
        <Search size={14} className="text-[var(--color-text-muted)]" />
        <input
          type="text"
          placeholder="Поиск законов..."
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setShowResults(true)
          }}
          onFocus={() => setShowResults(true)}
          className="flex-1 bg-transparent outline-none text-sm text-[var(--color-text)] placeholder:text-[var(--color-text-muted)]"
        />
        {query && (
          <button
            onClick={() => {
              setQuery('')
              setResults([])
            }}
            className="p-0.5 hover:bg-[var(--color-surface-2)] rounded transition-colors"
          >
            <X size={12} className="text-[var(--color-text-muted)]" />
          </button>
        )}
      </div>

      {showResults && (results.length > 0 || isLoading) && (
        <div className="absolute top-full left-0 right-0 mt-2 bg-white border border-[var(--color-border)] rounded-2xl shadow-lg z-40">
          {isLoading ? (
            <div className="flex items-center justify-center py-4">
              <Loader2 size={14} className="text-[var(--color-accent)] animate-spin" />
            </div>
          ) : (
            <div className="max-h-[240px] overflow-y-auto">
              {results.map((law) => (
                <button
                  key={law.code}
                  onClick={() => handleSelect(law)}
                  className="w-full text-left px-4 py-3 hover:bg-[var(--color-surface)] transition-colors border-b border-[var(--color-border-light)] last:border-0"
                >
                  <div className="text-sm font-medium text-[var(--color-text)] truncate">{law.title}</div>
                  <div className="text-xs text-[var(--color-text-muted)] mt-1">{law.code} · {law.type}</div>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
