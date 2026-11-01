import React, { useState, useRef, useEffect } from 'react';
import { MapPin, X, Check } from 'lucide-react';
import { useQuartiers } from '../../hooks/useReferences';

interface QuartierAutocompleteProps {
  value: string;
  onChange: (val: string) => void;
  placeholder?: string;
  className?: string;
}

export const QuartierAutocomplete: React.FC<QuartierAutocompleteProps> = ({
  value,
  onChange,
  placeholder = 'Rechercher un quartier (ex: Almadies, Mermoz)...',
  className = '',
}) => {
  const { data: quartiers = [] } = useQuartiers();
  const [open, setOpen] = useState(false);
  const [inputValue, setInputValue] = useState(value);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setInputValue(value);
  }, [value]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const filteredQuartiers = quartiers.filter((q) =>
    q.toLowerCase().includes(inputValue.toLowerCase())
  );

  return (
    <div ref={containerRef} className={`relative w-full ${className}`}>
      <div className="relative flex items-center">
        <MapPin className="absolute left-3 w-4 h-4 text-terracotta-500 pointer-events-none" />
        <input
          type="text"
          value={inputValue}
          onChange={(e) => {
            setInputValue(e.target.value);
            onChange(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          placeholder={placeholder}
          className="w-full pl-9 pr-8 py-2 text-xs rounded-xl border border-sand-300 bg-white focus:outline-none focus:ring-2 focus:ring-terracotta-500"
        />
        {inputValue && (
          <button
            type="button"
            onClick={() => {
              setInputValue('');
              onChange('');
              setOpen(false);
            }}
            className="absolute right-2.5 p-1 text-charcoal-400 hover:text-charcoal-600 rounded-full"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {open && filteredQuartiers.length > 0 && (
        <div className="absolute z-50 left-0 right-0 mt-1 max-h-48 overflow-y-auto bg-white rounded-xl shadow-lg border border-sand-300 py-1 text-xs">
          {filteredQuartiers.map((q) => {
            const isSelected = value.toLowerCase() === q.toLowerCase();
            return (
              <button
                key={q}
                type="button"
                onClick={() => {
                  setInputValue(q);
                  onChange(q);
                  setOpen(false);
                }}
                className={`w-full flex items-center justify-between px-3 py-2 text-left hover:bg-sand-100 transition-colors ${
                  isSelected ? 'bg-terracotta-50 text-terracotta-700 font-semibold' : 'text-charcoal-800'
                }`}
              >
                <span>{q}</span>
                {isSelected && <Check className="w-3.5 h-3.5 text-terracotta-600" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
};
