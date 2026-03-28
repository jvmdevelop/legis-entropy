interface CorpusReviewPanelProps {
  corpusReview: { review: string; llm_ready: boolean } | null;
  onClose: () => void;
}

export function CorpusReviewPanel({ corpusReview, onClose }: CorpusReviewPanelProps) {
  if (!corpusReview) return null;

  return (
    <div className="absolute top-[52px] left-4 z-20 w-80 rounded-lg bg-white border border-gray-300 shadow-lg overflow-hidden">
      <div className="h-0.5 bg-gray-800" />
      <div className="p-5 space-y-2.5">
        <div className="flex items-center justify-between">
          <span className="text-[9px] font-bold uppercase tracking-widest text-gray-700">
            {corpusReview.llm_ready ? '✦ AI-обзор корпуса (Qwen2)' : '⏳ AI-обзор'}
          </span>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
              <path d="M1 1l8 8M9 1L1 9" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
            </svg>
          </button>
        </div>
        <p className="text-[11px] text-gray-700 leading-relaxed">{corpusReview.review}</p>
      </div>
    </div>
  );
}
