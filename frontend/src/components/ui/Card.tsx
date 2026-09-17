import React from 'react';
import { cn } from '../../lib/cn';

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'sahel' | 'terracotta-accent';
}

export const Card: React.FC<CardProps> = ({
  children,
  className,
  variant = 'default',
  ...props
}) => {
  const variantStyles = {
    default: 'bg-white border border-argile-200 shadow-warm-card',
    sahel: 'bg-sahel-800 text-white border border-sahel-700 shadow-sahel-glow',
    'terracotta-accent': 'bg-sand-100 border border-terracotta-200 shadow-terracotta-glow',
  };

  return (
    <div
      className={cn(
        'rounded-2xl transition-all duration-300 overflow-hidden',
        variantStyles[variant],
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
};

export const CardHeader: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({
  children,
  className,
  ...props
}) => (
  <div className={cn('p-5 sm:p-6 border-b border-argile-200/60', className)} {...props}>
    {children}
  </div>
);

export const CardBody: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({
  children,
  className,
  ...props
}) => (
  <div className={cn('p-5 sm:p-6', className)} {...props}>
    {children}
  </div>
);

export const CardFooter: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({
  children,
  className,
  ...props
}) => (
  <div className={cn('p-5 sm:p-6 bg-sand-50/50 border-t border-argile-200/60', className)} {...props}>
    {children}
  </div>
);
