import apt01 from '../assets/home/property-fallbacks/apartment/apartment-01.webp';
import apt02 from '../assets/home/property-fallbacks/apartment/apartment-02.webp';
import apt03 from '../assets/home/property-fallbacks/apartment/apartment-03.webp';
import villa01 from '../assets/home/property-fallbacks/villa/villa-01.webp';
import villa02 from '../assets/home/property-fallbacks/villa/villa-02.webp';
import villa03 from '../assets/home/property-fallbacks/villa/villa-03.webp';
import house01 from '../assets/home/property-fallbacks/house/house-01.webp';
import house02 from '../assets/home/property-fallbacks/house/house-02.webp';
import land01 from '../assets/home/property-fallbacks/land/land-01.webp';
import comm01 from '../assets/home/property-fallbacks/commercial/commercial-01.webp';
import default01 from '../assets/home/property-fallbacks/default/default-01.webp';
import default02 from '../assets/home/property-fallbacks/default/default-02.webp';

export type PropertyVisualCategory =
  | 'apartment'
  | 'villa'
  | 'house'
  | 'land'
  | 'commercial'
  | 'default';

/**
 * Technical floor rejecting synthetic 8x8 monochrome audit swatches.
 * Passing 64x64 is necessary to qualify for display, never sufficient proof of editorial photography quality.
 */
export const MIN_VALID_REAL_PHOTO_WIDTH = 64;
export const MIN_VALID_REAL_PHOTO_HEIGHT = 64;

export const PROPERTY_FALLBACK_IMAGES: Record<PropertyVisualCategory, readonly string[]> = {
  apartment: [apt01, apt02, apt03],
  villa: [villa01, villa02, villa03],
  house: [house01, house02],
  land: [land01],
  commercial: [comm01],
  default: [default01, default02],
};

export const CATEGORY_LABELS_FR: Record<PropertyVisualCategory, string> = {
  apartment: 'Appartement',
  villa: 'Villa',
  house: 'Maison',
  land: 'Terrain',
  commercial: 'Local commercial',
  default: 'Résidence',
};

function normalizeToken(value?: string | null): string {
  if (!value) return '';
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toUpperCase();
}

function fnv1aHash(input: string): number {
  let hash = 2166136261;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

/**
 * Deterministically maps backend property type & existing title to a local fallback category.
 * Uses only existing listing fields without mutating contracts or adding server metadata.
 */
export function mapPropertyCategory(
  type?: string | null,
  title?: string | null
): PropertyVisualCategory {
  const normType = normalizeToken(type);
  const normTitle = normalizeToken(title);

  if (
    normType === 'APPARTEMENT' ||
    normType === 'APARTMENT' ||
    normType === 'STUDIO' ||
    normType === 'CHAMBRE' ||
    normType === 'DUPLEX' ||
    normType === 'PENTHOUSE'
  ) {
    return 'apartment';
  }

  if (normType === 'TERRAIN' || normType === 'LAND' || normType === 'PARCELLE') {
    return 'land';
  }

  if (
    normType === 'COMMERCIAL' ||
    normType === 'LOCAL_COMMERCIAL' ||
    normType === 'BUREAU' ||
    normType === 'BOUTIQUE' ||
    normType === 'MAGASIN'
  ) {
    return 'commercial';
  }

  if (normType === 'MAISON' || normType === 'HOUSE' || normType === 'PAVILLON') {
    return 'house';
  }

  if (normType === 'VILLA') {
    if (normTitle.includes('MAISON') && !normTitle.includes('VILLA')) {
      return 'house';
    }
    return 'villa';
  }

  return 'default';
}

export function isUsablePhotoUrl(url?: string | null): boolean {
  if (!url || typeof url !== 'string') return false;
  const trimmed = url.trim();
  if (!trimmed) return false;
  if (trimmed === 'undefined' || trimmed === 'null') return false;
  return (
    trimmed.startsWith('http://') ||
    trimmed.startsWith('https://') ||
    trimmed.startsWith('/') ||
    trimmed.startsWith('data:image/') ||
    /^[a-zA-Z0-9._/-]+$/.test(trimmed)
  );
}

/**
 * Selects a deterministic illustrative photo URL for a listing when no valid real photograph is present.
 * Avoids duplicate neighboring visuals when multiple curated category images exist.
 */
export function selectFallbackPhoto(
  category: PropertyVisualCategory,
  listingId: string,
  usedUrls?: Set<string>,
  registry: Partial<Record<PropertyVisualCategory, readonly string[]>> = PROPERTY_FALLBACK_IMAGES
): string | null {
  const categoryPool = registry[category];
  const defaultPool = registry.default;
  const pool =
    categoryPool && categoryPool.length > 0
      ? categoryPool
      : defaultPool && defaultPool.length > 0
        ? defaultPool
        : null;

  if (!pool || pool.length === 0) return null;

  const startIdx = fnv1aHash(`${category}:${listingId}`) % pool.length;

  for (let offset = 0; offset < pool.length; offset++) {
    const candidate = pool[(startIdx + offset) % pool.length];
    if (candidate && (!usedUrls || !usedUrls.has(candidate))) {
      usedUrls?.add(candidate);
      return candidate;
    }
  }

  const fallback = pool[startIdx] ?? null;
  if (fallback) usedUrls?.add(fallback);
  return fallback;
}
