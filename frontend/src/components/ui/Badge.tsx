import React from 'react';
import { cn } from '../../lib/cn';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'terracotta' | 'sahel' | 'ocher' | 'sand' | 'outline' | 'success' | 'warning' | 'info' | 'rose' | 'neutral';
  size?: 'sm' | 'md' | 'lg';
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  className,
  variant = 'terracotta',
  size = 'md',
  ...props
}) => {
  const variantStyles = {
    terracotta: 'bg-terracotta-100 text-terracotta-800 border-terracotta-200',
    sahel: 'bg-sahel-100 text-sahel-900 border-sahel-200',
    ocher: 'bg-ocher-100 text-ocher-900 border-ocher-200',
    sand: 'bg-sand-200 text-charcoal-800 border-sand-300',
    outline: 'bg-white text-charcoal-700 border-argile-300',
    success: 'bg-emerald-50 text-emerald-800 border-emerald-200',
    warning: 'bg-amber-50 text-amber-800 border-amber-200',
    info: 'bg-sky-50 text-sky-800 border-sky-200',
    rose: 'bg-rose-50 text-rose-800 border-rose-200',
    neutral: 'bg-gray-100 text-gray-700 border-gray-200',
  };

  const sizeStyles = {
    sm: 'px-2 py-0.5 text-[10px]',
    md: 'px-3 py-1 text-xs',
    lg: 'px-4 py-1.5 text-sm',
  };

  return (
    <span
      className={cn(
        'inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold tracking-wide border',
        variantStyles[variant],
        sizeStyles[size],
        className
      )}
      {...props}
    >
      {children}
    </span>
  );
};
