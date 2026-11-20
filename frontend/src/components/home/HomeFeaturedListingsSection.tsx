import React, { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  ArrowRight,
  Building2,
  DoorOpen,
  MapPin,
  Maximize2,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { apiFetch, getPhotoUrl } from '../../services/api';
import { AnnonceDTO, PageResponse } from '../../types';
import { FavoriteButton } from '../ui/FavoriteButton';
import { PatternClaustra } from '../ui/PatternClaustra';
import {
  CATEGORY_LABELS_FR,
  MIN_VALID_REAL_PHOTO_HEIGHT,
  MIN_VALID_REAL_PHOTO_WIDTH,
  PropertyVisualCategory,
  isUsablePhotoUrl,
  mapPropertyCategory,
  selectFallbackPhoto,
} from './propertyMedia';

interface ResolvedPropertyCardItem {
  prop: AnnonceDTO;
  category: PropertyVisualCategory;
  fallbackUrl: string | null;
}

export interface HomePropertyCardProps {
  prop: AnnonceDTO;
  category?: PropertyVisualCategory;
  fallbackUrl?: string | null;
}

type VisualStage = 'real' | 'fallback' | 'placeholder';

export const HomePropertyCard: React.FC<HomePropertyCardProps> = ({
  prop,
  category: providedCategory,
  fallbackUrl: providedFallbackUrl,
}) => {
  const category = providedCategory ?? mapPropertyCategory(prop.type, prop.titre);
  const fallbackUrl =
    providedFallbackUrl !== undefined
      ? providedFallbackUrl
      : selectFallbackPhoto(category, prop.id);

  const rawPhotoUrl = prop.photos?.[0]?.url;
  const hasUsableRealUrl = isUsablePhotoUrl(rawPhotoUrl);
  const resolvedRealUrl = hasUsableRealUrl ? getPhotoUrl(rawPhotoUrl) : null;

  const [stage, setStage] = useState<VisualStage>(() =>
    resolvedRealUrl ? 'real' : fallbackUrl ? 'fallback' : 'placeholder'
  );

  const isAgent = prop.auteurRole === 'AGENT' || prop.auteur?.role === 'AGENT';
  const categoryLabel = CATEGORY_LABELS_FR[category];

  const handleRealLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget;
    if (
      img.naturalWidth < MIN_VALID_REAL_PHOTO_WIDTH ||
      img.naturalHeight < MIN_VALID_REAL_PHOTO_HEIGHT
    ) {
      setStage(fallbackUrl ? 'fallback' : 'placeholder');
    }
  };

  const handleRealError = () => {
    setStage(fallbackUrl ? 'fallback' : 'placeholder');
  };

  const handleFallbackError = () => {
    setStage('placeholder');
  };

  return (
    <article className="group bg-white rounded-2xl border border-[#E2DACB] hover:border-[#B85C42]/55 shadow-[0_6px_24px_-8px_rgba(37,42,41,0.08)] hover:shadow-[0_18px_36px_-10px_rgba(37,42,41,0.16)] hover:-translate-y-1 transition-all duration-300 flex flex-col h-full overflow-hidden">
      {/* 1. Photo / Category Fallback / Graphic Placeholder (Uniform 16:10) */}
      <div className="relative aspect-[16/10] bg-[#EAE2D3] overflow-hidden border-b border-[#E2DACB] shrink-0">
        {stage === 'real' && resolvedRealUrl && (
          <img
            src={resolvedRealUrl}
            alt={prop.titre}
            loading="lazy"
            decoding="async"
            width={640}
            height={400}
            onLoad={handleRealLoad}
            onError={handleRealError}
            className="w-full h-full object-cover object-center transition-transform duration-500 group-hover:scale-[1.03]"
          />
        )}

        {stage === 'fallback' && fallbackUrl && (
          <img
            src={fallbackUrl}
            alt={`${prop.titre} — ${categoryLabel} (image illustrative)`}
            loading="lazy"
            decoding="async"
            width={640}
            height={400}
            onError={handleFallbackError}
            className="w-full h-full object-cover object-center transition-transform duration-500 group-hover:scale-[1.03]"
          />
        )}

        {stage === 'placeholder' && (
          <div className="relative w-full h-full flex flex-col items-center justify-center bg-[#EFE8DB] text-[#5B5244] px-4 text-center">
            <PatternClaustra variant="terracotta" opacity={0.07} scale={1.1} />
            <Building2 className="w-9 h-9 text-[#9D4A32]/75 relative z-10 mb-2" aria-hidden="true" />
            <span className="relative z-10 text-xs font-semibold text-[#252A29]">
              {categoryLabel} &bull; {prop.quartier}
            </span>
            <span className="relative z-10 text-[11px] text-[#4F5654] mt-0.5">
              Visuel non disponible
            </span>
          </div>
        )}

        {/* Subtle bottom scrim for clear readability over sunlit visuals */}
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-[#141A18]/55 via-[#141A18]/15 to-transparent" />

        {/* 3. Transaction Mode (top-left) + Favorite Button (top-right) */}
        <div className="absolute top-3 left-3 right-3 flex items-start justify-between gap-2 z-10">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="inline-flex items-center px-2.5 py-1 rounded-full bg-white/95 backdrop-blur-sm text-[#252A29] text-[10px] font-bold uppercase tracking-wider border border-[#E2DACB] shadow-sm">
              {prop.modeTransaction === 'VENTE' ? 'Vente' : 'Location'}
            </span>
            <span className="inline-flex items-center px-2 py-1 rounded-full bg-[#1C352E]/90 backdrop-blur-sm text-[#F5F0E8] text-[10px] font-medium border border-white/15">
              {categoryLabel}
            </span>
          </div>

          <FavoriteButton annonceId={prop.id} estFavori={Boolean(prop.estFavori)} size="sm" />
        </div>

        {/* 2. Prominent Price + Discreet Illustrative Notice */}
        <div className="absolute bottom-3 left-3 right-3 flex items-end justify-between gap-2 z-10">
          <span className="inline-flex items-baseline gap-1 px-3 py-1.5 rounded-lg bg-[#18211F]/95 backdrop-blur-sm text-white text-sm sm:text-[15px] font-extrabold tracking-tight shadow-sm border border-white/10">
            <span>{prop.prix.toLocaleString('fr-FR')} FCFA</span>
            {prop.modeTransaction === 'LOCATION' && (
              <span className="text-[11px] font-normal text-[#E5DEC9]">/ mois</span>
            )}
          </span>

          {stage === 'fallback' && (
            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-[#18211F]/85 backdrop-blur-sm text-[#F5F0E8] text-[10px] font-medium border border-white/15">
              Image illustrative
            </span>
          )}
        </div>
      </div>

      {/* Card Body with strict hierarchy */}
      <div className="p-5 flex-1 flex flex-col justify-between gap-4">
        <div className="space-y-2.5">
          {/* 4. Localisation */}
          <div className="flex items-center justify-between gap-2 text-xs font-medium text-[#4F5654]">
            <span className="inline-flex items-center gap-1.5 truncate">
              <MapPin className="w-3.5 h-3.5 text-[#B85C42] shrink-0" aria-hidden="true" />
              <span className="truncate">
                {prop.quartier}, {prop.ville}
              </span>
            </span>
            {prop.meuble && (
              <span className="shrink-0 px-2 py-0.5 rounded bg-[#B85C42]/10 text-[#9D4A32] text-[10px] font-bold">
                Meublé
              </span>
            )}
          </div>

          {/* 5. Title (uniform min height across cards) */}
          <h3 className="font-editorial font-bold text-base sm:text-[17px] text-[#252A29] line-clamp-2 min-h-[2.85rem] leading-snug group-hover:text-[#B85C42] transition-colors">
            <Link
              to={`/annonce/${prop.id}`}
              className="focus-visible:outline-none focus-visible:underline"
            >
              {prop.titre}
            </Link>
          </h3>

          {/* 6. Caractéristiques */}
          <div className="flex flex-wrap items-center gap-2 pt-0.5 text-xs text-[#3F4745]">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-[#F5F0E8] font-medium">
              <Maximize2 className="w-3 h-3 text-[#7A6F5E]" aria-hidden="true" />
              {prop.superficie} m²
            </span>
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-[#F5F0E8] font-medium">
              <DoorOpen className="w-3 h-3 text-[#7A6F5E]" aria-hidden="true" />
              {prop.nombrePieces} pièces
            </span>
          </div>
        </div>

        {/* 7. Statut de vérification & 8. Bouton de consultation */}
        <div className="mt-auto pt-3.5 border-t border-[#EFE8DC] flex items-center justify-between gap-2 flex-wrap">
          <div className="min-w-0 flex flex-col gap-0.5">
            {prop.statutVerification === 'VERIFIEE' && (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-[#1C352E]">
                <ShieldCheck className="w-3.5 h-3.5 text-[#1C352E] shrink-0" aria-hidden="true" />
                <span>Contrôle cadastral vérifié</span>
              </span>
            )}
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className="text-xs font-medium text-[#4F5654] truncate">
                {prop.auteur?.prenom} {prop.auteur?.nom}
              </span>
              {isAgent && (
                <span className="px-1.5 py-0.5 rounded bg-[#D5AD72]/25 text-[#6D4C1B] text-[10px] font-semibold">
                  Agent agréé
                </span>
              )}
            </div>
          </div>

          <Link
            to={`/annonce/${prop.id}`}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-full border border-[#B85C42] text-[#B85C42] hover:bg-[#B85C42] hover:text-white text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] focus-visible:ring-offset-1 shrink-0"
          >
            <span>Consulter</span>
            <ArrowRight className="w-3.5 h-3.5" aria-hidden="true" />
          </Link>
        </div>
      </div>
    </article>
  );
};

export const HomeFeaturedListingsSection: React.FC = () => {
  const {
    data: featuredData,
    isLoading,
    isError,
    refetch,
  } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: ['featured-annonces'],
    queryFn: () =>
      apiFetch<PageResponse<AnnonceDTO>>(
        '/api/v1/annonces?page=0&size=12&sort=dateCreation,desc&statutVerification=VERIFIEE&statutPublication=DISPONIBLE'
      ),
  });

  // Deduplicate exact repeated seed/test fixtures and assign non-repeating deterministic fallbacks
  const resolvedCards = useMemo<ResolvedPropertyCardItem[]>(() => {
    const raw = featuredData?.content || [];
    const seenSignatures = new Set<string>();
    const unique: AnnonceDTO[] = [];

    for (const item of raw) {
      const signature = `${item.titre.trim().toLowerCase()}|${item.ville.toLowerCase()}|${item.quartier.toLowerCase()}|${item.prix}`;
      if (!seenSignatures.has(signature)) {
        seenSignatures.add(signature);
        unique.push(item);
      }
      if (unique.length >= 6) break;
    }

    const usedFallbacks = new Set<string>();
    return unique.map((prop) => {
      const category = mapPropertyCategory(prop.type, prop.titre);
      const fallbackUrl = selectFallbackPhoto(category, prop.id, usedFallbacks);
      return { prop, category, fallbackUrl };
    });
  }, [featuredData]);

  return (
    <section
      id="annonces-verifiees"
      aria-labelledby="featured-listings-heading"
      className="max-w-7xl mx-auto px-4 xs:px-6 lg:px-10 space-y-8 scroll-mt-24"
    >
      {/* Editorial Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 border-b border-[#E2DACB] pb-5">
        <div className="space-y-2 max-w-2xl">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#B85C42]/10 border border-[#B85C42]/20 text-[#9D4A32] text-xs font-semibold">
            <ShieldCheck className="w-3.5 h-3.5" aria-hidden="true" />
            <span>Catalogue vérifié</span>
          </div>
          <h2
            id="featured-listings-heading"
            className="font-editorial text-2xl sm:text-3xl lg:text-4xl font-extrabold text-[#252A29] tracking-tight"
          >
            Biens vérifiés disponibles
          </h2>
          <p className="text-sm text-[#3F4745]">
            Sélection issue en direct des annonces disponibles ayant passé le contrôle documentaire
            E-Courtier.
          </p>
        </div>

        <Link
          to="/recherche?statutVerification=VERIFIEE"
          className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#B85C42] hover:text-[#9D4A32] transition-colors shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] rounded-lg px-2 py-1"
        >
          <span>Voir tout le catalogue</span>
          <ArrowRight className="w-4 h-4" aria-hidden="true" />
        </Link>
      </div>

      {/* Loading State */}
      {isLoading && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6" aria-busy="true">
          {[1, 2, 3].map((idx) => (
            <div
              key={idx}
              className="rounded-2xl bg-white border border-[#E2DACB] overflow-hidden shadow-xs"
            >
              <div className="aspect-[16/10] animate-shimmer" />
              <div className="p-5 space-y-3">
                <div className="h-3 w-28 rounded animate-shimmer" />
                <div className="h-5 w-3/4 rounded animate-shimmer" />
                <div className="h-8 w-full rounded animate-shimmer" />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Error State */}
      {!isLoading && isError && (
        <div className="rounded-2xl bg-white border border-[#E2DACB] p-8 sm:p-10 text-center space-y-4">
          <AlertCircle className="w-10 h-10 text-[#B85C42] mx-auto" aria-hidden="true" />
          <div className="space-y-1">
            <h3 className="font-editorial font-bold text-lg text-[#252A29]">
              Impossible de charger les annonces pour le moment
            </h3>
            <p className="text-sm text-[#4F5654] max-w-md mx-auto">
              La connexion au catalogue en temps réel n&rsquo;a pas répondu. Vous pouvez relancer le
              chargement ou ouvrir la recherche complète.
            </p>
          </div>
          <div className="flex flex-wrap items-center justify-center gap-3">
            <button
              type="button"
              onClick={() => refetch()}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-[#B85C42] hover:bg-[#9D4A32] text-white text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] focus-visible:ring-offset-2"
            >
              <RefreshCw className="w-4 h-4" aria-hidden="true" />
              <span>Actualiser</span>
            </button>
            <Link
              to="/recherche"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full border border-[#D8CEBC] text-[#252A29] hover:bg-[#F5F0E8] text-sm font-semibold transition-colors"
            >
              <span>Aller à la recherche</span>
            </Link>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && !isError && resolvedCards.length === 0 && (
        <div className="rounded-2xl bg-white border border-[#E2DACB] p-8 sm:p-12 text-center space-y-4">
          <Building2 className="w-10 h-10 text-[#8B7D69] mx-auto" aria-hidden="true" />
          <div className="space-y-1">
            <h3 className="font-editorial font-bold text-lg text-[#252A29]">
              Aucune annonce vérifiée disponible à cet instant
            </h3>
            <p className="text-sm text-[#4F5654] max-w-md mx-auto">
              Consultez l&rsquo;ensemble du catalogue publié sur la carte interactive.
            </p>
          </div>
          <Link
            to="/recherche"
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-[#B85C42] hover:bg-[#9D4A32] text-white text-sm font-semibold transition-colors"
          >
            <span>Explorer le catalogue complet</span>
            <ArrowRight className="w-4 h-4" aria-hidden="true" />
          </Link>
        </div>
      )}

      {/* Real Listings Grid */}
      {!isLoading && !isError && resolvedCards.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {resolvedCards.map(({ prop, category, fallbackUrl }) => (
            <HomePropertyCard
              key={`${prop.id}-${fallbackUrl ?? 'none'}`}
              prop={prop}
              category={category}
              fallbackUrl={fallbackUrl}
            />
          ))}
        </div>
      )}
    </section>
  );
};
