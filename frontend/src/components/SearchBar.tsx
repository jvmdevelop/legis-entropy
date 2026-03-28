import { useState } from 'react';
import { useSearch } from '../hooks/useSearch';

interface SearchBarProps {
  onResults: (ids: string[]) => void;
  embedded?: boolean;
}

export function SearchBar({ onResults, embedded }: SearchBarProps) {
  const [query, setQuery] = useState('');
  const { loading, search, clear } = useSearch(onResults);

  const handleChange = (value: string) => {
    setQuery(value);
    if (value.trim()) {
      search(value);
    } else {
      clear();
    }
  };

  const handleClear = () => {
    setQuery('');
    clear();
  };

  return (
    <div className={`relative ${embedded ? 'w-60' : 'w-72'}`}>
      <div className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none">
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
          <circle cx="5" cy="5" r="3.5" stroke="#9ca3af" strokeWidth="1.3" />
          <path d="M8 8l2.5 2.5" stroke="#9ca3af" strokeWidth="1.3" strokeLinecap="round" />
        </svg>
      </div>

      <input
        type="text"
        value={query}
        onChange={e => handleChange(e.target.value)}
        placeholder="Поиск по НПА…"
        className={`w-full text-xs text-gray-700 pl-9 pr-9 py-0 h-full focus:outline-none placeholder-gray-400 transition-all duration-300 ${
          embedded
            ? 'bg-transparent border-0 focus:ring-0'
            : 'bg-white/80 backdrop-blur-sm border border-white/20 rounded-lg hover:border-gray-400 focus:border-gray-600 focus:ring-2 focus:ring-gray-100'
        }`}
      />

      {loading ? (
        <div className="absolute right-3 top-1/2 -translate-y-1/2 w-3 h-3 border border-gray-400 border-t-transparent rounded-full animate-spin" />
      ) : query ? (
        <button
          onClick={handleClear}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
        >
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
            <path d="M1 1l8 8M9 1L1 9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
          </svg>
        </button>
      ) : null}
    </div>
  );
}
