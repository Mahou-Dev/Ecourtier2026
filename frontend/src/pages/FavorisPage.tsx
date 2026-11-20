import React, { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Heart,
  MapPin,
  ShieldCheck,
  Search,
  ChevronLeft,
  ChevronRight,
  SlidersHorizontal,
  Building2,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../services/api';
import { AnnonceDTO, PageResponse } from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { Button } from '../components/ui/Button';
import { Badge, BadgeProps } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { FavoriteButton } from '../components/ui/FavoriteButton';
import { PropertyVisual } from '../components/property/PropertyVisual';
import {
  CATEGORY_LABELS_FR,
  mapPropertyCategory,
  selectFallbackPhoto,
} from '../lib/propertyMedia';

type QuickFilter = 'TOUS' | 'DISPONIBLE' | 'LOCATION' | 'VENTE';

function getPublicationBadgeMeta(
  statut?: AnnonceDTO['statutPublication']
): { label: string; variant: NonNullable<BadgeProps['variant']> } | null {
  switch (statut) {
    case 'RESERVEE':
      return { label: 'Réservée', variant: 'warning' };
    case 'LOUEE':
      return { label: 'Louée', variant: 'neutral' };
    case 'VENDUE':
      return { label: 'Vendue', variant: 'neutral' };
    case 'SUSPENDUE':
      return { label: 'Suspendue', variant: 'rose' };
    default:
      return null;
  }
}

export const FavorisPage: React.FC = () => {
  const { utilisateur } = useAuthStore();
  const [page, setPage] = useState<number>(0);
  const [quickFilter, setQuickFilter] = useState<QuickFilter>('TOUS');

  const { data, isLoading, isError, refetch } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: ['favoris', page],
    queryFn: () =>
      apiFetch<PageResponse<AnnonceDTO>>(
        `/api/v1/utilisateurs/moi/favoris?page=${page}&size=12`
      ),
    enabled: !!utilisateur,
  });

  const favoris = useMemo(() => data?.content || [], [data?.content]);

  const filterCounts = useMemo(() => {
    return {
      TOUS: favoris.length,
      DISPONIBLE: favoris.filter((p) => p.statutPublication === 'DISPONIBLE').length,
      LOCATION: favoris.filter((p) => p.modeTransaction === 'LOCATION').length,
      VENTE: favoris.filter((p) => p.modeTransaction === 'VENTE').length,
    };
  }, [favoris]);

  const filteredFavoris = useMemo(() => {
    switch (quickFilter) {
      case 'DISPONIBLE':
        return favoris.filter((p) => p.statutPublication === 'DISPONIBLE');
      case 'LOCATION':
        return favoris.filter((p) => p.modeTransaction === 'LOCATION');
      case 'VENTE':
        return favoris.filter((p) => p.modeTransaction === 'VENTE');
      default:
        return favoris;
    }
  }, [favoris, quickFilter]);

  if (!utilisateur) return null;

  const totalSaved = data?.totalElements ?? favoris.length;
  const usedFallbacks = new Set<string>();

  const handleFavoriteToggled = (newIsFavorite: boolean, annonceId: string) => {
    const remainingOnPage = favoris.filter((item) => item.id !== annonceId).length;
    if (!newIsFavorite && remainingOnPage === 0 && page > 0) {
      setPage((prev) => Math.max(0, prev - 1));
    }
  };

  const quickFilterOptions: Array<{ id: QuickFilter; label: string; count: number }> = [
    { id: 'TOUS', label: 'Tous', count: filterCounts.TOUS },
    { id: 'DISPONIBLE', label: 'Disponibles uniquement', count: filterCounts.DISPONIBLE },
    { id: 'LOCATION', label: 'Location', count: filterCounts.LOCATION },
    { id: 'VENTE', label: 'Vente', count: filterCounts.VENTE },
  ];

  return (
    <div className="max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-6 sm:py-8 pb-24 sm:pb-16 space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3.5 min-w-0">
          <div className="w-12 h-12 rounded-2xl bg-terracotta-500 flex items-center justify-center shadow-terracotta-glow shrink-0">
            <Heart className="w-6 h-6 text-white fill-current" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <h1 className="font-editorial font-extrabold text-2xl md:text-3xl text-charcoal-900 tracking-tight">
              Mes Favoris
            </h1>
            <p className="text-xs sm:text-sm text-charcoal-600">
              {isLoading
                ? 'Chargement de vos favoris...'
                : `${totalSaved} annonce${totalSaved > 1 ? 's' : ''} sauvegardée${totalSaved > 1 ? 's' : ''}`}
            </p>
          </div>
        </div>

        <Link
          to="/recherche"
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 min-h-[44px] rounded-full border-2 border-terracotta-500 text-terracotta-600 hover:bg-terracotta-50 font-display font-semibold text-xs sm:text-sm transition-colors shrink-0 self-start sm:self-center"
        >
          <Search className="w-4 h-4 shrink-0" aria-hidden="true" />
          <span>Explorer le catalogue</span>
        </Link>
      </div>

      {/* Quick Filter Bar (on loaded favorites) */}
      {!isLoading && !isError && favoris.length > 0 && (
        <div className="bg-white rounded-2xl border border-argile-200 p-3 sm:p-4 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-xs font-bold text-charcoal-700 shrink-0">
            <SlidersHorizontal className="w-4 h-4 text-terracotta-600" aria-hidden="true" />
            <span>Affiner cette sélection :</span>
          </div>

          <div
            role="group"
            aria-label="Filtres rapides des favoris"
            className="flex flex-wrap items-center gap-2"
          >
            {quickFilterOptions.map((opt) => {
              const active = quickFilter === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => setQuickFilter(opt.id)}
                  aria-pressed={active}
                  className={`inline-flex items-center gap-1.5 px-3.5 py-2 min-h-[44px] sm:min-h-[38px] rounded-xl text-xs font-semibold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-500 ${
                    active
                      ? 'bg-terracotta-500 text-white shadow-xs'
                      : 'bg-sand-100 text-charcoal-700 hover:bg-sand-200 border border-argile-200'
                  }`}
                >
                  <span>{opt.label}</span>
                  <span
                    className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
                      active ? 'bg-white/20 text-white' : 'bg-white text-charcoal-700'
                    }`}
                  >
                    {opt.count}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Content */}
      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} className="bg-white rounded-2xl border border-argile-200 overflow-hidden">
              <Skeleton variant="rect" className="w-full h-48" />
              <div className="p-4 space-y-3">
                <Skeleton variant="text" className="w-3/4" />
                <Skeleton variant="text" className="w-1/2" />
                <Skeleton variant="text" className="w-full" />
              </div>
            </div>
          ))}
        </div>
      ) : isError ? (
        <div className="p-8 text-center space-y-4 bg-white rounded-2xl border border-argile-200 shadow-xs">
          <h2 className="font-editorial font-bold text-base text-charcoal-900">
            Erreur de chargement
          </h2>
          <p className="text-xs sm:text-sm text-charcoal-600">
            Impossible de récupérer vos favoris pour le moment.
          </p>
          <Button
            variant="primary"
            size="md"
            onClick={() => refetch()}
            className="min-h-[44px]"
          >
            Réessayer
          </Button>
        </div>
      ) : favoris.length === 0 ? (
        <div className="relative overflow-hidden p-8 sm:p-16 text-center space-y-5 bg-white rounded-3xl border border-argile-200 shadow-warm-card">
          <PatternClaustra variant="terracotta" opacity={0.05} scale={1} />
          <div className="relative z-10 w-16 h-16 rounded-full bg-terracotta-100 text-terracotta-500 flex items-center justify-center mx-auto shadow-xs">
            <Heart className="w-8 h-8" aria-hidden="true" />
          </div>
          <div className="relative z-10 space-y-2 max-w-md mx-auto">
            <h2 className="font-editorial font-extrabold text-xl text-charcoal-900">
              Aucun favori pour le moment
            </h2>
            <p className="text-sm text-charcoal-600 leading-relaxed">
              Explorez les annonces disponibles et cliquez sur le cœur pour sauvegarder vos biens préférés ici.
            </p>
          </div>
          <div className="relative z-10 pt-2">
            <Link
              to="/recherche"
              className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] rounded-full bg-terracotta-500 text-white hover:bg-terracotta-600 font-display font-semibold text-sm shadow-terracotta-glow transition-all"
            >
              <Search className="w-4 h-4" aria-hidden="true" />
              <span>Parcourir les annonces</span>
            </Link>
          </div>
        </div>
      ) : filteredFavoris.length === 0 ? (
        <div className="p-8 sm:p-12 text-center space-y-4 bg-white rounded-3xl border border-argile-200 shadow-xs">
          <Building2 className="w-10 h-10 text-charcoal-400 mx-auto" aria-hidden="true" />
          <div className="space-y-1 max-w-md mx-auto">
            <h2 className="font-editorial font-bold text-lg text-charcoal-900">
              Aucun bien pour ce filtre sur cette page
            </h2>
            <p className="text-xs sm:text-sm text-charcoal-600">
              Réinitialisez le filtre rapide pour afficher l&rsquo;ensemble de vos favoris de la page.
            </p>
          </div>
          <Button
            variant="outline"
            size="md"
            onClick={() => setQuickFilter('TOUS')}
            className="min-h-[44px]"
          >
            Afficher tous les favoris
          </Button>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {filteredFavoris.map((prop, idx) => {
              const category = mapPropertyCategory(prop.type, prop.titre);
              const fallbackUrl = selectFallbackPhoto(category, prop.id, usedFallbacks);
              const hasSuperficie =
                typeof prop.superficie === 'number' &&
                Number.isFinite(prop.superficie) &&
                prop.superficie > 0;
              const hasPieces =
                typeof prop.nombrePieces === 'number' &&
                Number.isFinite(prop.nombrePieces) &&
                prop.nombrePieces > 0;
              const isAgent = prop.auteurRole === 'AGENT' || prop.auteur?.role === 'AGENT';
              const authorFullName = [prop.auteur?.prenom, prop.auteur?.nom]
                .filter(Boolean)
                .join(' ');
              const pubBadge = getPublicationBadgeMeta(prop.statutPublication);
              const isUnavailable = Boolean(pubBadge);

              return (
                <article
                  key={prop.id}
                  className={`bg-white rounded-2xl border overflow-hidden shadow-warm-card hover:shadow-warm-hover hover:border-terracotta-400 transition-all duration-200 flex flex-col group ${
                    isUnavailable ? 'border-argile-300/90' : 'border-argile-200'
                  }`}
                >
                  {/* Image Header aligned with SearchResultsPage */}
                  <div className="relative w-full h-48 bg-sand-200 overflow-hidden">
                    <PropertyVisual
                      mode="card"
                      rawUrl={prop.photos?.[0]?.url}
                      listingId={prop.id}
                      type={prop.type}
                      title={prop.titre}
                      quartier={prop.quartier}
                      fallbackUrl={fallbackUrl}
                      loading={idx < 3 ? 'eager' : 'lazy'}
                      className={`w-full h-full ${isUnavailable ? 'opacity-80 saturate-75' : ''}`}
                      imageClassName="group-hover:scale-102 transition-transform duration-300"
                    />

                    {/* Top-left transaction, verification & unavailability badges */}
                    <div className="absolute top-2.5 left-2.5 flex flex-wrap items-center gap-1.5 max-w-[calc(100%-3.5rem)]">
                      <span
                        className={`text-[10px] font-extrabold uppercase px-2 py-0.5 rounded shadow-sm ${
                          prop.modeTransaction === 'VENTE'
                            ? 'bg-ocher-800 text-white'
                            : 'bg-sahel-800 text-white'
                        }`}
                      >
                        {prop.modeTransaction === 'VENTE' ? 'Vente' : 'Location'}
                      </span>

                      {prop.statutVerification === 'VERIFIEE' && (
                        <span className="bg-sahel-700 text-white text-[10px] font-bold px-2 py-0.5 rounded shadow-sm inline-flex items-center gap-1">
                          <ShieldCheck className="w-3 h-3 shrink-0" aria-hidden="true" />
                          <span>Vérifié</span>
                        </span>
                      )}

                      {pubBadge && (
                        <Badge variant={pubBadge.variant} size="sm" className="shadow-sm">
                          {pubBadge.label}
                        </Badge>
                      )}
                    </div>

                    {/* Top-right Favorite toggle */}
                    <div className="absolute top-2.5 right-2.5">
                      <FavoriteButton
                        annonceId={prop.id}
                        estFavori={prop.estFavori ?? true}
                        size="md"
                        className="w-11 h-11 sm:w-10 sm:h-10"
                        onToggled={(nextFav) => handleFavoriteToggled(nextFav, prop.id)}
                      />
                    </div>
                  </div>

                  {/* Card Details */}
                  <div className="p-4 flex-1 min-w-0 flex flex-col justify-between space-y-3">
                    <div className="space-y-1.5">
                      <div className="flex flex-wrap items-center justify-between gap-1.5">
                        <span className="font-display font-extrabold text-base text-terracotta-600 whitespace-nowrap">
                          {prop.prix.toLocaleString('fr-FR')} FCFA
                          {prop.modeTransaction === 'LOCATION' && (
                            <span className="text-xs text-charcoal-500 font-normal"> /mois</span>
                          )}
                        </span>

                        <span className="text-xs text-charcoal-600 flex items-center gap-1 min-w-0">
                          <MapPin className="w-3.5 h-3.5 text-terracotta-500 shrink-0" aria-hidden="true" />
                          <span className="truncate">
                            {prop.ville}
                            {prop.quartier ? ` (${prop.quartier})` : ''}
                          </span>
                        </span>
                      </div>

                      <h3 className="font-editorial font-bold text-sm text-charcoal-900 line-clamp-2 leading-snug">
                        {prop.titre}
                      </h3>
                    </div>

                    {/* Normalized Technical Metadata Tags */}
                    <div className="flex flex-wrap items-center gap-1.5 text-xs text-charcoal-600 bg-sand-100/90 p-2 rounded-xl border border-sand-200">
                      {hasSuperficie && (
                        <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">
                          {prop.superficie} m²
                        </span>
                      )}
                      {hasPieces && (
                        <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">
                          {prop.nombrePieces} {prop.nombrePieces === 1 ? 'pièce' : 'pièces'}
                        </span>
                      )}
                      {prop.meuble && (
                        <span className="bg-terracotta-100 text-terracotta-800 text-[10px] font-bold px-2 py-0.5 rounded">
                          Meublé
                        </span>
                      )}
                      <span className="ml-auto font-mono text-[10px] uppercase bg-sahel-800 text-white px-2 py-0.5 rounded font-bold">
                        {CATEGORY_LABELS_FR[category] || prop.type}
                      </span>
                    </div>

                    {/* Footer: Author + Primary CTA */}
                    <div className="flex flex-wrap items-center justify-between gap-2 pt-1 border-t border-sand-200">
                      <div className="flex items-center gap-1.5 text-[11px] text-charcoal-600 flex-wrap min-w-0">
                        {authorFullName && (
                          <span className="font-semibold text-charcoal-800 truncate">
                            {authorFullName}
                          </span>
                        )}
                        {isAgent && (
                          <span className="bg-amber-100 text-amber-900 border border-amber-300 text-[10px] font-bold px-1.5 py-0.5 rounded">
                            Agent immobilier
                          </span>
                        )}
                      </div>

                      <Link
                        to={`/annonce/${prop.id}`}
                        className="inline-flex items-center justify-center px-4 py-2 min-h-[44px] sm:min-h-[40px] rounded-full bg-terracotta-500 text-white hover:bg-terracotta-600 font-display font-semibold text-xs shadow-terracotta-glow transition-all shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-500"
                      >
                        Voir la fiche
                      </Link>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <div className="flex flex-wrap items-center justify-between gap-3 pt-4 pb-2 text-xs font-semibold text-charcoal-700 border-t border-argile-200">
              <span>
                Page {data.number + 1} sur {data.totalPages}
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={data.first}
                  onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                  className="min-h-[44px] sm:min-h-[38px]"
                  leftIcon={<ChevronLeft className="w-4 h-4" />}
                >
                  Précédent
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={data.last}
                  onClick={() => setPage((prev) => prev + 1)}
                  className="min-h-[44px] sm:min-h-[38px]"
                  rightIcon={<ChevronRight className="w-4 h-4" />}
                >
                  Suivant
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default FavorisPage;
