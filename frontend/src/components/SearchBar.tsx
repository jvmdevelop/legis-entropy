import { useState, useRef } from 'react';
import type { SearchResult } from '../types';

interface Props {
  onResults: (ids: string[]) => void;
}

export function SearchBar({ onResults }: Props) {
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
    <div className="relative w-64">
      <input
        type="text"
        value={query}
        onChange={e => handleChange(e.target.value)}
        placeholder="Семантический поиск..."
        className="w-full text-xs bg-slate-800 border border-slate-600 text-slate-200 rounded px-3 py-1.5 pr-7 focus:outline-none focus:border-violet-500 placeholder-slate-500"
      />
      {loading ? (
        <div className="absolute right-2 top-1/2 -translate-y-1/2 w-3 h-3 border border-violet-400 border-t-transparent rounded-full animate-spin" />
      ) : query ? (
        <button
          onClick={() => { setQuery(''); onResults([]); }}
          className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200 text-sm leading-none"
        >
          ×
        </button>
      ) : null}
    </div>
  );
}
