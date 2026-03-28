"use client"

import { X, AlertCircle } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"

interface DocumentComparisonProps {
  onClose: () => void
}

export function DocumentComparison({ onClose }: DocumentComparisonProps) {
  return (
    <div className="absolute left-4 top-4 z-10 w-[600px] rounded-lg border border-border bg-card/95 backdrop-blur-sm shadow-xl">
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-foreground">+ Сравнение документов</span>
        </div>
        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={onClose}>
          <X className="h-4 w-4" />
        </Button>
      </div>
      
      <div className="grid grid-cols-[1fr_auto_1fr] gap-4 p-4">
        <div className="space-y-2">
          <span className="text-xs font-medium uppercase tracking-wider text-primary">Документ A</span>
          <h3 className="text-sm font-semibold text-foreground leading-tight">
            Об административных правонарушениях
          </h3>
          <div className="flex items-center gap-3 text-xs text-muted-foreground">
            <span>430 ссылок</span>
            <span>10 статей</span>
          </div>
          <div className="flex items-center gap-2 text-xs text-destructive">
            <AlertCircle className="h-3 w-3" />
            <span>Ошибка: HTTP 504</span>
          </div>
        </div>

        <div className="flex items-center justify-center">
          <Badge variant="outline" className="bg-secondary text-muted-foreground border-border">
            vs
          </Badge>
        </div>

        <div className="space-y-2">
          <span className="text-xs font-medium uppercase tracking-wider text-primary">Документ Б</span>
          <h3 className="text-sm font-semibold text-foreground leading-tight">
            О Перечне республиканских государственных предприятий
          </h3>
          <div className="flex items-center gap-3 text-xs text-muted-foreground">
            <span>570 ссылок</span>
          </div>
        </div>
      </div>
    </div>
  )
}
