import type { StreamState } from '../hooks/useAnalysisStream';

interface Props {
  state: StreamState;
  onStop: () => void;
}

export function AnalysisProgress({ state, onStop }: Props) {
  if (!state.running && !state.error) return null;

  return (
    <div className="absolute inset-x-4 top-14 z-30 bg-slate-900 border border-slate-700 rounded-lg p-4 shadow-xl max-w-md mx-auto">
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-medium text-slate-300">
          {state.error ? 'Ошибка' : 'Анализ НПА'}
        </span>
        {state.running && (
          <button onClick={onStop} className="text-slate-400 hover:text-slate-200 text-xs">
            Отмена
          </button>
        )}
      </div>

      {state.error ? (
        <p className="text-xs text-red-400">{state.error}</p>
      ) : (
        <>
          <p className="text-xs text-slate-400 mb-2">{state.message}</p>
          <div className="w-full bg-slate-700 rounded-full h-1.5">
            <div
              className="bg-violet-500 h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${state.progress}%` }}
            />
          </div>
          <span className="text-xs text-slate-500 mt-1 block text-right">{state.progress}%</span>
        </>
      )}
    </div>
  );
}
