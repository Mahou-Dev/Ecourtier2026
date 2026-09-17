import React from 'react';
import { cn } from '../../lib/cn';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'text' | 'card' | 'avatar' | 'rect';
}

export const Skeleton: React.FC<SkeletonProps> = ({
  className,
  variant = 'rect',
  ...props
}) => {
  const variants = {
    text: 'h-4 w-3/4 rounded-md',
    card: 'h-64 w-full rounded-2xl',
    avatar: 'h-12 w-12 rounded-full',
    rect: 'rounded-xl',
  };

  return (
    <div
      className={cn(
        'bg-gradient-to-r from-sand-200 via-sand-300 to-sand-200 animate-pulse',
        variants[variant],
        className
      )}
      {...props}
    />
  );
};
