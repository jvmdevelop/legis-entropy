"use client"

import { X, RefreshCw, Link2, AlertTriangle, FileText, ChevronDown, ExternalLink } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"

interface InspectorProps {
  documentId: string | null
  onClose: () => void
}

export function Inspector({ documentId, onClose }: InspectorProps) {
  if (!documentId) {
    return (
      <aside className="w-[380px] border-l border-border bg-card flex flex-col">
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <div className="flex items-center gap-2">
            <div className="h-3 w-3 rounded-full bg-muted" />
            <span className="font-medium text-foreground">Инспектор</span>
          </div>
          <Button variant="ghost" size="icon" className="h-7 w-7" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>
        <div className="flex flex-1 items-center justify-center p-8 text-center">
          <p className="text-sm text-muted-foreground">
            Выберите документ на графе для просмотра деталей
          </p>
        </div>
      </aside>
    )
  }

  return (
    <aside className="w-[380px] border-l border-border bg-card flex flex-col">
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <div className="flex items-center gap-2">
          <div className="h-3 w-3 rounded-full bg-primary" />
          <span className="font-medium text-foreground">Инспектор</span>
        </div>
        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={onClose}>
          <X className="h-4 w-4" />
        </Button>
      </div>

      <ScrollArea className="flex-1">
        <div className="p-4 space-y-6">
          {/* Document Header */}
          <div className="space-y-3">
            <h2 className="text-lg font-semibold text-foreground leading-tight">
              О Перечне республиканских государственных предприятий
            </h2>
            <div className="flex items-center gap-2 flex-wrap">
              <Badge variant="outline" className="bg-status-inactive/20 text-status-inactive border-status-inactive/30">
                Утратил силу
              </Badge>
              <Badge variant="outline" className="bg-destructive/20 text-destructive border-destructive/30">
                <AlertTriangle className="h-3 w-3 mr-1" />
                2 проблемы
              </Badge>
            </div>
          </div>

          <Separator />

          {/* Document Info */}
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span className="text-muted-foreground">ID документа</span>
                <p className="font-mono text-foreground">P960000790_</p>
              </div>
              <div>
                <span className="text-muted-foreground">Ссылки на НПА</span>
                <p className="text-foreground font-medium">570 документов</p>
              </div>
              <div>
                <span className="text-muted-foreground">Тип документа</span>
                <p className="text-foreground">НПА</p>
              </div>
            </div>
          </div>

          <Separator />

          {/* Issues Section */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-medium text-muted-foreground uppercase tracking-wider">
                Почему помечен (2)
              </h3>
            </div>

            {/* Issue 1 */}
            <div className="rounded-lg border border-border bg-secondary/50 p-4 space-y-3">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="rounded-full bg-status-amendment/20 p-2">
                    <RefreshCw className="h-4 w-4 text-status-amendment" />
                  </div>
                  <div>
                    <h4 className="font-medium text-foreground">Циклические ссылки</h4>
                    <Badge variant="outline" className="mt-1 text-xs bg-status-amendment/20 text-status-amendment border-status-amendment/30">
                      Средний
                    </Badge>
                  </div>
                </div>
              </div>
              <p className="text-sm text-muted-foreground leading-relaxed">
                Циклические ссылки между 5964 документами: Z980000284_, Z930004300_, Z930003700_, Z970000075_, Z970000090_, Z990000391_, Z990000481_, Z980000216_, Z980000302_, ...
              </p>
              <div className="flex items-center justify-between">
                <Button variant="ghost" size="sm" className="text-primary hover:text-primary">
                  <ChevronDown className="h-4 w-4 mr-1" />
                  Подробнее
                </Button>
                <span className="text-xs text-muted-foreground">5964 документа</span>
              </div>
            </div>

            {/* Issue 2 */}
            <div className="rounded-lg border border-border bg-secondary/50 p-4 space-y-3">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="rounded-full bg-status-unknown/20 p-2">
                    <Link2 className="h-4 w-4 text-status-unknown" />
                  </div>
                  <div>
                    <h4 className="font-medium text-foreground">Акт внесения изменений</h4>
                    <Badge variant="outline" className="mt-1 text-xs bg-status-active/20 text-status-active border-status-active/30">
                      Низкий
                    </Badge>
                  </div>
                </div>
              </div>
              <p className="text-sm text-muted-foreground leading-relaxed">
                «О внесении изменений и дополнений в постановления Правительства Республики Казахстан от 25 июня 1996 года № 790 и от 19 ноября 1999 года № 1754» является актом...
              </p>
              <div className="flex items-center justify-between">
                <Button variant="ghost" size="sm" className="text-primary hover:text-primary">
                  <ChevronDown className="h-4 w-4 mr-1" />
                  Подробнее
                </Button>
                <span className="text-xs text-muted-foreground">4 документа</span>
              </div>
            </div>
          </div>
        </div>
      </ScrollArea>

      {/* Actions Footer */}
      <div className="border-t border-border p-4">
        <div className="flex gap-2">
          <Button className="flex-1 bg-primary hover:bg-primary/90 text-primary-foreground">
            Открыть
            <ExternalLink className="h-4 w-4 ml-2" />
          </Button>
          <Button variant="outline" className="flex-1">
            Сравнить
          </Button>
        </div>
      </div>
    </aside>
  )
}
