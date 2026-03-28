import { LayoutGrid, RefreshCw, Sparkles, Sun, Moon } from 'lucide-react';
import { Logo } from './Logo';
import { SearchBar } from './SearchBar';
import type { useAnalysisStream } from '../hooks/useAnalysisStream';
import type { Theme } from '../hooks/useTheme';

interface NavigationBarProps {
  onStartAnalysis: () => void;
  onFetchCorpusReview: () => void;
  corpusReviewLoading: boolean;
  stream: ReturnType<typeof useAnalysisStream>['state'];
  onInspectorToggle: () => void;
  isInspectorOpen: boolean;
  searchHits: string[];
  onSearchResults: (ids: string[]) => void;
  theme: Theme;
  onThemeToggle: () => void;
}

export function NavigationBar({
  onStartAnalysis,
  onFetchCorpusReview,
  corpusReviewLoading,
  stream,
  onInspectorToggle,
  isInspectorOpen,
  searchHits,
  onSearchResults,
  theme,
  onThemeToggle,
}: NavigationBarProps) {
  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-border bg-card px-4 z-20">
      {/* Left: brand + actions */}
      <div className="flex items-center gap-6">
        <div className="flex items-center gap-2">
          <Logo size={20} theme={theme} />
        </div>

        <div className="flex items-center gap-1">
          <button
            onClick={onStartAnalysis}
            disabled={stream.running}
            className="flex items-center gap-2 px-3 py-1.5 rounded-md text-sm text-muted-foreground hover:text-foreground hover:bg-secondary disabled:opacity-40 transition-colors"
          >
            <RefreshCw className={`h-4 w-4 ${stream.running ? 'animate-spin' : ''}`} />
            {stream.running ? 'Анализ…' : 'Анализ'}
          </button>

          <button
            onClick={onFetchCorpusReview}
            disabled={corpusReviewLoading}
            className="flex items-center gap-2 px-3 py-1.5 rounded-md text-sm text-muted-foreground hover:text-foreground hover:bg-secondary disabled:opacity-40 transition-colors"
          >
            <Sparkles className="h-4 w-4 text-primary" />
            {corpusReviewLoading ? 'AI анализ…' : 'AI обзор'}
          </button>
        </div>
      </div>

      {/* Center: search */}
      <div className="flex-1 max-w-xl mx-8">
        <div className="relative flex items-center h-9 rounded-md border border-border bg-input focus-within:ring-1 focus-within:ring-ring">
          <SearchBar onResults={onSearchResults} embedded />
          {searchHits.length > 0 && (
            <span className="mr-3 px-2 py-0.5 text-[10px] font-bold rounded-full bg-primary text-primary-foreground tabular-nums">
              {searchHits.length}
            </span>
          )}
        </div>
      </div>

      {/* Right: theme toggle + inspector toggle */}
      <div className="flex items-center gap-1">
        <button
          onClick={onThemeToggle}
          title={theme === 'dark' ? 'Светлая тема' : 'Тёмная тема'}
          className="flex items-center justify-center h-8 w-8 rounded-md text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
        >
          {theme === 'dark'
            ? <Sun className="h-4 w-4" />
            : <Moon className="h-4 w-4" />
          }
        </button>

        <button
          onClick={onInspectorToggle}
          className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-sm transition-colors ${
            isInspectorOpen
              ? 'bg-secondary text-foreground'
              : 'text-muted-foreground hover:text-foreground hover:bg-secondary'
          }`}
        >
          <LayoutGrid className="h-4 w-4" />
          Инспектор
          {isInspectorOpen && (
            <span className="h-2 w-2 rounded-full bg-primary" />
          )}
        </button>
      </div>
    </header>
  );
}
