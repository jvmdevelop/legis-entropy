"use client"

import { Search, Sparkles, RefreshCw, LayoutGrid } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

interface HeaderProps {
  onInspectorToggle: () => void
  isInspectorOpen: boolean
}

export function Header({ onInspectorToggle, isInspectorOpen }: HeaderProps) {
  return (
    <header className="flex h-14 items-center justify-between border-b border-border bg-card px-4">
      <div className="flex items-center gap-6">
        <div className="flex items-center gap-2">
          <span className="text-xl font-bold tracking-tight text-foreground">LE</span>
        </div>
        
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="sm" className="gap-2 text-muted-foreground hover:text-foreground">
            <RefreshCw className="h-4 w-4" />
            Анализ
          </Button>
          <Button variant="ghost" size="sm" className="gap-2 text-muted-foreground hover:text-foreground">
            <Sparkles className="h-4 w-4 text-primary" />
            AI обзор
          </Button>
        </div>
      </div>

      <div className="flex-1 max-w-xl mx-8">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Поиск по НПА..."
            className="w-full bg-secondary border-border pl-10 placeholder:text-muted-foreground focus:ring-primary"
          />
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Button
          variant={isInspectorOpen ? "secondary" : "ghost"}
          size="sm"
          onClick={onInspectorToggle}
          className="gap-2"
        >
          <LayoutGrid className="h-4 w-4" />
          Инспектор
          {isInspectorOpen && (
            <span className="h-2 w-2 rounded-full bg-primary" />
          )}
        </Button>
      </div>
    </header>
  )
}
