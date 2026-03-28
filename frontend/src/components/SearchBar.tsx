import { useState } from 'react';
import { Search, X } from 'lucide-react';
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
    <div className={`relative flex items-center ${embedded ? 'flex-1' : 'w-72'}`}>
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />

      <input
        type="text"
        value={query}
        onChange={e => handleChange(e.target.value)}
        placeholder="Поиск по НПА…"
        className={`w-full text-sm text-foreground pl-10 pr-9 focus:outline-none placeholder:text-muted-foreground transition-colors bg-transparent ${
          embedded
            ? 'h-9 border-0'
            : 'h-9 rounded-md border border-border bg-input focus:ring-1 focus:ring-ring'
        }`}
      />

      {loading ? (
        <div className="absolute right-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 border border-border border-t-primary rounded-full animate-spin" />
      ) : query ? (
        <button
          onClick={handleClear}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      ) : null}
    </div>
  );
}
