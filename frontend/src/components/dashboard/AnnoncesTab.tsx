import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  Building,
  CalendarClock,
  ChevronLeft,
  ChevronRight,
  Eye,
  Pencil,
  Plus,
  Search,
  SearchX,
  ShieldCheck,
  Trash2,
  X,
} from 'lucide-react';
import { apiFetch } from '../../services/api';
import { getErrorMessage } from '../../lib/errors';
import {
  canPublishListings,
  formatDateFr,
  formatRoomCount,
  formatSurface,
  getPublicationMeta,
  getVerificationMeta,
  parsePageParam,
  pluralize,
} from '../../lib/dashboardModel';
import {
  MY_ANNONCES_PAGE_SIZE,
  useMyAnnonces,
  type AnnonceModeFilter,
  type AnnonceVerificationFilter,
} from '../../hooks/useDashboardData';
import type { AnnonceDTO, UtilisateurDTO } from '../../types';
import { PropertyVisual } from '../property/PropertyVisual';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Card, CardBody } from '../ui/Card';
import { EditAnnonceModal } from './EditAnnonceModal';
import { ConfirmActionModal } from './ConfirmActionModal';
import { DashboardEmpty, DashboardError, DashboardListSkeleton, DashboardTabHeader } from './DashboardStates';

interface AnnoncesTabProps {
  utilisateur: UtilisateurDTO;
  /** Nombre de demandes de visite à traiter, indexé par annonce (données réelles du dashboard). */
  pendingRequestsByAnnonce: Record<string, number>;
  onCreate: () => void;
}

const MODE_OPTIONS: { value: '' | AnnonceModeFilter; label: string }[] = [
  { value: '', label: 'Tous les types de transaction' },
  { value: 'LOCATION', label: 'Location' },
  { value: 'VENTE', label: 'Vente' },
];

const VERIFICATION_OPTIONS: { value: '' | AnnonceVerificationFilter; label: string }[] = [
  { value: '', label: 'Toutes les vérifications' },
  { value: 'VERIFIEE', label: 'Vérifiées' },
  { value: 'EN_ATTENTE', label: 'Vérification en cours' },
  { value: 'REJETEE', label: 'Rejetées' },
];

const parseMode = (raw: string | null): AnnonceModeFilter | undefined =>
  raw === 'LOCATION' || raw === 'VENTE' ? raw : undefined;

const parseVerification = (raw: string | null): AnnonceVerificationFilter | undefined =>
  raw === 'VERIFIEE' || raw === 'EN_ATTENTE' || raw === 'REJETEE' ? raw : undefined;

const selectClassName =
  'w-full min-h-[44px] px-3 rounded-xl border border-sand-300 bg-white text-sm text-sahel-900 focus:outline-none focus:ring-2 focus:ring-terracotta-500';

/** Champ de recherche à soumission explicite : aucun appel réseau pendant la saisie. */
const AnnoncesSearchForm: React.FC<{ initialValue: string; onSubmit: (value: string) => void }> = ({
  initialValue,
  onSubmit,
}) => {
  const [draft, setDraft] = useState(initialValue);
  return (
    <form
      role="search"
      aria-label="Rechercher dans mes annonces"
      className="flex gap-2"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit(draft.trim());
      }}
    >
      <div className="relative flex-1 min-w-0">
        <label htmlFor="dashboard-annonces-q" className="sr-only">
          Rechercher dans mes annonces
        </label>
        <Search className="w-4 h-4 text-sahel-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" aria-hidden="true" />
        <input
          id="dashboard-annonces-q"
          type="search"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Titre, quartier, mot-clé…"
          maxLength={100}
          className="w-full min-h-[44px] pl-10 pr-10 rounded-xl border border-sand-300 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-terracotta-500"
        />
        {draft && (
          <button
            type="button"
            onClick={() => {
              setDraft('');
              if (initialValue) onSubmit('');
            }}
            aria-label="Effacer la recherche"
            className="absolute right-1.5 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full flex items-center justify-center text-sahel-500 hover:bg-sand-200 focus:outline-none focus:ring-2 focus:ring-terracotta-400"
          >
            <X className="w-4 h-4" aria-hidden="true" />
          </button>
        )}
      </div>
      <Button type="submit" variant="secondary" size="sm" className="min-h-[44px] px-5">
        Rechercher
      </Button>
    </form>
  );
};

export const AnnoncesTab: React.FC<AnnoncesTabProps> = ({ utilisateur, pendingRequestsByAnnonce, onCreate }) => {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [annonceEnEdition, setAnnonceEnEdition] = useState<AnnonceDTO | null>(null);
  const [annonceASupprimer, setAnnonceASupprimer] = useState<AnnonceDTO | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const q = searchParams.get('aq') ?? '';
  const modeTransaction = parseMode(searchParams.get('amode'));
  const statutVerification = parseVerification(searchParams.get('averif'));
  const page = parsePageParam(searchParams.get('apage'));
  const hasFilters = Boolean(q || modeTransaction || statutVerification);
  const isAgent = utilisateur.role === 'AGENT';
  const canPublish = canPublishListings(utilisateur.role);

  const { data, isLoading, isError, isFetching, isPlaceholderData, refetch } = useMyAnnonces(
    { page: page - 1, size: MY_ANNONCES_PAGE_SIZE, q: q || undefined, modeTransaction, statutVerification },
    true
  );

  const annonces = data?.content ?? [];
  const totalElements = typeof data?.totalElements === 'number' ? data.totalElements : annonces.length;
  const totalPages = typeof data?.totalPages === 'number' ? data.totalPages : annonces.length > 0 ? 1 : 0;

  const updateParams = (patch: Record<string, string | null>, replace = false) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(patch).forEach(([key, value]) => {
      if (value === null || value === '') next.delete(key);
      else next.set(key, value);
    });
    setSearchParams(next, { replace });
  };

  // Page hors limites (ex. dernière annonce supprimée) : retour à la dernière page valide.
  useEffect(() => {
    if (!data || isPlaceholderData) return;
    if (page > 1 && page > Math.max(totalPages, 1)) {
      const next = new URLSearchParams(searchParams);
      if (totalPages > 1) next.set('apage', String(totalPages));
      else next.delete('apage');
      setSearchParams(next, { replace: true });
    }
  }, [data, isPlaceholderData, page, totalPages, searchParams, setSearchParams]);

  const resetFilters = () => updateParams({ aq: null, amode: null, averif: null, apage: null });

  const handleConfirmSuppression = async () => {
    if (!annonceASupprimer) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await apiFetch(`/api/v1/annonces/${annonceASupprimer.id}`, { method: 'DELETE' });
      toast.success('Annonce supprimée avec succès');
      setAnnonceASupprimer(null);
      await queryClient.invalidateQueries({ queryKey: ['my-annonces'] });
    } catch (err: unknown) {
      setDeleteError(getErrorMessage(err, "Erreur lors de la suppression de l'annonce"));
    } finally {
      setIsDeleting(false);
    }
  };

  const title = isAgent ? 'Mon portefeuille d’annonces' : 'Mes Annonces Immobilières';
  const description = isAgent
    ? 'Pilotez les biens que vous gérez : statut de publication, vérification et demandes de visite.'
    : 'Gérez vos propriétés, vérifiez le statut de publication et contrôlez vos annonces.';

  return (
    <div className="space-y-6">
      <DashboardTabHeader
        title={title}
        description={description}
        actions={
          canPublish ? (
            <Button variant="primary" onClick={onCreate} className="min-h-[44px] w-full sm:w-auto" leftIcon={<Plus className="w-4 h-4" aria-hidden="true" />}>
              Créer une annonce
            </Button>
          ) : undefined
        }
      />

      <Card className="p-3 sm:p-4 border border-sand-300 space-y-3">
        <AnnoncesSearchForm key={q} initialValue={q} onSubmit={(value) => updateParams({ aq: value || null, apage: null })} />
        <div className="grid grid-cols-1 sm:grid-cols-[1fr_1fr_auto] gap-2 items-end">
          <div>
            <label htmlFor="dashboard-annonces-mode" className="block text-[11px] font-bold text-sahel-700 mb-1">
              Transaction
            </label>
            <select
              id="dashboard-annonces-mode"
              className={selectClassName}
              value={modeTransaction ?? ''}
              onChange={(event) => updateParams({ amode: event.target.value || null, apage: null })}
            >
              {MODE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="dashboard-annonces-verif" className="block text-[11px] font-bold text-sahel-700 mb-1">
              Vérification
            </label>
            <select
              id="dashboard-annonces-verif"
              className={selectClassName}
              value={statutVerification ?? ''}
              onChange={(event) => updateParams({ averif: event.target.value || null, apage: null })}
            >
              {VERIFICATION_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          {hasFilters && (
            <Button variant="ghost" size="sm" onClick={resetFilters} className="min-h-[44px]">
              Réinitialiser
            </Button>
          )}
        </div>
        {hasFilters && (
          <p className="text-[11px] text-sahel-500">
            Filtres actifs
            {q && <> · mot-clé « {q} »</>}
            {modeTransaction && <> · {modeTransaction === 'LOCATION' ? 'location' : 'vente'}</>}
            {statutVerification && <> · {VERIFICATION_OPTIONS.find((o) => o.value === statutVerification)?.label.toLowerCase()}</>}
          </p>
        )}
      </Card>

      {isLoading ? (
        <DashboardListSkeleton rows={2} />
      ) : isError ? (
        <DashboardError title="Impossible de charger vos annonces" onRetry={() => void refetch()} isRetrying={isFetching} />
      ) : annonces.length === 0 ? (
        hasFilters ? (
          <DashboardEmpty
            icon={SearchX}
            title="Aucune annonce ne correspond"
            description="Modifiez ou retirez vos filtres pour retrouver vos biens."
            action={
              <Button variant="outline" onClick={resetFilters} className="min-h-[44px]">
                Réinitialiser les filtres
              </Button>
            }
          />
        ) : (
          <DashboardEmpty
            icon={Building}
            title="Aucune annonce enregistrée"
            description="Vous n'avez pas encore publié de propriété. Déposez votre premier bien pour recevoir des demandes de visite."
            action={
              canPublish ? (
                <Button variant="primary" onClick={onCreate} className="min-h-[44px]" leftIcon={<Plus className="w-4 h-4" aria-hidden="true" />}>
                  Publier un bien
                </Button>
              ) : undefined
            }
          />
        )
      ) : (
        <div className={isPlaceholderData ? 'opacity-60 transition-opacity' : 'transition-opacity'} aria-busy={isPlaceholderData}>
          <p className="text-xs text-sahel-600 mb-3" aria-live="polite">
            <strong className="text-sahel-900">{totalElements}</strong> {pluralize(totalElements, 'annonce')}
            {totalPages > 1 && <> · page {page} sur {totalPages}</>}
          </p>
          <ul className="grid grid-cols-1 gap-3 sm:gap-4" aria-label="Mes annonces">
            {annonces.map((item) => {
              const publication = getPublicationMeta(item.statutPublication);
              const verification = getVerificationMeta(item.statutVerification);
              const rooms = formatRoomCount(item.nombrePieces);
              const surface = formatSurface(item.superficie);
              const pending = pendingRequestsByAnnonce[item.id] ?? 0;
              const canEdit = utilisateur.role === 'ADMIN' || utilisateur.id === item.auteur?.id;
              const createdAt = formatDateFr(item.dateCreation);
              const place = [item.quartier, item.ville].filter(Boolean).join(', ');
              return (
                <li key={item.id}>
                  <Card className="border border-sand-300 hover:shadow-warm-hover transition-all">
                    <CardBody className="p-3 sm:p-5">
                      <div className="flex flex-col lg:flex-row lg:items-center gap-4">
                        <div className="flex items-start gap-3 sm:gap-4 min-w-0 flex-1">
                          <PropertyVisual
                            mode="thumbnail"
                            rawUrl={item.photos?.[0]?.url}
                            listingId={item.id}
                            type={item.type}
                            title={item.titre}
                            quartier={item.quartier}
                            className="w-20 h-20 sm:w-28 sm:h-28 rounded-xl shrink-0"
                            showIllustrativeBadge={true}
                            illustrativeBadgeCompact={true}
                          />
                          <div className="min-w-0 flex-1 space-y-1.5">
                            <h3 className="font-display font-bold text-sm sm:text-base text-sahel-900 break-words">{item.titre}</h3>
                            {place && <p className="text-xs text-sahel-600 break-words">{place}</p>}
                            <p className="text-sm font-bold text-terracotta-700">
                              {typeof item.prix === 'number' ? `${item.prix.toLocaleString('fr-FR')} FCFA` : 'Prix non renseigné'}
                              {item.modeTransaction === 'LOCATION' && typeof item.prix === 'number' && (
                                <span className="text-xs font-semibold text-sahel-500"> / mois</span>
                              )}
                            </p>
                            {(rooms || surface || createdAt) && (
                              <p className="text-[11px] text-sahel-500">
                                {[rooms, surface, createdAt ? `publiée le ${createdAt}` : null].filter(Boolean).join(' · ')}
                              </p>
                            )}
                            <div className="flex flex-wrap items-center gap-1.5 pt-0.5">
                              <Badge variant={publication.variant} size="sm">Publication : {publication.label}</Badge>
                              <Badge variant={verification.variant} size="sm" className="gap-1">
                                {item.statutVerification === 'VERIFIEE' && <ShieldCheck className="w-3 h-3" aria-hidden="true" />}
                                {verification.label}
                              </Badge>
                              <Badge variant="sand" size="sm">{item.modeTransaction === 'VENTE' ? 'Vente' : 'Location'}</Badge>
                              {pending > 0 && (
                                <Badge variant="terracotta" size="sm" className="gap-1">
                                  <CalendarClock className="w-3 h-3" aria-hidden="true" />
                                  {pending} {pluralize(pending, 'demande')} de visite à traiter
                                </Badge>
                              )}
                            </div>
                          </div>
                        </div>

                        <div className="flex flex-wrap items-center gap-2 lg:justify-end border-t lg:border-t-0 pt-3 lg:pt-0 border-sand-200 shrink-0">
                          {canEdit && (
                            <>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setAnnonceEnEdition(item)}
                                className="min-h-[44px] text-xs text-sahel-700 hover:bg-sand-200"
                              >
                                <Pencil className="w-3.5 h-3.5 text-terracotta-600" aria-hidden="true" />
                                <span>Modifier</span>
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => {
                                  setDeleteError(null);
                                  setAnnonceASupprimer(item);
                                }}
                                className="min-h-[44px] text-xs text-rose-600 hover:bg-rose-50"
                              >
                                <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                                <span>Supprimer</span>
                              </Button>
                            </>
                          )}
                          <Link
                            to={`/annonce/${item.id}`}
                            className="inline-flex items-center justify-center gap-1.5 min-h-[44px] px-3 rounded-full text-xs font-display font-semibold text-sahel-700 hover:bg-sand-200 focus:outline-none focus:ring-2 focus:ring-sahel-400"
                          >
                            <Eye className="w-4 h-4 text-sahel-600" aria-hidden="true" />
                            <span>Consulter</span>
                          </Link>
                        </div>
                      </div>
                    </CardBody>
                  </Card>
                </li>
              );
            })}
          </ul>

          {totalPages > 1 && (
            <nav aria-label="Pagination des annonces" className="mt-5 flex items-center justify-between gap-3">
              <Button
                variant="outline"
                size="sm"
                className="min-h-[44px]"
                disabled={page <= 1 || isPlaceholderData}
                onClick={() => updateParams({ apage: page - 1 > 1 ? String(page - 1) : null })}
                leftIcon={<ChevronLeft className="w-4 h-4" aria-hidden="true" />}
              >
                Précédent
              </Button>
              <span className="text-xs font-semibold text-sahel-700" aria-live="polite">
                Page {page} sur {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                className="min-h-[44px]"
                disabled={page >= totalPages || isPlaceholderData}
                onClick={() => updateParams({ apage: String(page + 1) })}
                rightIcon={<ChevronRight className="w-4 h-4" aria-hidden="true" />}
              >
                Suivant
              </Button>
            </nav>
          )}
        </div>
      )}

      <EditAnnonceModal
        isOpen={!!annonceEnEdition}
        onClose={() => setAnnonceEnEdition(null)}
        annonce={annonceEnEdition}
        onSuccess={() => {
          void queryClient.invalidateQueries({ queryKey: ['my-annonces'] });
        }}
      />

      <ConfirmActionModal
        isOpen={!!annonceASupprimer}
        title="Confirmer la suppression"
        description={
          <>
            <p>
              Êtes-vous sûr de vouloir supprimer l'annonce <strong>« {annonceASupprimer?.titre} »</strong> ?
            </p>
            <p className="text-xs text-charcoal-500 bg-sand-100 p-3 rounded-xl border border-sand-300">
              Cette action effectue une <strong>suppression logique</strong> : le bien ne sera plus accessible aux
              recherches publiques, mais restera archivé pour l'historique administratif.
            </p>
          </>
        }
        confirmLabel="Confirmer la suppression"
        cancelLabel="Annuler"
        isLoading={isDeleting}
        errorMessage={deleteError}
        onConfirm={handleConfirmSuppression}
        onClose={() => setAnnonceASupprimer(null)}
      />
    </div>
  );
};
