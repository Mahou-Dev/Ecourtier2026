import React, { useState } from 'react';
import { Building2 } from 'lucide-react';
import { cn } from '../../lib/cn';
import { getPhotoUrl } from '../../services/api';
import { PatternClaustra } from '../ui/PatternClaustra';
import {
  CATEGORY_LABELS_FR,
  MIN_VALID_REAL_PHOTO_HEIGHT,
  MIN_VALID_REAL_PHOTO_WIDTH,
  isUsablePhotoUrl,
  mapPropertyCategory,
  selectFallbackPhoto,
} from '../../lib/propertyMedia';

export type PropertyVisualMode = 'thumbnail' | 'card' | 'gallery';
export type PropertyVisualRatio = '16/10' | '4/3' | '16/9' | 'square' | 'auto';
export type PropertyVisualBadgePosition = 'bottom-right' | 'bottom-left' | 'top-left';

export interface PropertyVisualProps {
  rawUrl?: string | null;
  listingId: string;
  type?: string | null;
  title: string;
  quartier?: string;
  mode?: PropertyVisualMode;
  ratio?: PropertyVisualRatio;
  fallbackUrl?: string | null;
  slotKey?: string;
  showIllustrativeBadge?: boolean;
  illustrativeBadgeCompact?: boolean;
  badgePosition?: PropertyVisualBadgePosition;
  loading?: 'eager' | 'lazy';
  fetchPriority?: 'high' | 'low' | 'auto';
  width?: number;
  height?: number;
  className?: string;
  imageClassName?: string;
  onClick?: () => void;
  onStageChange?: (stage: 'real' | 'fallback' | 'placeholder', resolvedUrl: string | null) => void;
}

type VisualStage = 'real' | 'fallback' | 'placeholder';

const RATIO_CLASSES: Record<PropertyVisualRatio, string> = {
  '16/10': 'aspect-[16/10]',
  '4/3': 'aspect-[4/3]',
  '16/9': 'aspect-video',
  square: 'aspect-square',
  auto: '',
};

const DEFAULT_DIMENSIONS: Record<PropertyVisualMode, { width: number; height: number }> = {
  thumbnail: { width: 96, height: 96 },
  card: { width: 640, height: 400 },
  gallery: { width: 960, height: 600 },
};

const BADGE_POSITIONS: Record<PropertyVisualBadgePosition, string> = {
  'bottom-right': 'bottom-2 right-2',
  'bottom-left': 'bottom-2 left-2',
  'top-left': 'top-2 left-2',
};

interface PropertyVisualInnerProps extends PropertyVisualProps {
  resolvedRealUrl: string | null;
  effectiveFallbackUrl: string | null;
  categoryLabel: string;
}

const PropertyVisualInner: React.FC<PropertyVisualInnerProps> = ({
  title,
  quartier,
  mode = 'card',
  ratio = 'auto',
  showIllustrativeBadge = true,
  illustrativeBadgeCompact,
  badgePosition = 'bottom-right',
  loading = 'lazy',
  fetchPriority,
  width,
  height,
  className,
  imageClassName,
  onClick,
  onStageChange,
  resolvedRealUrl,
  effectiveFallbackUrl,
  categoryLabel,
}) => {
  const [stage, setStage] = useState<VisualStage>(() =>
    resolvedRealUrl ? 'real' : effectiveFallbackUrl ? 'fallback' : 'placeholder'
  );

  const dims = DEFAULT_DIMENSIONS[mode];
  const finalWidth = width ?? dims.width;
  const finalHeight = height ?? dims.height;
  const isCompactBadge = illustrativeBadgeCompact ?? mode === 'thumbnail';

  const transitionTo = (nextStage: VisualStage) => {
    setStage(nextStage);
    const nextUrl =
      nextStage === 'fallback'
        ? effectiveFallbackUrl
        : nextStage === 'real'
          ? resolvedRealUrl
          : null;
    onStageChange?.(nextStage, nextUrl);
  };

  const handleRealLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget;
    if (
      img.naturalWidth < MIN_VALID_REAL_PHOTO_WIDTH ||
      img.naturalHeight < MIN_VALID_REAL_PHOTO_HEIGHT
    ) {
      transitionTo(effectiveFallbackUrl ? 'fallback' : 'placeholder');
      return;
    }
    onStageChange?.('real', resolvedRealUrl);
  };

  const handleRealError = () => {
    transitionTo(effectiveFallbackUrl ? 'fallback' : 'placeholder');
  };

  const handleFallbackError = () => {
    transitionTo('placeholder');
  };

  const fetchPriorityAttr = fetchPriority
    ? ({ fetchpriority: fetchPriority } as React.ImgHTMLAttributes<HTMLImageElement>)
    : undefined;

  return (
    <div
      onClick={onClick}
      className={cn(
        'relative overflow-hidden bg-[#EAE2D3] select-none',
        RATIO_CLASSES[ratio],
        className
      )}
    >
      {stage === 'real' && resolvedRealUrl && (
        <img
          src={resolvedRealUrl}
          alt={title}
          loading={loading}
          {...fetchPriorityAttr}
          decoding="async"
          width={finalWidth}
          height={finalHeight}
          onLoad={handleRealLoad}
          onError={handleRealError}
          className={cn('w-full h-full object-cover object-center', imageClassName)}
        />
      )}

      {stage === 'fallback' && effectiveFallbackUrl && (
        <img
          src={effectiveFallbackUrl}
          alt={`${title} — ${categoryLabel} (image illustrative)`}
          loading={loading}
          {...fetchPriorityAttr}
          decoding="async"
          width={finalWidth}
          height={finalHeight}
          onLoad={() => onStageChange?.('fallback', effectiveFallbackUrl)}
          onError={handleFallbackError}
          className={cn('w-full h-full object-cover object-center', imageClassName)}
        />
      )}

      {stage === 'placeholder' && (
        <div
          className={cn(
            'relative w-full h-full flex flex-col items-center justify-center bg-[#EFE8DB] text-[#5B5244] text-center',
            mode === 'thumbnail' ? 'p-1.5' : 'px-4 py-3'
          )}
        >
          <PatternClaustra variant="terracotta" opacity={0.08} scale={mode === 'thumbnail' ? 0.7 : 1.1} />
          <Building2
            className={cn(
              'text-[#9D4A32]/75 relative z-10',
              mode === 'thumbnail' ? 'w-5 h-5' : mode === 'gallery' ? 'w-11 h-11 mb-2.5' : 'w-8 h-8 mb-1.5'
            )}
            aria-hidden="true"
          />
          {mode !== 'thumbnail' && (
            <>
              <span className="relative z-10 text-xs font-semibold text-[#252A29] line-clamp-1">
                {quartier ? `${categoryLabel} • ${quartier}` : categoryLabel}
              </span>
              <span className="relative z-10 text-[11px] text-[#4F5654] mt-0.5">
                Visuel non disponible
              </span>
            </>
          )}
        </div>
      )}

      {stage === 'fallback' && showIllustrativeBadge && (
        <span
          title="Image illustrative associée au type de bien"
          className={cn(
            'pointer-events-none absolute z-10 inline-flex items-center rounded-md bg-[#18211F]/80 backdrop-blur-sm text-[#F5F0E8] border border-white/15 font-medium',
            BADGE_POSITIONS[badgePosition],
            isCompactBadge ? 'px-1.5 py-0.5 text-[9px] leading-tight' : 'px-2 py-0.5 text-[10px]'
          )}
        >
          {isCompactBadge ? 'Illustratif' : 'Image illustrative'}
        </span>
      )}
    </div>
  );
};

/**
 * Shared 3-tier property visual renderer:
 * 1) Real API photograph (rejecting < 64x64 synthetic swatches & network failures),
 * 2) Categorized local Sahel/Senegal WebP fallback clearly marked "Image illustrative",
 * 3) Graphic architectural placeholder when no image asset is available.
 */
export const PropertyVisual: React.FC<PropertyVisualProps> = (props) => {
  const { rawUrl, listingId, type, title, fallbackUrl, slotKey } = props;
  const category = mapPropertyCategory(type, title);
  const categoryLabel = CATEGORY_LABELS_FR[category];
  const seedKey = slotKey ? `${listingId}:${slotKey}` : listingId;
  const effectiveFallbackUrl =
    fallbackUrl !== undefined ? fallbackUrl : selectFallbackPhoto(category, seedKey);

  const hasUsableRealUrl = isUsablePhotoUrl(rawUrl);
  const resolvedRealUrl = hasUsableRealUrl ? getPhotoUrl(rawUrl ?? undefined) : null;
  const visualKey = `${resolvedRealUrl ?? ''}::${effectiveFallbackUrl ?? ''}::${category}`;

  return (
    <PropertyVisualInner
      key={visualKey}
      {...props}
      resolvedRealUrl={resolvedRealUrl}
      effectiveFallbackUrl={effectiveFallbackUrl}
      categoryLabel={categoryLabel}
    />
  );
};
