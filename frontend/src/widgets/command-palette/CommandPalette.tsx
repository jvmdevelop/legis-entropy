import { useEffect, useState, useCallback } from 'react'
import { Search, Command, FileText, Zap, HelpCircle, ExternalLink } from 'lucide-react'
import { graphApi } from '@/features/graph'
import { useGraphStore } from '@/features/graph'
import type { LawDTO } from '@/entities/graph'
import { toast } from 'sonner'

interface CommandItem {
  id: string
  label: string
  description?: string
  icon: React.ReactNode
  action: () => void
  category: 'law' | 'action' | 'help'
}

export function CommandPalette() {
  const [isOpen, setIsOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<CommandItem[]>([])
  const [selectedIndex, setSelectedIndex] = useState(0)
  const { addGraph } = useGraphStore()

  const actions: CommandItem[] = [
    {
      id: 'new-chat',
      label: 'Новый чат',
      description: 'Начать новую беседу',
      icon: <FileText size={16} />,
      category: 'action',
      action: () => {
        toast.success('Новый чат создан')
        setIsOpen(false)
      },
    },
    {
      id: 'clear-history',
      label: 'Очистить историю',
      description: 'Удалить все графы',
      icon: <Zap size={16} />,
      category: 'action',
      action: () => {
        toast.error('История очищена')
        setIsOpen(false)
      },
    },
    {
      id: 'help',
      label: 'Справка',
      description: 'Горячие клавиши и помощь',
      icon: <HelpCircle size={16} />,
      category: 'help',
      action: () => {
        toast.info('Cmd+K - Поиск · Esc - Закрыть · Enter - Выполнить')
        setIsOpen(false)
      },
    },
  ]

  const searchLaws = useCallback(async (q: string) => {
    if (q.length < 2) {
      setResults(actions)
      return
    }

    try {
      const laws = await graphApi.searchLaws(q)
      const lawItems: CommandItem[] = laws.slice(0, 8).map((law: LawDTO) => ({
        id: law.code,
        label: law.title,
        description: law.code,
        icon: <ExternalLink size={16} />,
        category: 'law' as const,
        action: async () => {
          try {
            const graph = await graphApi.getLawWithRelationships(law.code)
            addGraph(graph)
            toast.success(`Граф "${law.title}" загружен`)
            setIsOpen(false)
          } catch {
            toast.error('Ошибка загрузки графа')
          }
        },
      }))
      setResults([...lawItems, ...actions])
    } catch (err) {
      toast.error('Ошибка поиска')
    }
  }, [actions, addGraph])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        setIsOpen(!isOpen)
        setQuery('')
        setSelectedIndex(0)
      }
      if (isOpen) {
        if (e.key === 'Escape') {
          setIsOpen(false)
        }
        if (e.key === 'ArrowDown') {
          e.preventDefault()
          setSelectedIndex(i => (i + 1) % results.length)
        }
        if (e.key === 'ArrowUp') {
          e.preventDefault()
          setSelectedIndex(i => (i - 1 + results.length) % results.length)
        }
        if (e.key === 'Enter') {
          e.preventDefault()
          results[selectedIndex]?.action()
        }
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, query, results, selectedIndex])

  useEffect(() => {
    searchLaws(query)
  }, [query, searchLaws])

  useEffect(() => {
    if (!isOpen) return
    setResults(actions)
    setSelectedIndex(0)
  }, [isOpen, actions])

  return (
    <>

      <button
        onClick={() => setIsOpen(!isOpen)}
        className="hidden lg:flex items-center gap-2 px-3 h-9 rounded-lg border border-[var(--color-border)] text-xs text-[var(--color-text-muted)] hover:bg-[var(--color-surface-2)] transition-colors"
      >
        <Search size={14} />
        <span>Поиск</span>
        <kbd className="ml-auto text-[10px] px-2 py-0.5 rounded bg-[var(--color-surface-2)] border border-[var(--color-border)]">
          ⌘K
        </kbd>
      </button>

      {isOpen && (
        <div
          className="fixed inset-0 bg-black/40 z-50 flex items-start justify-center pt-12"
          onClick={() => setIsOpen(false)}
        >

          <div
            className="w-full max-w-[600px] mx-auto bg-white rounded-xl border border-[var(--color-border)] shadow-lg overflow-hidden"
            onClick={e => e.stopPropagation()}
          >

            <div className="flex items-center gap-3 px-4 py-3 border-b border-[var(--color-border)]">
              <Search size={18} className="text-[var(--color-accent)]" />
              <input
                autoFocus
                type="text"
                placeholder="Поиск законов, действий..."
                value={query}
                onChange={e => {
                  setQuery(e.target.value)
                  setSelectedIndex(0)
                }}
                className="flex-1 bg-transparent text-sm outline-none text-[var(--color-text)] placeholder:text-[var(--color-text-muted)]"
              />
              <kbd className="text-xs px-2 py-1 rounded bg-[var(--color-surface)] border border-[var(--color-border)] text-[var(--color-text-muted)]">
                Esc
              </kbd>
            </div>

            <div className="max-h-[400px] overflow-y-auto">
              {results.length === 0 ? (
                <div className="px-4 py-12 text-center text-sm text-[var(--color-text-muted)]">
                  Ничего не найдено
                </div>
              ) : (
                <div className="py-2">
                  {results.map((item, index) => (
                    <button
                      key={item.id}
                      onClick={() => item.action()}
                      onMouseEnter={() => setSelectedIndex(index)}
                      className={`w-full text-left px-4 py-2.5 flex items-center gap-3 transition-colors ${
                        index === selectedIndex
                          ? 'bg-[var(--color-accent)] text-white'
                          : 'hover:bg-[var(--color-surface)]'
                      }`}
                    >
                      <span
                        className={`flex-shrink-0 ${
                          index === selectedIndex
                            ? 'text-white'
                            : 'text-[var(--color-accent)]'
                        }`}
                      >
                        {item.icon}
                      </span>
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium">{item.label}</div>
                        {item.description && (
                          <div
                            className={`text-xs ${
                              index === selectedIndex
                                ? 'text-white/70'
                                : 'text-[var(--color-text-muted)]'
                            }`}
                          >
                            {item.description}
                          </div>
                        )}
                      </div>
                      {index === selectedIndex && (
                        <Command size={14} className="opacity-50" />
                      )}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="px-4 py-2 border-t border-[var(--color-border)] bg-[var(--color-surface)] text-xs text-[var(--color-text-muted)] flex gap-4">
              <span>↑↓ Навигация</span>
              <span>Enter Выбор</span>
              <span>Esc Закрыть</span>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
