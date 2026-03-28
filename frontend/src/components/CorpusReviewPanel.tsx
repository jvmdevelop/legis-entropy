import { X } from 'lucide-react';

interface CorpusReviewPanelProps {
  corpusReview: { review: string; llm_ready: boolean } | null;
  onClose: () => void;
}

export function CorpusReviewPanel({ corpusReview, onClose }: CorpusReviewPanelProps) {
  if (!corpusReview) return null;

  return (
    <div className="absolute top-4 left-4 z-20 w-80 rounded-lg border border-border bg-card/95 backdrop-blur-sm shadow-xl overflow-hidden">
      <div className={`h-0.5 ${corpusReview.llm_ready ? 'bg-primary' : 'bg-muted'}`} />
      <div className="p-4 space-y-2.5">
        <div className="flex items-center justify-between">
          <span className={`text-xs font-bold uppercase tracking-wider ${corpusReview.llm_ready ? 'text-primary' : 'text-muted-foreground'}`}>
            {corpusReview.llm_ready ? '✦ AI-обзор корпуса (Qwen2)' : '⏳ AI-обзор'}
          </span>
          <button
            onClick={onClose}
            className="h-6 w-6 flex items-center justify-center rounded-md text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
        <p className="text-sm text-foreground leading-relaxed">{corpusReview.review}</p>
      </div>
    </div>
  );
}
