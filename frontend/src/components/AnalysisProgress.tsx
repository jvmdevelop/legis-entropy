import type { StreamState } from '../hooks/useAnalysisStream';

interface Props {
  state: StreamState;
  onStop: () => void;
}

export function AnalysisProgress({ state, onStop }: Props) {
  if (!state.running && !state.error) return null;

  return (
    <div className="absolute inset-x-0 top-16 z-30 flex justify-center px-8 pointer-events-none">
      <div className="pointer-events-auto w-full max-w-sm rounded-lg bg-white/95 backdrop-blur-md border border-white/20 elevation-3 overflow-hidden">
        <div className={`h-0.5 ${state.error ? 'bg-gradient-to-r from-red-400 to-red-500' : 'bg-gradient-to-r from-gray-600 to-gray-700'}`} />

        <div className="p-6">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-3">
              {state.error ? (
                <div className="w-8 h-8 rounded-lg bg-red-50 border border-red-200 flex items-center justify-center elevation-1">
                  <svg width="14" height="14" viewBox="0 0 12 12" fill="none">
                    <path d="M6 3.5v3M6 8.5h.01M11 6A5 5 0 111 6a5 5 0 0110 0z" stroke="#ef4444" strokeWidth="1.3" strokeLinecap="round"/>
                  </svg>
                </div>
              ) : (
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-gray-50 to-slate-50 border border-gray-300 flex items-center justify-center elevation-1">
                  <div className="w-4 h-4 border-2 border-gray-600 border-t-transparent rounded-full animate-spin" />
                </div>
              )}
              <span className="text-xs font-bold text-gray-900">
                {state.error ? 'Ошибка' : 'Анализ НПА'}
              </span>
            </div>
            {state.running && (
              <button
                onClick={onStop}
                className="text-[10px] text-gray-400 hover:text-gray-600 px-2 py-2 rounded-lg hover:bg-gray-50 transition-all duration-200 ripple"
              >
                Отмена
              </button>
            )}
          </div>

          {state.error ? (
            <p className="text-xs text-red-600 leading-relaxed">{state.error}</p>
          ) : (
            <>
              <p className="text-[11px] text-gray-500 mb-6 leading-relaxed">{state.message}</p>
              <div className="space-y-3">
                <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-gray-600 to-gray-700 transition-all duration-500"
                    style={{ width: `${state.progress}%` }}
                  />
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[10px] text-gray-400">{state.message ? 'Выполняется…' : ''}</span>
                  <span className="text-[10px] text-gray-400 font-mono">{state.progress}%</span>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
