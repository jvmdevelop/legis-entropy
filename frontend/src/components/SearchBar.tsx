import { useState, useRef } from 'react';
import type { SearchResult } from '../types';

interface Props {
  onResults: (ids: string[]) => void;
  embedded?: boolean;
}

export function SearchBar({ onResults, embedded }: Props) {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const debounce = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleChange = (value: string) => {
    setQuery(value);
    if (debounce.current) clearTimeout(debounce.current);
    if (!value.trim()) { onResults([]); return; }

    debounce.current = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await fetch('/api/search', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ query: value, top_k: 6 }),
        });
        const data: { results: SearchResult[] } = await res.json();
        onResults(data.results.map(r => r.id));
      } catch {
        onResults([]);
      } finally {
        setLoading(false);
      }
    }, 350);
  };

  return (
    <div className="relative w-44">
      {/* Search icon */}
      <div className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none">
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
          <circle cx="5" cy="5" r="3.5" stroke="#9ca3af" strokeWidth="1.3"/>
          <path d="M8 8l2.5 2.5" stroke="#9ca3af" strokeWidth="1.3" strokeLinecap="round"/>
        </svg>
      </div>

      <input
        type="text"
        value={query}
        onChange={e => handleChange(e.target.value)}
        placeholder="Поиск по НПА…"
        className={`w-full text-xs text-gray-700 pl-9 pr-9 py-3 focus:outline-none placeholder-gray-400 transition-colors ${
          embedded
            ? 'bg-transparent border-0 focus:ring-0'
            : 'bg-white border border-gray-200 rounded-lg shadow-sm hover:border-gray-300 focus:border-gray-400 focus:ring-0'
        }`}
      />

      {loading ? (
        <div className="absolute right-3 top-1/2 -translate-y-1/2 w-3 h-3 border border-gray-400 border-t-transparent rounded-full animate-spin" />
      ) : query ? (
        <button
          onClick={() => { setQuery(''); onResults([]); }}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
        >
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
            <path d="M1 1l8 8M9 1L1 9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
          </svg>
        </button>
      ) : null}
    </div>
  );
}
