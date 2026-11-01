import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Heart, MapPin, ShieldCheck, Search, ChevronLeft, ChevronRight } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { apiFetch, getPhotoUrl } from '../services/api';
import { AnnonceDTO, PageResponse } from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { FavoriteButton } from '../components/ui/FavoriteButton';

export const FavorisPage: React.FC = () => {
  const navigate = useNavigate();
  const { utilisateur } = useAuthStore();
  const [page, setPage] = useState<number>(0);

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!utilisateur) {
      navigate('/connexion', { state: { from: '/favoris' } });
    }
  }, [utilisateur, navigate]);

  const { data, isLoading, isError } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: ['favoris', page],
    queryFn: () =>
      apiFetch<PageResponse<AnnonceDTO>>(
        `/api/v1/utilisateurs/moi/favoris?page=${page}&size=12`
      ),
    enabled: !!utilisateur,
  });

  const favoris = data?.content || [];

  if (!utilisateur) return null;

  return (
    <div className="max-w-7xl mx-auto px-4 py-8 space-y-6">
      {/* Page Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-2xl bg-terracotta-500 flex items-center justify-center shadow-terracotta-glow">
            <Heart className="w-6 h-6 text-white fill-current" />
          </div>
          <div>
            <h1 className="font-display font-extrabold text-2xl md:text-3xl text-charcoal-900 tracking-tight">
              Mes Favoris
            </h1>
            <p className="text-xs text-charcoal-500">
              {data?.totalElements ?? 0} annonce{(data?.totalElements ?? 0) > 1 ? 's' : ''} sauvegardée{(data?.totalElements ?? 0) > 1 ? 's' : ''}
            </p>
          </div>
        </div>
      </div>

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
        <div className="p-8 text-center space-y-4 bg-white rounded-2xl border border-argile-200">
          <h3 className="font-display font-bold text-base text-charcoal-800">Erreur de chargement</h3>
          <p className="text-xs text-charcoal-500">Impossible de récupérer vos favoris pour le moment.</p>
          <Button variant="primary" size="sm" onClick={() => window.location.reload()}>
            Réessayer
          </Button>
        </div>
      ) : favoris.length === 0 ? (
        <div className="relative overflow-hidden p-8 sm:p-16 text-center space-y-5 bg-white rounded-3xl border border-argile-300 shadow-warm-card">
          <PatternClaustra variant="terracotta" opacity={0.05} scale={1} />
          <div className="relative z-10 w-16 h-16 rounded-full bg-terracotta-100 text-terracotta-500 flex items-center justify-center mx-auto shadow-xs">
            <Heart className="w-8 h-8" />
          </div>
          <div className="relative z-10 space-y-2 max-w-md mx-auto">
            <h3 className="font-display font-extrabold text-xl text-charcoal-900">
              Aucun favori pour le moment
            </h3>
            <p className="text-sm text-charcoal-600 leading-relaxed">
              Explorez les annonces disponibles et cliquez sur le cœur pour sauvegarder vos biens préférés ici.
            </p>
          </div>
          <div className="relative z-10 pt-2">
            <Link to="/recherche">
              <Button variant="primary" size="md" leftIcon={<Search className="w-4 h-4" />}>
                Parcourir les annonces
              </Button>
            </Link>
          </div>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {favoris.map((prop) => {
              const firstPhoto =
                prop.photos && prop.photos.length > 0
                  ? getPhotoUrl(prop.photos[0].url)
                  : getPhotoUrl();
              return (
                <div
                  key={prop.id}
                  className="bg-white rounded-2xl border border-argile-200 overflow-hidden shadow-warm-card hover:shadow-warm-hover hover:border-terracotta-400 transition-all duration-200 flex flex-col group"
                >
                  {/* Image */}
                  <div className="relative w-full h-48 bg-sand-200">
                    <img
                      src={firstPhoto}
                      alt={prop.titre}
                      className="w-full h-full object-cover group-hover:scale-102 transition-transform duration-300"
                    />
                    {/* Badges */}
                    <div className="absolute top-2 left-2 flex items-center gap-1.5">
                      {prop.statutVerification === 'VERIFIEE' && (
                        <Badge variant="sahel" className="text-[10px] px-2 py-0.5 font-bold">
                          <ShieldCheck className="w-3 h-3 mr-1 inline" /> Vérifié
                        </Badge>
                      )}
                    </div>
                    {/* Favorite button */}
                    <div className="absolute top-2 right-2">
                      <FavoriteButton
                        annonceId={prop.id}
                        estFavori={prop.estFavori ?? true}
                      />
                    </div>
                  </div>

                  {/* Details */}
                  <div className="p-4 flex-1 flex flex-col justify-between space-y-3">
                    <div>
                      <div className="flex items-center justify-between text-xs text-sahel-800 font-semibold mb-1">
                        <span className="flex items-center gap-1">
                          <MapPin className="w-3.5 h-3.5 text-terracotta-500" />
                          {prop.quartier}, {prop.ville}
                        </span>
                        <span className="text-terracotta-600 font-extrabold text-sm">
                          {prop.prix.toLocaleString('fr-FR')} FCFA
                          {prop.modeTransaction === 'LOCATION' ? ' / mois' : ''}
                        </span>
                      </div>
                      <h3 className="font-display font-bold text-sm text-charcoal-900 line-clamp-2">
                        {prop.titre}
                      </h3>
                    </div>

                    {/* Tags */}
                    <div className="flex items-center gap-2 text-xs text-charcoal-600 bg-sand-100/90 p-2 rounded-lg">
                      <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">
                        {prop.superficie} m²
                      </span>
                      <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">
                        {prop.nombrePieces} pièces
                      </span>
                      {prop.meuble && (
                        <span className="bg-terracotta-100 text-terracotta-800 text-[10px] font-bold px-2 py-0.5 rounded">
                          Meublé
                        </span>
                      )}
                      <span className="ml-auto font-mono text-[10px] uppercase bg-sahel-800 text-white px-2 py-0.5 rounded font-bold">
                        {prop.type}
                      </span>
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      <div className="flex items-center gap-1.5 text-[11px] text-charcoal-600">
                        <span className="font-semibold text-charcoal-800">
                          {prop.auteur?.prenom} {prop.auteur?.nom}
                        </span>
                      </div>
                      <Link to={`/annonce/${prop.id}`}>
                        <Button variant="primary" size="sm" className="text-xs py-1.5 px-3">
                          Voir la fiche
                        </Button>
                      </Link>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <div className="flex items-center justify-between pt-4 pb-2 text-xs font-semibold text-charcoal-700 border-t border-argile-200">
              <span>
                Page {data.number + 1} sur {data.totalPages}
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={data.first}
                  onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                  leftIcon={<ChevronLeft className="w-4 h-4" />}
                >
                  Précédent
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={data.last}
                  onClick={() => setPage((prev) => prev + 1)}
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
