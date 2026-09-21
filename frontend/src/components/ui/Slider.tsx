import React from 'react';
import * as RadixSlider from '@radix-ui/react-slider';
import { cn } from '../../lib/cn';

export interface SliderProps {
  min: number;
  max: number;
  step?: number;
  value: number;
  onChange: (value: number) => void;
  label?: string;
  formatValue?: (val: number) => string;
  className?: string;
}

export const Slider: React.FC<SliderProps> = ({
  min,
  max,
  step = 1,
  value,
  onChange,
  label,
  formatValue = (v) => v.toString(),
  className,
}) => {
  return (
    <div className={cn('w-full space-y-2', className)}>
      <div className="flex items-center justify-between text-xs font-semibold text-charcoal-800">
        {label && <span>{label}</span>}
        <span className="text-terracotta-600 font-bold bg-terracotta-50 px-2 py-0.5 rounded border border-terracotta-100">
          {formatValue(value)}
        </span>
      </div>

      <RadixSlider.Root
        className="relative flex items-center select-none touch-none w-full h-5 cursor-pointer"
        value={[value]}
        onValueChange={(vals) => onChange(vals[0])}
        min={min}
        max={max}
        step={step}
      >
        <RadixSlider.Track className="bg-sand-200 relative grow rounded-full h-2">
          <RadixSlider.Range className="absolute bg-terracotta-500 rounded-full h-full" />
        </RadixSlider.Track>
        <RadixSlider.Thumb
          className="block w-5 h-5 bg-white border-2 border-terracotta-500 rounded-full shadow-md hover:scale-110 focus:outline-none focus:ring-2 focus:ring-terracotta-500/50 transition-transform"
          aria-label={label || 'Slider'}
        />
      </RadixSlider.Root>

      <div className="flex justify-between text-[11px] text-charcoal-400 font-mono">
        <span>{formatValue(min)}</span>
        <span>{formatValue(max)}</span>
      </div>
    </div>
  );
};
