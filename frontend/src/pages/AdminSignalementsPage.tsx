import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ShieldAlert, CheckCircle, AlertTriangle, ExternalLink, RefreshCw } from 'lucide-react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../services/api';
import { PageResponse, SignalementDTO, StatutSignalement } from '../types';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { toast } from 'sonner';

export const AdminSignalementsPage: React.FC = () => {
  const [statutFilter, setStatutFilter] = useState<string>('EN_ATTENTE');
  const [page, setPage] = useState<number>(0);
  const [processingId, setProcessingId] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery<PageResponse<SignalementDTO>>({
    queryKey: ['admin-signalements', statutFilter, page],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '15');
      if (statutFilter !== 'TOUS') {
        params.append('statut', statutFilter);
      }
      return apiFetch<PageResponse<SignalementDTO>>(`/api/v1/admin/signalements?${params.toString()}`);
    },
  });

  const handleTraiter = async (signalementId: string, statut: StatutSignalement, suspendreAnnonce: boolean = false) => {
    setProcessingId(signalementId);
    try {
      await apiFetch(`/api/v1/admin/signalements/${signalementId}/traiter`, {
        method: 'PUT',
        body: JSON.stringify({ statut, suspendreAnnonce }),
      });
      toast.success(
        suspendreAnnonce
          ? 'Signalement validé et annonce suspendue des recherches publiques'
          : `Signalement mis à jour (${statut})`
      );
      refetch();
    } catch (err: any) {
      toast.error(err.message || 'Erreur lors du traitement du signalement');
    } finally {
      setProcessingId(null);
    }
  };

  const signalements = data?.content || [];

  return (
    <div className="min-h-screen bg-sand-100 py-8 px-4 sm:px-6 lg:px-8">
      <div className="max-w-6xl mx-auto space-y-6">
        
        {/* Header */}
        <div className="bg-white p-6 rounded-2xl border border-argile-300 shadow-warm-card flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 relative overflow-hidden">
          <PatternClaustra variant="terracotta" opacity={0.05} scale={1} />
          <div className="relative z-10 flex items-center gap-3">
            <div className="w-12 h-12 rounded-xl bg-rose-100 text-rose-700 flex items-center justify-center shrink-0 shadow-xs">
              <ShieldAlert className="w-6 h-6" />
            </div>
            <div>
              <h1 className="font-display font-extrabold text-xl text-charcoal-900">
                Modération des Signalements d'Arnaques
              </h1>
              <p className="text-xs text-charcoal-600">
                Examinez les alertes utilisateurs et suspendez les annonces frauduleuses.
              </p>
            </div>
          </div>

          {/* Status Filter Tabs */}
          <div className="relative z-10 flex items-center gap-2 bg-sand-100 p-1.5 rounded-xl border border-argile-300">
            {['EN_ATTENTE', 'TRAITE', 'REJETE', 'TOUS'].map((st) => (
              <button
                key={st}
                onClick={() => {
                  setStatutFilter(st);
                  setPage(0);
                }}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  statutFilter === st
                    ? 'bg-sahel-800 text-white shadow-xs'
                    : 'text-charcoal-600 hover:bg-sand-200'
                }`}
              >
                {st === 'EN_ATTENTE' ? 'En Attente' : st === 'TRAITE' ? 'Traités' : st === 'REJETE' ? 'Rejetés' : 'Tous'}
              </button>
            ))}
          </div>
        </div>

        {/* Content Table / Cards */}
        {isLoading ? (
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="bg-white p-5 rounded-2xl border border-argile-200 space-y-3">
                <Skeleton variant="text" className="w-1/3" />
                <Skeleton variant="text" className="w-full" />
              </div>
            ))}
          </div>
        ) : isError ? (
          <div className="p-8 bg-white rounded-2xl border border-argile-300 text-center space-y-3">
            <p className="text-xs text-charcoal-600">Erreur lors de la récupération des signalements.</p>
            <Button variant="outline" size="sm" onClick={() => refetch()} leftIcon={<RefreshCw className="w-4 h-4" />}>
              Réessayer
            </Button>
          </div>
        ) : signalements.length === 0 ? (
          <div className="p-12 bg-white rounded-2xl border border-argile-300 text-center space-y-3 shadow-xs">
            <CheckCircle className="w-10 h-10 text-emerald-500 mx-auto" />
            <h3 className="font-display font-bold text-base text-charcoal-900">Aucun signalement dans cette catégorie</h3>
            <p className="text-xs text-charcoal-500">Toutes les alertes enregistrées ont été traitées.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {signalements.map((sig) => {
              const isPending = sig.statut === 'EN_ATTENTE';
              return (
                <div
                  key={sig.id}
                  className="bg-white p-5 rounded-2xl border border-argile-300 shadow-warm-card flex flex-col md:flex-row items-start md:items-center justify-between gap-4 transition-all hover:border-terracotta-400"
                >
                  <div className="space-y-2 flex-1">
                    <div className="flex items-center gap-2">
                      <Badge
                        variant={sig.statut === 'EN_ATTENTE' ? 'rose' : sig.statut === 'TRAITE' ? 'success' : 'neutral'}
                        className="text-[10px] uppercase font-bold"
                      >
                        {sig.statut === 'EN_ATTENTE' ? 'En Attente' : sig.statut === 'TRAITE' ? 'Traité' : 'Rejeté'}
                      </Badge>
                      <span className="text-xs font-bold text-terracotta-600 bg-terracotta-50 px-2 py-0.5 rounded border border-terracotta-200">
                        {sig.motif.replace('_', ' ')}
                      </span>
                      <span className="text-[11px] text-charcoal-400">
                        Signalé le {new Date(sig.dateCreation).toLocaleDateString('fr-FR')} par {sig.auteurEmail}
                      </span>
                    </div>

                    <div className="flex items-center gap-2">
                      <h3 className="font-display font-bold text-sm text-charcoal-900">
                        Annonce : {sig.annonceTitre}
                      </h3>
                      <Link
                        to={`/annonce/${sig.annonceId}`}
                        target="_blank"
                        className="text-terracotta-600 hover:text-terracotta-700 flex items-center gap-0.5 text-xs font-semibold"
                      >
                        <span>Voir</span>
                        <ExternalLink className="w-3.5 h-3.5" />
                      </Link>
                    </div>

                    {sig.description && (
                      <p className="text-xs text-charcoal-700 bg-sand-100 p-3 rounded-xl border border-argile-200 italic">
                        "{sig.description}"
                      </p>
                    )}
                  </div>

                  {/* Action Buttons */}
                  {isPending && (
                    <div className="flex items-center gap-2 shrink-0 pt-2 md:pt-0 border-t md:border-t-0 border-argile-200 w-full md:w-auto">
                      <Button
                        variant="primary"
                        size="sm"
                        className="text-xs py-1.5 px-3 bg-rose-600 hover:bg-rose-700 text-white shadow-none"
                        isLoading={processingId === sig.id}
                        leftIcon={<AlertTriangle className="w-3.5 h-3.5" />}
                        onClick={() => handleTraiter(sig.id, 'TRAITE', true)}
                      >
                        Suspendre Annonce
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        className="text-xs py-1.5 px-3"
                        isLoading={processingId === sig.id}
                        onClick={() => handleTraiter(sig.id, 'TRAITE', false)}
                      >
                        Classer Traité
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-xs py-1.5 px-2 text-charcoal-500 hover:text-rose-600"
                        isLoading={processingId === sig.id}
                        onClick={() => handleTraiter(sig.id, 'REJETE', false)}
                      >
                        Rejeter
                      </Button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}

      </div>
    </div>
  );
};
