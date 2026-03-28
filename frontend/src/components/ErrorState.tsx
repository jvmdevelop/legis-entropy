interface ErrorStateProps {
  error: string;
  onRetry: () => void;
}

export function ErrorState({ error, onRetry }: ErrorStateProps) {
  return (
    <div className="h-full flex items-center justify-center bg-white">
      <div className="text-center space-y-4 p-16 rounded-lg border border-gray-200 shadow-sm">
        <div className="w-10 h-10 rounded-full bg-red-50 border border-red-100 flex items-center justify-center mx-auto">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <path d="M9 6v3m0 3h.01M17 9A8 8 0 111 9a8 8 0 0116 0z" stroke="#ef4444" strokeWidth="1.5" strokeLinecap="round"/>
          </svg>
        </div>
        <div>
          <p className="text-sm font-medium text-gray-900">Не удалось загрузить граф</p>
          <p className="text-xs text-gray-400 mt-1">{error}</p>
        </div>
        <button
          onClick={onRetry}
          className="px-8 py-3 text-xs font-medium bg-gray-900 hover:bg-gray-700 text-white rounded-lg transition-colors"
        >
          Повторить
        </button>
      </div>
    </div>
  );
}
