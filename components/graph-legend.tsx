"use client"

import { Badge } from "@/components/ui/badge"

const legendItems = [
  { label: "Действующий", color: "bg-status-active" },
  { label: "Утратил силу", color: "bg-status-inactive" },
  { label: "Неизвестен", color: "bg-status-unknown" },
  { label: "Акт изменений", color: "bg-status-amendment" }
]

export function GraphLegend() {
  return (
    <div className="absolute bottom-6 left-1/2 -translate-x-1/2 z-10">
      <div className="flex items-center gap-1 rounded-full border border-border bg-card/90 backdrop-blur-sm px-4 py-2 shadow-lg">
        {legendItems.map((item, index) => (
          <div key={item.label} className="flex items-center gap-2">
            <Badge 
              variant="outline" 
              className="flex items-center gap-2 border-0 bg-transparent hover:bg-secondary/50 px-3 py-1"
            >
              <span className={`h-3 w-3 rounded-full ${item.color}`} />
              <span className="text-sm text-foreground">{item.label}</span>
            </Badge>
            {index < legendItems.length - 1 && (
              <div className="h-4 w-px bg-border" />
            )}
          </div>
        ))}
        <div className="h-4 w-px bg-border ml-2" />
        <span className="text-xs text-muted-foreground ml-2">Размер — ссылки</span>
      </div>
    </div>
  )
}
