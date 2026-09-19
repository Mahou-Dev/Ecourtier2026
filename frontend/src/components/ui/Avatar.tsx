import React from 'react';
import { ShieldCheck } from 'lucide-react';
import { cn } from '../../lib/cn';

export interface AvatarProps {
  src?: string;
  name: string;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  isVerified?: boolean;
  isOnline?: boolean;
  className?: string;
}

export const Avatar: React.FC<AvatarProps> = ({
  src,
  name,
  size = 'md',
  isVerified = false,
  isOnline = false,
  className,
}) => {
  const sizes = {
    sm: 'w-8 h-8 text-xs',
    md: 'w-11 h-11 text-sm',
    lg: 'w-14 h-14 text-base',
    xl: 'w-20 h-20 text-xl',
  };

  const badgeSizes = {
    sm: 'w-3 h-3',
    md: 'w-4 h-4',
    lg: 'w-5 h-5',
    xl: 'w-6 h-6',
  };

  const avatarSrc = src || `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=334739&color=ffffff`;

  return (
    <div className={cn('relative inline-block shrink-0', className)}>
      <img
        src={avatarSrc}
        alt={name}
        className={cn('rounded-full object-cover border-2 border-white shadow-sm', sizes[size])}
      />
      {isVerified && (
        <div className="absolute -bottom-0.5 -right-0.5 bg-sahel-800 text-white p-0.5 rounded-full ring-2 ring-white">
          <ShieldCheck className={cn(badgeSizes[size])} />
        </div>
      )}
      {isOnline && !isVerified && (
        <span className="absolute bottom-0 right-0 block w-3 h-3 bg-emerald-500 rounded-full ring-2 ring-white" />
      )}
    </div>
  );
};
