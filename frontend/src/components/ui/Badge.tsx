import React from 'react';
import { cn } from '../../lib/cn';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'terracotta' | 'sahel' | 'ocher' | 'sand';
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  className,
  variant = 'terracotta',
  ...props
}) => {
  const variantStyles = {
    terracotta: 'bg-terracotta-100 text-terracotta-800 border-terracotta-200',
    sahel: 'bg-sahel-100 text-sahel-900 border-sahel-200',
    ocher: 'bg-ocher-100 text-ocher-900 border-ocher-200',
    sand: 'bg-sand-200 text-charcoal-800 border-sand-300',
  };

  return (
    <span
      className={cn(
        'inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold tracking-wide border',
        variantStyles[variant],
        className
      )}
      {...props}
    >
      {children}
    </span>
  );
};
