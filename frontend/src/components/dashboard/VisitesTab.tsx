import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Building, CalendarX2, CheckCircle2, Clock, ExternalLink, FileText, Mail, UserRound } from 'lucide-react';
import { apiFetch } from '../../services/api';
import { getErrorMessage } from '../../lib/errors';
import {
  buildVisitGroups,
  canManageVisit,
  formatDateTimeFr,
  isOwnVisit,
  isVisitDatePassed,
  type DashboardTabId,
  type VisitGroup,
} from '../../lib/dashboardModel';
import type { RendezVousResponseDTO, TransactionResponseDTO, UtilisateurDTO } from '../../types';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Card, CardBody } from '../ui/Card';
import { ConfirmActionModal } from './ConfirmActionModal';
import { DashboardEmpty, DashboardError, DashboardListSkeleton, DashboardTabHeader } from './DashboardStates';

interface VisitesTabProps {
  utilisateur: UtilisateurDTO;
  visits: RendezVousResponseDTO[];
  transactions: TransactionResponseDTO[];
  isLoading: boolean;
  isError: boolean;
  isRefetching: boolean;
  onRetry: () => void;
  onSelectTab: (tab: DashboardTabId) => void;
}

const STATUT_BADGES: Record<RendezVousResponseDTO['statut'], { label: string; variant: 'warning' | 'info' | 'rose' | 'success' }> = {
  DEMANDE: { label: 'Demandé', variant: 'warning' },
  CONFIRME: { label: 'Confirmé', variant: 'info' },
  ANNULE: { label: 'Annulé', variant: 'rose' },
  EFFECTUE: { label: 'Visite effectuée', variant: 'success' },
};

type PendingAction = { kind: 'confirm' | 'effectue'; id: string } | null;

export const VisitesTab: React.FC<VisitesTabProps> = ({
  utilisateur,
  visits,
  transactions,
  isLoading,
  isError,
  isRefetching,
  onRetry,
  onSelectTab,
}) => {
  const queryClient = useQueryClient();
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);
  const [visitToCancel, setVisitToCancel] = useState<RendezVousResponseDTO | null>(null);
  const [isCancelling, setIsCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const isStaff = utilisateur.role !== 'LOCATAIRE';
  const groups = buildVisitGroups(visits, utilisateur);

  const refreshVisits = () => queryClient.invalidateQueries({ queryKey: ['my-visites'] });

  const runAction = async (kind: 'confirm' | 'effectue', visitId: string) => {
    setPendingAction({ kind, id: visitId });
    try {
      if (kind === 'confirm') {
        await apiFetch<RendezVousResponseDTO>(`/api/v1/rendez-vous/${visitId}/confirmer`, { method: 'PUT' });
        toast.success('Rendez-vous confirmé avec succès !');
      } else {
        await apiFetch<RendezVousResponseDTO>(`/api/v1/rendez-vous/${visitId}/marquer-effectue`, { method: 'PUT' });
        toast.success('Rendez-vous marqué comme EFFECTUÉ !');
      }
      await refreshVisits();
    } catch (err: unknown) {
      toast.error(
        getErrorMessage(err, kind === 'confirm' ? 'Échec de la confirmation du rendez-vous' : 'Échec du changement de statut')
      );
    } finally {
      setPendingAction(null);
    }
  };

  const confirmCancellation = async () => {
    if (!visitToCancel) return;
    setIsCancelling(true);
    setCancelError(null);
    try {
      await apiFetch<RendezVousResponseDTO>(`/api/v1/rendez-vous/${visitToCancel.id}/annuler`, {
        method: 'PUT',
        body: JSON.stringify({ motif: 'Annulé depuis le tableau de bord' }),
      });
      toast.success('Rendez-vous annulé.');
      setVisitToCancel(null);
      await refreshVisits();
    } catch (err: unknown) {
      setCancelError(getErrorMessage(err, "Échec de l'annulation du rendez-vous. Réessayez dans un instant."));
    } finally {
      setIsCancelling(false);
    }
  };

  const hasTransactionFor = (annonceId: string) =>
    transactions.some((tx) => tx.annonceId === annonceId && tx.locataireId === utilisateur.id);

  const renderVisit = (rdv: RendezVousResponseDTO, group: VisitGroup) => {
    const badge = STATUT_BADGES[rdv.statut];
    const own = isOwnVisit(rdv, utilisateur);
    const manageable = canManageVisit(rdv, utilisateur);
    const rowBusy = pendingAction?.id === rdv.id;
    const datePassed = (rdv.statut === 'DEMANDE' || rdv.statut === 'CONFIRME') && isVisitDatePassed(rdv);

    return (
      <Card
        key={rdv.id}
        className={`border border-sand-300 ${group.isHistory ? 'bg-sand-50/70' : ''}`}
        data-testid="visite-card"
      >
        <CardBody className="p-4 sm:p-5">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
            <div className="space-y-2 min-w-0">
              <div className="flex items-start gap-2">
                <Building className="w-4 h-4 text-terracotta-500 shrink-0 mt-0.5" aria-hidden="true" />
                <h3 className="font-display font-bold text-sm sm:text-base text-sahel-900 break-words min-w-0">
                  {rdv.annonceTitre || 'Bien non renseigné'}
                </h3>
              </div>
              <p className="text-xs sm:text-[13px] text-terracotta-700 font-semibold flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                <span>{formatDateTimeFr(rdv.dateHeure)}</span>
                {datePassed && <Badge variant="outline" size="sm">Date dépassée</Badge>}
              </p>
              {manageable ? (
                <p className="text-xs text-sahel-600 flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span className="inline-flex items-center gap-1.5">
                    <UserRound className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                    <span>
                      Demandeur : <strong className="text-sahel-900">{rdv.demandeurNomComplet}</strong>
                    </span>
                  </span>
                  {rdv.demandeurEmail && (
                    <a
                      href={`mailto:${rdv.demandeurEmail}`}
                      className="inline-flex items-center gap-1.5 text-sahel-700 underline decoration-sahel-300 break-all"
                    >
                      <Mail className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                      {rdv.demandeurEmail}
                    </a>
                  )}
                </p>
              ) : own && isStaff ? (
                <p className="text-xs text-sahel-600">Demande effectuée par vous.</p>
              ) : null}
              {rdv.statut === 'ANNULE' && rdv.motifAnnulation && (
                <p className="text-xs text-sahel-500">Motif d’annulation : {rdv.motifAnnulation}</p>
              )}
            </div>

            <div className="flex flex-col gap-3 lg:items-end shrink-0">
              <Badge variant={badge.variant} className="self-start lg:self-end">
                {badge.label}
              </Badge>
              <div className="flex flex-wrap items-center gap-2">
                {rdv.statut === 'DEMANDE' && manageable && (
                  <Button
                    variant="primary"
                    size="sm"
                    className="min-h-[44px]"
                    isLoading={rowBusy && pendingAction?.kind === 'confirm'}
                    disabled={rowBusy}
                    onClick={() => runAction('confirm', rdv.id)}
                  >
                    Confirmer
                  </Button>
                )}
                {rdv.statut === 'CONFIRME' && manageable && (
                  <Button
                    variant="outline"
                    size="sm"
                    className="min-h-[44px] border-emerald-600 text-emerald-700 hover:bg-emerald-50"
                    isLoading={rowBusy && pendingAction?.kind === 'effectue'}
                    disabled={rowBusy}
                    onClick={() => runAction('effectue', rdv.id)}
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" aria-hidden="true" />
                    Marquer effectué
                  </Button>
                )}
                {(rdv.statut === 'DEMANDE' || rdv.statut === 'CONFIRME') && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="min-h-[44px] text-rose-600 hover:bg-rose-50"
                    disabled={rowBusy}
                    onClick={() => {
                      setCancelError(null);
                      setVisitToCancel(rdv);
                    }}
                  >
                    Annuler
                  </Button>
                )}
                {rdv.statut === 'EFFECTUE' && utilisateur.role === 'LOCATAIRE' && (
                  hasTransactionFor(rdv.annonceId) ? (
                    <Button
                      variant="secondary"
                      size="sm"
                      className="min-h-[44px]"
                      onClick={() => onSelectTab('transactions')}
                    >
                      <FileText className="w-3.5 h-3.5" aria-hidden="true" />
                      Voir le dossier
                    </Button>
                  ) : (
                    <Link
                      to={`/annonce/${rdv.annonceId}`}
                      className="inline-flex items-center justify-center min-h-[44px] px-4 rounded-full bg-terracotta-500 text-white text-xs font-display font-semibold hover:bg-terracotta-600 focus:outline-none focus:ring-2 focus:ring-terracotta-400 focus:ring-offset-2"
                    >
                      Initier location / achat
                    </Link>
                  )
                )}
                <Link
                  to={`/annonce/${rdv.annonceId}`}
                  className="inline-flex items-center justify-center gap-1.5 min-h-[44px] px-3 rounded-full text-xs font-display font-semibold text-sahel-700 hover:bg-sand-200 focus:outline-none focus:ring-2 focus:ring-sahel-400"
                >
                  <ExternalLink className="w-3.5 h-3.5" aria-hidden="true" />
                  Voir le bien
                </Link>
              </div>
            </div>
          </div>
        </CardBody>
      </Card>
    );
  };

  const isLocataire = utilisateur.role === 'LOCATAIRE';

  return (
    <div className="space-y-6">
      <DashboardTabHeader
        title={isLocataire ? 'Mes Visites' : 'Agenda des Visites'}
        description={
          isLocataire
            ? 'Suivez vos demandes de visite, vos rendez-vous confirmés et leur historique.'
            : 'Traitez les demandes de visite sur vos biens et suivez vos propres demandes.'
        }
      />

      {isLoading ? (
        <DashboardListSkeleton rows={2} />
      ) : isError ? (
        <DashboardError title="Impossible de charger vos visites" onRetry={onRetry} isRetrying={isRefetching} />
      ) : visits.length === 0 ? (
        <DashboardEmpty
          icon={CalendarX2}
          title="Aucun rendez-vous prévu"
          description={
            isLocataire
              ? 'Repérez un bien qui vous plaît et demandez une visite depuis sa fiche : le suivi apparaîtra ici.'
              : 'Les demandes de visite sur vos biens apparaîtront ici dès leur réception.'
          }
          action={
            isLocataire ? (
              <Link
                to="/recherche"
                className="inline-flex items-center justify-center min-h-[44px] px-5 rounded-full bg-terracotta-500 text-white text-sm font-display font-semibold hover:bg-terracotta-600 focus:outline-none focus:ring-2 focus:ring-terracotta-400 focus:ring-offset-2"
              >
                Rechercher un bien
              </Link>
            ) : undefined
          }
        />
      ) : (
        <div className="space-y-8">
          {groups
            .filter((group) => group.items.length > 0)
            .map((group) => (
              <section key={group.id} aria-labelledby={`visites-${group.id}`} className="space-y-3">
                <div>
                  <h2 id={`visites-${group.id}`} className="font-display font-bold text-base text-sahel-900 flex items-center gap-2">
                    {group.title}
                    <Badge variant="sand" size="sm">{group.items.length}</Badge>
                  </h2>
                  <p className="text-xs text-sahel-600">{group.description}</p>
                </div>
                <div className="space-y-3">{group.items.map((rdv) => renderVisit(rdv, group))}</div>
              </section>
            ))}
        </div>
      )}

      <ConfirmActionModal
        isOpen={!!visitToCancel}
        title="Annuler ce rendez-vous ?"
        description={
          <>
            <p>
              Vous allez annuler la visite de <strong>« {visitToCancel?.annonceTitre} »</strong> prévue le{' '}
              <strong>{formatDateTimeFr(visitToCancel?.dateHeure)}</strong>.
            </p>
            <p className="text-xs text-charcoal-500 bg-sand-100 p-3 rounded-xl border border-sand-300">
              Cette action est définitive : pour reprogrammer une visite, une nouvelle demande sera nécessaire.
            </p>
          </>
        }
        confirmLabel="Confirmer l’annulation"
        cancelLabel="Conserver le rendez-vous"
        isLoading={isCancelling}
        errorMessage={cancelError}
        onConfirm={confirmCancellation}
        onClose={() => setVisitToCancel(null)}
      />
    </div>
  );
};
