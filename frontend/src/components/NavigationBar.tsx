import { Logo } from './Logo';
import { useAnalysisStream } from '../hooks/useAnalysisStream';

interface NavigationBarProps {
  onStartAnalysis: () => void;
  onFetchCorpusReview: () => void;
  corpusReviewLoading: boolean;
  stream: ReturnType<typeof useAnalysisStream>['state'];
}

export function NavigationBar({ onStartAnalysis, onFetchCorpusReview, corpusReviewLoading, stream }: NavigationBarProps) {
  return (
    <div className="absolute top-4 left-4 z-10">
      <div className="flex items-center h-12 rounded-lg bg-white/90 backdrop-blur-md border border-white/20 elevation-2 overflow-hidden divide-x divide-gray-100">
        {/* Brand */}
        <div className="flex items-center gap-3 px-5 h-full bg-gradient-to-r from-gray-50 to-slate-50">
          <Logo size={20} />
        </div>

        {/* Анализ */}
        <button
          onClick={onStartAnalysis}
          disabled={stream.running}
          className="h-full flex items-center gap-2 px-5 text-[11px] font-medium text-gray-600 hover:text-gray-900 hover:bg-gradient-to-r hover:from-gray-50 hover:to-slate-50 disabled:opacity-40 transition-all duration-300 relative group ripple"
        >
          {stream.running ? (
            <div className="w-4 h-4 border-2 border-gray-600 border-t-transparent rounded-full animate-spin" />
          ) : (
            <svg width="13" height="13" viewBox="0 0 12 12" fill="none" className="group-hover:rotate-90 transition-transform duration-300">
              <path d="M10.5 6A4.5 4.5 0 1 1 7.5 1.9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
              <path d="M7.5 1v2.5H10" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          )}
          <span className="group-hover:translate-x-0.5 transition-transform duration-300">{stream.running ? 'Анализ…' : 'Анализ'}</span>
        </button>

        {/* AI обзор */}
        <button
          onClick={onFetchCorpusReview}
          disabled={corpusReviewLoading}
          className="h-full flex items-center gap-2 px-5 text-[11px] font-medium text-gray-600 hover:text-gray-900 hover:bg-gradient-to-r hover:from-gray-50 hover:to-slate-50 disabled:opacity-40 transition-all duration-300 relative group ripple"
        >
          {corpusReviewLoading ? (
            <div className="w-4 h-4 border-2 border-gray-600 border-t-transparent rounded-full animate-spin" />
          ) : (
            <span className="text-sm leading-none group-hover:scale-110 transition-transform duration-300">✦</span>
          )}
          <span className="group-hover:translate-x-0.5 transition-transform duration-300">{corpusReviewLoading ? 'AI анализ…' : 'AI обзор'}</span>
        </button>
      </div>
    </div>
  );
}
