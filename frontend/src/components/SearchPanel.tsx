import { SearchBar } from './SearchBar';

interface SearchPanelProps {
  searchHits: string[];
  onSearchResults: (results: string[]) => void;
}

export function SearchPanel({ searchHits, onSearchResults }: SearchPanelProps) {
  return (
    <div className="absolute top-4 left-1/2 -translate-x-1/2 z-20">
      <div className="flex items-center h-12 rounded-lg bg-white/90 backdrop-blur-md border border-white/20 elevation-2">
        <SearchBar onResults={onSearchResults} embedded />
        {searchHits.length > 0 && (
          <span className="mr-4 px-3 py-1.5 text-[10px] font-bold rounded-full bg-gradient-to-r from-gray-700 to-gray-800 text-white tabular-nums elevation-1">
            {searchHits.length}
          </span>
        )}
      </div>
    </div>
  );
}
