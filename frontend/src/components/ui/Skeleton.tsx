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
        'animate-shimmer border border-argile-200/50',
        variants[variant],
        className
      )}
      {...props}
    />
  );
};
