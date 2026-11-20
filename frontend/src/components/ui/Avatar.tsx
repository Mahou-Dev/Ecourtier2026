import React, { useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { cn } from '../../lib/cn';

export interface AvatarProps {
  src?: string;
  name: string;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  /**
   * N'afficher que lorsqu'une vérification réelle et documentée existe (jamais déduit du rôle).
   */
  isVerified?: boolean;
  isOnline?: boolean;
  className?: string;
}

const MONOGRAM_TONES = [
  'bg-[#1C352E] text-[#F5F0E8]',
  'bg-[#B85C42] text-white',
  'bg-[#2C4C3B] text-[#F5F0E8]',
  'bg-[#6F4B18] text-[#FAF7F2]',
] as const;

function getInitials(rawName?: string): string {
  const cleaned = (rawName || '').trim();
  if (!cleaned) return 'EC';
  const parts = cleaned.split(/\s+/).filter(Boolean);
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return `${parts[0][0] || ''}${parts[parts.length - 1][0] || ''}`.toUpperCase();
}

function getToneClass(rawName?: string): string {
  const input = (rawName || '').trim();
  let hash = 0;
  for (let i = 0; i < input.length; i++) {
    hash = (hash * 31 + input.charCodeAt(i)) >>> 0;
  }
  return MONOGRAM_TONES[hash % MONOGRAM_TONES.length];
}

export const Avatar: React.FC<AvatarProps> = ({
  src,
  name,
  size = 'md',
  isVerified = false,
  isOnline = false,
  className,
}) => {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  const sizes = {
    sm: 'w-8 h-8 text-[11px]',
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

  const trimmedSrc = typeof src === 'string' ? src.trim() : '';
  const hasUsableImage = trimmedSrc.length > 0 && failedSrc !== trimmedSrc;
  const initials = getInitials(name);
  const toneClass = getToneClass(name);

  return (
    <div className={cn('relative inline-block shrink-0', className)}>
      {hasUsableImage ? (
        <img
          src={trimmedSrc}
          alt={name}
          onError={() => setFailedSrc(trimmedSrc)}
          className={cn(
            'rounded-full object-cover border-2 border-white shadow-sm bg-sand-200',
            sizes[size]
          )}
        />
      ) : (
        <div
          role="img"
          aria-label={name}
          className={cn(
            'rounded-full border-2 border-white shadow-sm flex items-center justify-center font-editorial font-extrabold tracking-tight select-none',
            toneClass,
            sizes[size]
          )}
        >
          {initials}
        </div>
      )}

      {isVerified === true && (
        <div
          title="Vérifié"
          aria-label="Profil vérifié"
          className="absolute -bottom-0.5 -right-0.5 bg-sahel-800 text-white p-0.5 rounded-full ring-2 ring-white"
        >
          <ShieldCheck className={cn(badgeSizes[size])} aria-hidden="true" />
        </div>
      )}

      {isOnline && isVerified !== true && (
        <span
          aria-label="En ligne"
          className="absolute bottom-0 right-0 block w-3 h-3 bg-emerald-500 rounded-full ring-2 ring-white"
        />
      )}
    </div>
  );
};
