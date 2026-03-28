import { AlertCircle } from 'lucide-react';

interface ErrorStateProps {
  error: string;
  onRetry: () => void;
}

export function ErrorState({ error, onRetry }: ErrorStateProps) {
  return (
    <div className="h-full flex items-center justify-center bg-background">
      <div className="text-center space-y-4 p-12 rounded-lg border border-border bg-card shadow-xl">
        <div className="w-10 h-10 rounded-full bg-destructive/10 border border-destructive/30 flex items-center justify-center mx-auto">
          <AlertCircle className="h-5 w-5 text-destructive" />
        </div>
        <div>
          <p className="text-sm font-medium text-foreground">Не удалось загрузить граф</p>
          <p className="text-xs text-muted-foreground mt-1">{error}</p>
        </div>
        <button
          onClick={onRetry}
          className="px-6 py-2 text-sm font-medium bg-primary hover:bg-primary/90 text-primary-foreground rounded-md transition-colors"
        >
          Повторить
        </button>
      </div>
    </div>
  );
}
