import React from 'react';
import * as RadixSelect from '@radix-ui/react-select';
import { ChevronDown, Check } from 'lucide-react';
import { cn } from '../../lib/cn';

export interface SelectOption {
  value: string;
  label: string;
  icon?: React.ReactNode;
}

export interface SelectProps {
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  label?: string;
  placeholder?: string;
  className?: string;
  error?: string;
}

export const Select: React.FC<SelectProps> = ({
  options,
  value,
  onChange,
  label,
  placeholder = 'Sélectionner...',
  className,
  error,
}) => {
  return (
    <div className="w-full space-y-1.5">
      {label && <label className="block text-xs font-semibold text-charcoal-800 tracking-wide">{label}</label>}
      <RadixSelect.Root value={value} onValueChange={onChange}>
        <RadixSelect.Trigger
          className={cn(
            'w-full bg-white text-charcoal-900 text-sm rounded-xl border border-argile-300 px-4 py-3 font-body flex items-center justify-between transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-terracotta-500/30 focus:border-terracotta-500',
            error && 'border-red-500',
            className
          )}
        >
          <RadixSelect.Value placeholder={placeholder} />
          <RadixSelect.Icon asChild>
            <ChevronDown className="w-4 h-4 text-charcoal-400" />
          </RadixSelect.Icon>
        </RadixSelect.Trigger>

        <RadixSelect.Portal>
          <RadixSelect.Content
            className="z-50 bg-white border border-argile-200 rounded-xl shadow-warm-hover py-1 max-h-60 overflow-y-auto animate-in fade-in zoom-in-95"
            position="popper"
            sideOffset={4}
          >
            <RadixSelect.Viewport className="p-1">
              {options.map((option) => (
                <RadixSelect.Item
                  key={option.value}
                  value={option.value}
                  className="w-full px-4 py-2.5 text-xs font-medium text-charcoal-700 rounded-lg flex items-center justify-between cursor-pointer focus:bg-terracotta-50 focus:text-terracotta-700 focus:outline-none data-[state=checked]:font-semibold data-[state=checked]:text-terracotta-700"
                >
                  <div className="flex items-center gap-2 truncate">
                    {option.icon}
                    <RadixSelect.ItemText>{option.label}</RadixSelect.ItemText>
                  </div>
                  <RadixSelect.ItemIndicator>
                    <Check className="w-4 h-4 text-terracotta-600" />
                  </RadixSelect.ItemIndicator>
                </RadixSelect.Item>
              ))}
            </RadixSelect.Viewport>
          </RadixSelect.Content>
        </RadixSelect.Portal>
      </RadixSelect.Root>
      {error && <p className="text-xs text-red-600 font-medium">{error}</p>}
    </div>
  );
};
