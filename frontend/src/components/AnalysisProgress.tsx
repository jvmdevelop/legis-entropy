import { AlertCircle } from 'lucide-react';
import type { StreamState } from '../hooks/useAnalysisStream';

interface Props {
  state: StreamState;
  onStop: () => void;
}

export function AnalysisProgress({ state, onStop }: Props) {
  if (!state.running && !state.error) return null;

  return (
    <div className="absolute inset-x-0 top-4 z-30 flex justify-center px-8 pointer-events-none">
      <div className="pointer-events-auto w-full max-w-sm rounded-lg border border-border bg-card/95 backdrop-blur-sm shadow-xl overflow-hidden">
        <div className={`h-0.5 ${state.error ? 'bg-destructive' : 'bg-primary'}`} />

        <div className="p-5">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              {state.error ? (
                <div className="w-8 h-8 rounded-full bg-destructive/10 border border-destructive/30 flex items-center justify-center">
                  <AlertCircle className="h-4 w-4 text-destructive" />
                </div>
              ) : (
                <div className="w-8 h-8 rounded-md bg-secondary flex items-center justify-center">
                  <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                </div>
              )}
              <span className="text-sm font-medium text-foreground">
                {state.error ? 'Ошибка' : 'Анализ НПА'}
              </span>
            </div>
            {state.running && (
              <button
                onClick={onStop}
                className="text-xs text-muted-foreground hover:text-foreground px-2 py-1 rounded-md hover:bg-secondary transition-colors"
              >
                Отмена
              </button>
            )}
          </div>

          {state.error ? (
            <p className="text-sm text-destructive leading-relaxed">{state.error}</p>
          ) : (
            <>
              <p className="text-xs text-muted-foreground mb-4 leading-relaxed">{state.message}</p>
              <div className="space-y-2">
                <div className="w-full h-1.5 bg-secondary rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full bg-primary transition-all duration-500"
                    style={{ width: `${state.progress}%` }}
                  />
                </div>
                <div className="flex justify-end">
                  <span className="text-xs text-muted-foreground font-mono">{state.progress}%</span>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
