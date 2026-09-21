import React from 'react';
import { cn } from '../../lib/cn';

export interface PatternClaustraProps extends React.SVGAttributes<SVGSVGElement> {
  variant?: 'terracotta' | 'sahel' | 'ocher' | 'sand' | 'charcoal';
  opacity?: number;
  scale?: number;
}

export const PatternClaustra: React.FC<PatternClaustraProps> = ({
  className,
  variant = 'terracotta',
  opacity = 0.15,
  scale = 1,
  ...props
}) => {
  const colorMap = {
    terracotta: '#C85A32',
    sahel: '#1D3E35',
    ocher: '#E09F3E',
    sand: '#DFCEAA',
    charcoal: '#1E232A',
  };

  const strokeColor = colorMap[variant];
  const patternId = `claustra-pattern-${variant}-${Math.random().toString(36).substring(2, 7)}`;

  return (
    <svg
      className={cn('pointer-events-none absolute inset-0 w-full h-full', className)}
      style={{ opacity }}
      xmlns="http://www.w3.org/2000/svg"
      {...props}
    >
      <defs>
        <pattern
          id={patternId}
          x="0"
          y="0"
          width={40 * scale}
          height={40 * scale}
          patternUnits="userSpaceOnUse"
        >
          {/* Outer Grid Diamond */}
          <path
            d="M 20 0 L 40 20 L 20 40 L 0 20 Z"
            fill="none"
            stroke={strokeColor}
            strokeWidth="1.2"
          />
          {/* Inner Inscribed Square */}
          <rect
            x="12"
            y="12"
            width="16"
            height="16"
            fill="none"
            stroke={strokeColor}
            strokeWidth="0.8"
            transform="rotate(45 20 20)"
          />
          {/* Center Point Motif */}
          <circle cx="20" cy="20" r="2" fill={strokeColor} />
          {/* Corner Cross Hair Accents */}
          <path
            d="M 0 20 L 6 20 M 34 20 L 40 20 M 20 0 L 20 6 M 20 34 L 20 40"
            stroke={strokeColor}
            strokeWidth="1"
          />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill={`url(#${patternId})`} />
    </svg>
  );
};
