import React from 'react';
import { Link } from 'react-router-dom';
import { Building, CheckCircle2, Circle, Clock3, ExternalLink, FileCheck2, FileWarning, Lock, XCircle } from 'lucide-react';
import {
  buildTransactionAmountLines,
  formatDateFr,
  formatFcfa,
  getBailMeta,
  getSequestreMeta,
  getTransactionNextStep,
  getTransactionSide,
  getTransactionTotal,
  isTransactionOpen,
} from '../../lib/dashboardModel';
import type { TransactionResponseDTO, UtilisateurDTO } from '../../types';
import { cn } from '../../lib/cn';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Card, CardBody } from '../ui/Card';
import { DashboardEmpty, DashboardError, DashboardListSkeleton, DashboardTabHeader } from './DashboardStates';

interface TransactionsTabProps {
  utilisateur: UtilisateurDTO;
  transactions: TransactionResponseDTO[];
  isLoading: boolean;
  isError: boolean;
  isRefetching: boolean;
  onRetry: () => void;
  onOpen: (tx: TransactionResponseDTO) => void;
}

type StepState = 'done' | 'current' | 'pending' | 'stopped';

/** Étapes dérivées uniquement des statuts réellement retournés par le backend. */
function buildSteps(tx: TransactionResponseDTO): { label: string; state: StepState }[] {
  const isClosedWithoutRelease = tx.statutSequestre === 'ANNULE' || tx.statutSequestre === 'EXPIRE' || tx.statutSequestre === 'REMBOURSE';
  const escrowDone = tx.statutSequestre === 'BLOQUE' || tx.statutSequestre === 'LIBERE';
  const signatureDone = tx.statutBail === 'SIGNE_COMPLET';
  const released = tx.statutSequestre === 'LIBERE';

  const escrow: StepState = escrowDone ? 'done' : isClosedWithoutRelease ? 'stopped' : 'current';
  const signature: StepState = signatureDone
    ? 'done'
    : isClosedWithoutRelease
      ? 'stopped'
      : escrowDone
        ? 'current'
        : 'pending';
  const closing: StepState = released ? 'done' : isClosedWithoutRelease ? 'stopped' : signatureDone ? 'current' : 'pending';

  return [
    { label: 'Séquestre', state: escrow },
    { label: 'Signatures', state: signature },
    { label: 'Clôture', state: closing },
  ];
}

const StepIcon: React.FC<{ state: StepState }> = ({ state }) => {
  if (state === 'done') return <CheckCircle2 className="w-4 h-4 text-emerald-600" aria-hidden="true" />;
  if (state === 'stopped') return <XCircle className="w-4 h-4 text-charcoal-400" aria-hidden="true" />;
  if (state === 'current') return <Clock3 className="w-4 h-4 text-terracotta-600" aria-hidden="true" />;
  return <Circle className="w-4 h-4 text-sand-400" aria-hidden="true" />;
};

const STEP_STATE_LABEL: Record<StepState, string> = {
  done: 'terminée',
  current: 'en cours',
  pending: 'à venir',
  stopped: 'interrompue',
};

const NEXT_STEP_TONE: Record<'action' | 'waiting' | 'done' | 'closed', string> = {
  action: 'bg-terracotta-50 border-terracotta-200 text-terracotta-900',
  waiting: 'bg-sand-100 border-sand-300 text-sahel-800',
  done: 'bg-emerald-50 border-emerald-200 text-emerald-900',
  closed: 'bg-gray-50 border-gray-200 text-gray-700',
};

export const TransactionsTab: React.FC<TransactionsTabProps> = ({
  utilisateur,
  transactions,
  isLoading,
  isError,
  isRefetching,
  onRetry,
  onOpen,
}) => {
  const sorted = [...transactions].sort(
    (a, b) => new Date(b.dateCreation).getTime() - new Date(a.dateCreation).getTime()
  );
  const open = sorted.filter(isTransactionOpen);
  const closed = sorted.filter((tx) => !isTransactionOpen(tx));

  const renderTransaction = (tx: TransactionResponseDTO) => {
    const isVente = tx.modeTransaction === 'VENTE';
    const side = getTransactionSide(tx, utilisateur);
    const total = formatFcfa(getTransactionTotal(tx));
    const lines = buildTransactionAmountLines(tx);
    const next = getTransactionNextStep(tx, utilisateur);
    const sequestre = getSequestreMeta(tx.statutSequestre);
    const bail = getBailMeta(tx.statutBail, isVente);
    const steps = buildSteps(tx);
    const expiration = tx.statutSequestre === 'INITIALISE' ? formatDateFr(tx.dateExpiration) : null;
    const createdAt = formatDateFr(tx.dateCreation);

    return (
      <Card key={tx.id} className="border border-sand-300 hover:shadow-warm-hover transition-all" data-testid="transaction-card">
        <CardBody className="p-4 sm:p-5 space-y-4">
          <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-4">
            <div className="space-y-2 min-w-0">
              <div className="flex items-start gap-2">
                <Building className="w-4 h-4 text-terracotta-500 shrink-0 mt-1" aria-hidden="true" />
                <h3 className="font-display font-bold text-sm sm:text-base text-sahel-900 break-words min-w-0">
                  {tx.annonceTitre || 'Bien non renseigné'}
                </h3>
              </div>
              <p className="text-xs text-sahel-600 break-words">
                {isVente ? 'Acheteur' : 'Locataire'} : <strong>{tx.locataireNomComplet || 'Non renseigné'}</strong>
                {' · '}
                {isVente ? 'Vendeur' : 'Propriétaire'} :{' '}
                <strong>{tx.proprietaireNomComplet || 'Non renseigné'}</strong>
                {side !== 'autre' && (
                  <span className="text-sahel-500"> — vous êtes {side === 'acquereur' ? (isVente ? 'l’acheteur' : 'le locataire') : (isVente ? 'le vendeur' : 'le propriétaire')}</span>
                )}
              </p>
              <p className="text-[11px] text-sahel-500">
                {isVente ? 'Vente' : 'Location'}
                {createdAt && <> · ouvert le {createdAt}</>}
                {expiration && <> · expire le {expiration}</>}
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2 lg:justify-end">
              <Badge variant={sequestre.variant}>{sequestre.label}</Badge>
              <Badge variant={bail.variant}>{bail.label}</Badge>
              {tx.statutDocumentBail === 'GENERE' && (
                <Badge variant="success" className="gap-1">
                  <FileCheck2 className="w-3 h-3" aria-hidden="true" /> PDF disponible
                </Badge>
              )}
              {tx.statutDocumentBail === 'A_GENERER' && <Badge variant="warning">PDF en préparation</Badge>}
              {tx.statutDocumentBail === 'ECHEC' && (
                <Badge variant="rose" className="gap-1">
                  <FileWarning className="w-3 h-3" aria-hidden="true" /> PDF en échec
                </Badge>
              )}
            </div>
          </div>

          <ol className="grid grid-cols-3 gap-2" aria-label="Avancement du dossier">
            {steps.map((step) => (
              <li
                key={step.label}
                className={cn(
                  'flex items-center gap-1.5 rounded-xl border px-2 py-2 text-[11px] sm:text-xs font-semibold min-w-0',
                  step.state === 'done' && 'border-emerald-200 bg-emerald-50 text-emerald-900',
                  step.state === 'current' && 'border-terracotta-300 bg-terracotta-50 text-terracotta-900',
                  step.state === 'pending' && 'border-sand-300 bg-white text-sahel-500',
                  step.state === 'stopped' && 'border-gray-200 bg-gray-50 text-gray-500'
                )}
              >
                <StepIcon state={step.state} />
                <span className="truncate">{step.label}</span>
                <span className="sr-only"> — {STEP_STATE_LABEL[step.state]}</span>
              </li>
            ))}
          </ol>

          <div className="grid grid-cols-1 md:grid-cols-[minmax(0,1fr)_auto] gap-4 md:items-end">
            <div className="space-y-3 min-w-0">
              <p className={cn('text-xs font-medium rounded-xl border px-3 py-2', NEXT_STEP_TONE[next.tone])}>
                {next.text}
              </p>
              <dl className="grid grid-cols-1 xs:grid-cols-2 gap-x-6 gap-y-1 text-xs">
                {lines.map((line) => (
                  <div key={line.label} className="flex items-baseline justify-between gap-3 border-b border-dashed border-sand-300 pb-1">
                    <dt className="text-sahel-600">{line.label}</dt>
                    <dd className={cn('font-semibold text-right', line.value === 'Non communiqués' ? 'text-sahel-500 italic' : 'text-sahel-900')}>
                      {line.value}
                    </dd>
                  </div>
                ))}
              </dl>
              <p className="text-sm font-bold text-terracotta-700">
                Total séquestre : {total ?? 'Non communiqué'}
              </p>
            </div>

            <div className="flex flex-col sm:flex-row md:flex-col gap-2">
              <Button variant="primary" size="sm" className="min-h-[44px]" onClick={() => onOpen(tx)}>
                Ouvrir le dossier
              </Button>
              <Link
                to={`/annonce/${tx.annonceId}`}
                className="inline-flex items-center justify-center gap-1.5 min-h-[44px] px-3 rounded-full text-xs font-display font-semibold text-sahel-700 hover:bg-sand-200 focus:outline-none focus:ring-2 focus:ring-sahel-400"
              >
                <ExternalLink className="w-3.5 h-3.5" aria-hidden="true" />
                Voir le bien
              </Link>
            </div>
          </div>
        </CardBody>
      </Card>
    );
  };

  return (
    <div className="space-y-6">
      <DashboardTabHeader
        title="Suivi des Transactions & Séquestres"
        description="Suivez les dépôts de séquestre, les signatures et les documents associés à chaque dossier."
      />

      {isLoading ? (
        <DashboardListSkeleton rows={2} />
      ) : isError ? (
        <DashboardError title="Impossible de charger vos transactions" onRetry={onRetry} isRetrying={isRefetching} />
      ) : sorted.length === 0 ? (
        <DashboardEmpty
          icon={Lock}
          title="Aucune transaction en cours"
          description="Les dossiers de location ou de vente initiés après une visite effectuée s’afficheront ici."
        />
      ) : (
        <div className="space-y-8">
          {open.length > 0 && (
            <section aria-labelledby="transactions-en-cours" className="space-y-3">
              <h2 id="transactions-en-cours" className="font-display font-bold text-base text-sahel-900 flex items-center gap-2">
                Dossiers en cours <Badge variant="sand" size="sm">{open.length}</Badge>
              </h2>
              <div className="space-y-3">{open.map(renderTransaction)}</div>
            </section>
          )}
          {closed.length > 0 && (
            <section aria-labelledby="transactions-clos" className="space-y-3">
              <h2 id="transactions-clos" className="font-display font-bold text-base text-sahel-900 flex items-center gap-2">
                Dossiers clos <Badge variant="sand" size="sm">{closed.length}</Badge>
              </h2>
              <div className="space-y-3">{closed.map(renderTransaction)}</div>
            </section>
          )}
        </div>
      )}
    </div>
  );
};
