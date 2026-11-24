import React, { useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { TransactionResponseDTO } from '../../types';
import {
  apiFetch,
  downloadApercuPdfBail,
  downloadPdfBail,
  genererPdfBail,
  verifierStatutPaiement,
} from '../../services/api';
import { adminService } from '../../services/admin.service';
import { toast } from 'sonner';
import { getErrorMessage, getErrorStatus } from '../../lib/errors';
import {
  FileCheck,
  Download,
  CreditCard,
  CheckCircle2,
  Clock,
  AlertCircle,
  Building,
  UserCheck,
  RotateCcw,
  RefreshCw,
  ShieldAlert,
  Eye,
} from 'lucide-react';
import { useAuthStore } from '../../store/useAuthStore';

interface TransactionDetailsModalProps {
  isOpen: boolean;
  onClose: () => void;
  transaction: TransactionResponseDTO | null;
  onRefresh: () => void;
}

const isTrustedPayTechRedirectUrl = (rawUrl?: string): boolean => {
  if (!rawUrl) return false;
  try {
    const parsed = new URL(rawUrl);
    const host = parsed.hostname.toLowerCase();
    return (
      parsed.protocol === 'https:' &&
      !parsed.username &&
      !parsed.password &&
      (host === 'paytech.sn' || host.endsWith('.paytech.sn'))
    );
  } catch {
    return false;
  }
};

export const TransactionDetailsModal: React.FC<TransactionDetailsModalProps> = ({
  isOpen,
  onClose,
  transaction: transactionProp,
  onRefresh,
}) => {
  const { utilisateur } = useAuthStore();
  const queryClient = useQueryClient();
  const [overrideTx, setOverrideTx] = useState<TransactionResponseDTO | null>(null);

  const [isPaying, setIsPaying] = useState(false);
  const [isCheckingStatus, setIsCheckingStatus] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [isSigning, setIsSigning] = useState(false);
  const [isDownloadingPdf, setIsDownloadingPdf] = useState(false);
  const [isDownloadingPreview, setIsDownloadingPreview] = useState(false);
  const [isRegeneratingPdf, setIsRegeneratingPdf] = useState(false);
  const [isRefunding, setIsRefunding] = useState(false);
  const [refundConfirmTxId, setRefundConfirmTxId] = useState<string | null>(null);
  const [motifRemboursementInput, setMotifRemboursementInput] = useState('');

  const handleModalClose = () => {
    setOverrideTx(null);
    setRefundConfirmTxId(null);
    setMotifRemboursementInput('');
    onClose();
  };

  const transaction = useMemo(() => {
    if (!transactionProp) return overrideTx;
    if (overrideTx && overrideTx.id === transactionProp.id) {
      return { ...transactionProp, ...overrideTx };
    }
    return transactionProp;
  }, [transactionProp, overrideTx]);

  const syncTransactionState = (updated: TransactionResponseDTO) => {
    setOverrideTx(updated);
    queryClient.setQueryData<TransactionResponseDTO[]>(['my-transactions'], (old) =>
      Array.isArray(old) ? old.map((t) => (t.id === updated.id ? { ...t, ...updated } : t)) : old
    );
    queryClient.invalidateQueries({ queryKey: ['my-transactions'] });
    queryClient.invalidateQueries({ queryKey: ['admin-transactions'] });
    if (updated.annonceId) {
      queryClient.invalidateQueries({ queryKey: ['annonce', updated.annonceId] });
    }
    queryClient.invalidateQueries({ queryKey: ['my-annonces'] });
    queryClient.invalidateQueries({ queryKey: ['annonces'] });
    onRefresh();
  };

  if (!transaction) return null;

  const showRefundConfirm = refundConfirmTxId === transaction.id;
  const setShowRefundConfirm = (open: boolean) => {
    setRefundConfirmTxId(open ? transaction.id : null);
  };

  const isAnyActionPending =
    isPaying ||
    isCheckingStatus ||
    isCancelling ||
    isSigning ||
    isDownloadingPdf ||
    isDownloadingPreview ||
    isRegeneratingPdf ||
    isRefunding;

  const isVente = transaction.modeTransaction === 'VENTE';
  const isLocataire = utilisateur?.id === transaction.locataireId;
  const isProprietaire = utilisateur?.id === transaction.proprietaireId;
  const isAdmin = utilisateur?.role === 'ADMIN';
  const isSimulatedMode = transaction.modeSimulationPaiement !== false;
  const isPendingExternalPayment =
    transaction.statutSequestre === 'INITIALISE' &&
    transaction.statutPaiementExterne === 'EN_ATTENTE_CONFIRMATION';
  const isClosedTerminal =
    transaction.dossierActif === false ||
    transaction.statutSequestre === 'REMBOURSE' ||
    transaction.statutSequestre === 'ANNULE' ||
    transaction.statutSequestre === 'EXPIRE';

  const handleRembourser = async () => {
    if (isAnyActionPending) return;
    if (!motifRemboursementInput.trim()) {
      toast.error('Veuillez renseigner un motif de remboursement.');
      return;
    }
    setIsRefunding(true);
    try {
      const res = await adminService.rembourserTransaction(transaction.id, motifRemboursementInput.trim());
      toast.success(
        `Remboursement simulé enregistré (Réf : ${res.referenceRemboursementWave || 'Enregistré'} — aucun virement réel).`
      );
      setShowRefundConfirm(false);
      setMotifRemboursementInput('');
      syncTransactionState({ ...transaction, ...res });
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Échec du remboursement du dossier.'));
    } finally {
      setIsRefunding(false);
    }
  };

  // Payment Initiation handler (ESCROW-FE-001: never falsely claims confirmed when pending)
  const handlePaiementCaution = async () => {
    if (isAnyActionPending) return;
    setIsPaying(true);
    try {
      const res = await apiFetch<TransactionResponseDTO>(
        `/api/v1/transactions/${transaction.id}/paiement-caution`,
        { method: 'POST' }
      );
      syncTransactionState({ ...transaction, ...res });

      if (res.statutSequestre === 'INITIALISE' && res.statutPaiementExterne === 'EN_ATTENTE_CONFIRMATION') {
        if (res.redirectUrlPaiement && res.zeroDebitGaranti === true && isTrustedPayTechRedirectUrl(res.redirectUrlPaiement)) {
          toast.info('Redirection vers la page de paiement PayTech...');
          window.location.assign(res.redirectUrlPaiement);
        } else if (res.redirectUrlPaiement && res.zeroDebitGaranti === true) {
          toast.error('URL de redirection prestataire refusée (domaine PayTech HTTPS requis).');
        } else {
          toast.info(
            'Paiement PayTech Sandbox initialisé — en attente de confirmation authentifiée (redirection bloquée tant que le zéro débit réel n’est pas garanti).'
          );
        }
      } else if (res.statutSequestre === 'BLOQUE' && res.modeSimulationPaiement === true) {
        toast.success(
          `Paiement simulé localement enregistré (Réf : ${res.referenceWave} — aucune transaction financière réelle).`
        );
      } else if (res.statutSequestre === 'BLOQUE') {
        toast.success(`Paiement confirmé via PayTech (Réf : ${res.referenceWave}).`);
      } else {
        toast.info('Demande de paiement enregistrée. En attente de confirmation.');
      }
    } catch (err: unknown) {
      if (getErrorStatus(err) === 403) {
        toast.error("Vous n'êtes pas autorisé à initier le paiement pour cette transaction.");
      } else {
        toast.error(getErrorMessage(err, "Échec de l'initialisation du paiement"));
      }
    } finally {
      setIsPaying(false);
    }
  };

  // Check PayTech external payment status handler
  const handleVerifierStatutPaiement = async () => {
    if (isAnyActionPending) return;
    setIsCheckingStatus(true);
    try {
      const res = await verifierStatutPaiement(transaction.id);
      syncTransactionState({ ...transaction, ...res });
      if (res.statutSequestre === 'BLOQUE') {
        toast.success('Paiement confirmé par le prestataire !');
      } else if (res.statutPaiementExterne === 'ANNULE') {
        toast.warning('Le paiement a été annulé chez le prestataire.');
      } else if (res.statutPaiementExterne === 'ECHOUE') {
        toast.error('Le paiement a échoué chez le prestataire.');
      } else {
        toast.info('Paiement toujours en attente de confirmation par le prestataire.');
      }
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Impossible de vérifier le statut du paiement.'));
    } finally {
      setIsCheckingStatus(false);
    }
  };

  // Unpaid INITIALISE Transaction Cancellation handler
  const handleAnnulerTransaction = async () => {
    if (isAnyActionPending) return;
    setIsCancelling(true);
    try {
      const res = await apiFetch<TransactionResponseDTO>(
        `/api/v1/transactions/${transaction.id}/annuler`,
        {
          method: 'POST',
          body: JSON.stringify({ motif: 'Annulation demandée depuis le dossier de transaction' }),
        }
      );
      toast.success('Transaction annulée. Le bien est de nouveau disponible.');
      syncTransactionState({ ...transaction, ...res });
    } catch (err: unknown) {
      if (getErrorStatus(err) === 403) {
        toast.error("Vous n'êtes pas autorisé à annuler cette transaction.");
      } else {
        toast.error(getErrorMessage(err, "Échec de l'annulation de la transaction"));
      }
    } finally {
      setIsCancelling(false);
    }
  };

  // Lease Sign handler (parties only)
  const handleSignerBail = async () => {
    if (isAnyActionPending) return;
    setIsSigning(true);
    try {
      const res = await apiFetch<TransactionResponseDTO>(
        `/api/v1/transactions/${transaction.id}/bail/signer`,
        { method: 'POST' }
      );
      toast.success('Votre signature applicative horodatée (SHA-256) a été enregistrée.');
      syncTransactionState({ ...transaction, ...res });
    } catch (err: unknown) {
      if (getErrorStatus(err) === 403) {
        toast.error("Accès refusé : seules les parties contractantes peuvent signer ce contrat.");
      } else {
        toast.error(getErrorMessage(err, 'Échec de la signature'));
      }
    } finally {
      setIsSigning(false);
    }
  };

  // Pre-signature PDF Preview handler (ESCROW-SIGN-002)
  const handleDownloadPreviewPdf = async () => {
    if (isAnyActionPending) return;
    setIsDownloadingPreview(true);
    try {
      await downloadApercuPdfBail(transaction.id);
      toast.success("Téléchargement de l'aperçu pré-signature démarré.");
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Impossible de télécharger l'aperçu pré-signature."));
    } finally {
      setIsDownloadingPreview(false);
    }
  };

  // PDF Download handler
  const handleDownloadPdf = async () => {
    if (isAnyActionPending) return;
    setIsDownloadingPdf(true);
    try {
      await downloadPdfBail(transaction.id);
      toast.success(
        isVente
          ? 'Téléchargement du récapitulatif de réservation démarré !'
          : 'Téléchargement du contrat de bail démarré !'
      );
    } catch (err: unknown) {
      if (getErrorStatus(err) === 503) {
        setOverrideTx(null);
        toast.error(getErrorMessage(err, 'Document PDF temporairement indisponible. Relancez la génération.'));
        queryClient.invalidateQueries({ queryKey: ['my-transactions'] });
        onRefresh();
      } else {
        toast.error(getErrorMessage(err, 'Impossible de télécharger le document PDF'));
      }
    } finally {
      setIsDownloadingPdf(false);
    }
  };

  // Manual PDF generation/recovery handler
  const handleRegeneratePdf = async () => {
    if (isAnyActionPending) return;
    setIsRegeneratingPdf(true);
    try {
      const updated = await genererPdfBail(transaction.id);
      toast.success('Document PDF généré avec succès !');
      syncTransactionState({ ...transaction, ...updated });
    } catch (err: unknown) {
      setOverrideTx(null);
      toast.error(getErrorMessage(err, 'Échec de la génération du document PDF'));
      queryClient.invalidateQueries({ queryKey: ['my-transactions'] });
      onRefresh();
    } finally {
      setIsRegeneratingPdf(false);
    }
  };

  // Honest Badge helpers (ESCROW-ARCH-001)
  const renderSequestreBadge = () => {
    if (isPendingExternalPayment) {
      return <Badge variant="warning">Paiement PayTech : En attente de confirmation</Badge>;
    }
    switch (transaction.statutSequestre) {
      case 'INITIALISE':
        return <Badge variant="warning">Paiement : En attente</Badge>;
      case 'BLOQUE':
        return (
          <Badge variant="info">
            {isSimulatedMode ? 'Paiement confirmé (simulation locale)' : 'Paiement confirmé via PayTech'}
          </Badge>
        );
      case 'LIBERE':
        return <Badge variant="success">Libération logique effectuée</Badge>;
      case 'REMBOURSE':
        return <Badge variant="neutral">{isSimulatedMode ? 'Remboursement simulé' : 'Remboursé'}</Badge>;
      case 'ANNULE':
        return <Badge variant="neutral">Transaction : Annulée</Badge>;
      case 'EXPIRE':
        return <Badge variant="rose">Transaction : Expirée</Badge>;
      default:
        return null;
    }
  };

  const renderBailBadge = () => {
    const labelPrefix = isVente ? 'Réservation' : 'Bail';
    switch (transaction.statutBail) {
      case 'NON_SIGNE':
        return <Badge variant="neutral">{labelPrefix} : Non signé</Badge>;
      case 'SIGNE_LOCATAIRE':
        return <Badge variant="info">{labelPrefix} : Signé par l&apos;{isVente ? 'acheteur' : 'locataire'}</Badge>;
      case 'SIGNE_PROPRIETAIRE':
        return <Badge variant="info">{labelPrefix} : Signé par le {isVente ? 'vendeur' : 'propriétaire'}</Badge>;
      case 'SIGNE_COMPLET':
        return <Badge variant="success">{labelPrefix} : Signé complet (2/2)</Badge>;
      default:
        return null;
    }
  };

  const totalAmount =
    transaction.montantTotalSequestre ??
    transaction.montantCaution + (transaction.montantPremierLoyer || 0) + transaction.fraisDossier;

  return (
    <Modal isOpen={isOpen} onClose={handleModalClose} title={`Dossier de Transaction #${transaction.id.slice(0, 8)}`} size="lg">
      <div className="space-y-6">
        {/* Header Summary */}
        <div className="p-3.5 sm:p-4 rounded-2xl bg-sand-50 border border-sand-200 flex flex-col md:flex-row md:items-center justify-between gap-3 sm:gap-4">
          <div className="min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <Building className="w-4 h-4 text-terracotta-500 shrink-0" />
              <h3 className="font-display font-bold text-base text-sahel-900 break-words">{transaction.annonceTitre}</h3>
            </div>
            <p className="text-xs text-sahel-600 break-words">
              {isVente ? (
                <>
                  Acheteur : <span className="font-semibold text-sahel-900">{transaction.locataireNomComplet}</span> | Vendeur :{' '}
                  <span className="font-semibold text-sahel-900">{transaction.proprietaireNomComplet || 'Agent'}</span>
                </>
              ) : (
                <>
                  Locataire : <span className="font-semibold text-sahel-900">{transaction.locataireNomComplet}</span> | Propriétaire :{' '}
                  <span className="font-semibold text-sahel-900">{transaction.proprietaireNomComplet || 'Agent'}</span>
                </>
              )}
            </p>
          </div>
          <div className="flex flex-wrap gap-2 shrink-0">
            {renderSequestreBadge()}
            {renderBailBadge()}
          </div>
        </div>

        {/* Step 1: Payment via PayTech / Local Simulation */}
        <div className="p-4 sm:p-5 rounded-2xl border border-sand-300 bg-white space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="font-display font-bold text-sm text-sahel-900 flex items-center gap-2">
              <CreditCard className="w-4 h-4 text-terracotta-500 shrink-0" />
              <span>
                {isVente
                  ? '1. Paiement via PayTech (Wave) — Acompte de réservation (10 % du prix)'
                  : '1. Paiement via PayTech (Wave) — Caution + 1er Loyer'}
              </span>
            </h4>
            {transaction.statutSequestre === 'BLOQUE' || transaction.statutSequestre === 'LIBERE' ? (
              <span className="text-xs font-bold text-emerald-700 flex items-center gap-1 shrink-0">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" />{' '}
                {isSimulatedMode ? 'Paiement simulé confirmé' : 'Paiement confirmé'}
              </span>
            ) : isPendingExternalPayment ? (
              <span className="text-xs font-bold text-amber-700 flex items-center gap-1 shrink-0">
                <Clock className="w-4 h-4 text-amber-600" /> En attente de confirmation PayTech
              </span>
            ) : transaction.statutSequestre === 'ANNULE' ? (
              <span className="text-xs font-bold text-sahel-600 shrink-0">Transaction annulée</span>
            ) : transaction.statutSequestre === 'EXPIRE' ? (
              <span className="text-xs font-bold text-rose-700 shrink-0">Délai expiré</span>
            ) : transaction.statutSequestre === 'REMBOURSE' ? (
              <span className="text-xs font-bold text-sahel-600 shrink-0">
                {isSimulatedMode ? 'Remboursement simulé' : 'Remboursé'}
              </span>
            ) : (
              <span className="text-xs font-bold text-amber-700 flex items-center gap-1 shrink-0">
                <Clock className="w-4 h-4 text-amber-600" /> En attente de paiement
              </span>
            )}
          </div>

          {isVente ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
              <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
                <p className="text-sahel-600">Acompte de réservation ({transaction.acomptePourcentage || 10} % du prix)</p>
                <p className="font-display font-bold text-sm text-sahel-900">{transaction.montantCaution.toLocaleString('fr-FR')} FCFA</p>
              </div>
              <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
                <p className="text-sahel-600">Frais de dossier plateforme</p>
                <p className="font-display font-bold text-sm text-sahel-900">{transaction.fraisDossier.toLocaleString('fr-FR')} FCFA</p>
              </div>
              <div className="p-3 rounded-xl bg-terracotta-50/50 border border-terracotta-200">
                <p className="text-terracotta-800 font-semibold">Total à régler</p>
                <p className="font-display font-bold text-sm text-terracotta-700">
                  {(transaction.montantTotalSequestre ?? transaction.montantCaution + transaction.fraisDossier).toLocaleString('fr-FR')} FCFA
                </p>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-1 xs:grid-cols-2 md:grid-cols-4 gap-3 text-xs">
              <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
                <p className="text-sahel-600">Caution</p>
                <p className="font-display font-bold text-sm text-sahel-900">{transaction.montantCaution.toLocaleString('fr-FR')} FCFA</p>
              </div>
              <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
                <p className="text-sahel-600">Premier loyer</p>
                <p className="font-display font-bold text-sm text-sahel-900">{(transaction.montantPremierLoyer || 0).toLocaleString('fr-FR')} FCFA</p>
              </div>
              <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
                <p className="text-sahel-600">Frais de dossier</p>
                <p className="font-display font-bold text-sm text-sahel-900">{transaction.fraisDossier.toLocaleString('fr-FR')} FCFA</p>
              </div>
              <div className="p-3 rounded-xl bg-terracotta-50/50 border border-terracotta-200">
                <p className="text-terracotta-800 font-semibold">Total du dossier</p>
                <p className="font-display font-bold text-sm text-terracotta-700">{totalAmount.toLocaleString('fr-FR')} FCFA</p>
              </div>
            </div>
          )}

          {/* Pending PayTech Sandbox Warning & Polling Controls (ESCROW-FE-001) */}
          {isPendingExternalPayment && (
            <div
              data-testid="paytech-pending-panel"
              className="p-3.5 rounded-xl bg-amber-50 border border-amber-300 text-xs text-amber-950 space-y-2.5"
            >
              <div className="flex items-start gap-2">
                <ShieldAlert className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" />
                <div className="space-y-1">
                  <p className="font-bold">Paiement PayTech (Wave Sandbox) en attente de confirmation</p>
                  <p>
                    La session PayTech a été initialisée mais <strong>le paiement n&apos;est pas encore confirmé</strong>. Le dossier ne sera
                    débloqué qu&apos;après réception d&apos;une notification IPN authentifiée par le backend.
                  </p>
                  {transaction.refCommandPaytech && (
                    <p className="font-mono text-[11px] break-all">
                      Référence commande PayTech : <strong>{transaction.refCommandPaytech}</strong>
                    </p>
                  )}
                  {transaction.zeroDebitGaranti !== true && (
                    <p className="text-[11px] text-amber-800 font-medium">
                      Avertissement sécurité Sandbox : la documentation PayTech indique qu&apos;un paiement en mode test (env=test) peut
                      débiter 100 à 150 FCFA réels. La redirection automatique est désactivée par précaution.
                    </p>
                  )}
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  className="border-amber-500 text-amber-900 hover:bg-amber-100"
                  isLoading={isCheckingStatus}
                  disabled={isAnyActionPending && !isCheckingStatus}
                  onClick={handleVerifierStatutPaiement}
                >
                  <RefreshCw className="w-3.5 h-3.5 mr-1.5" />
                  Vérifier le statut du paiement
                </Button>
              </div>
            </div>
          )}

          {transaction.referenceWave && (
            <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-900 flex flex-wrap items-center justify-between gap-2">
              <span className="break-all">
                {isSimulatedMode ? 'Référence de simulation locale : ' : 'Référence paiement PayTech (Wave) : '}
                <strong>{transaction.referenceWave}</strong>
              </span>
              {transaction.statutSequestre === 'BLOQUE' ? (
                <Badge variant="success">
                  {isSimulatedMode ? 'Paiement simulé (zéro débit)' : 'Paiement confirmé'}
                </Badge>
              ) : transaction.statutSequestre === 'LIBERE' ? (
                <Badge variant="info">Libération logique</Badge>
              ) : transaction.statutSequestre === 'REMBOURSE' ? (
                <Badge variant="neutral">{isSimulatedMode ? 'Remboursement simulé' : 'Remboursé'}</Badge>
              ) : transaction.statutSequestre === 'EXPIRE' ? (
                <Badge variant="rose">Expiré</Badge>
              ) : (
                <Badge variant="warning">{transaction.statutSequestre}</Badge>
              )}
            </div>
          )}

          {transaction.statutSequestre === 'LIBERE' && (
            <div className="p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-900 space-y-1.5">
              <div className="flex items-center gap-1.5 font-bold">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                <span>Libération logique du dossier effectuée</span>
              </div>
              <p>
                {isVente ? (
                  <>
                    Part destinée au vendeur ({transaction.proprietaireNomComplet || 'Vendeur'}) :{' '}
                    <strong>{(transaction.montantVerseProprietaire ?? transaction.montantCaution).toLocaleString('fr-FR')} FCFA</strong>{' '}
                    (acompte de réservation de {transaction.acomptePourcentage || 10} %).
                  </>
                ) : (
                  <>
                    Part destinée au propriétaire ({transaction.proprietaireNomComplet || 'Bailleur'}) :{' '}
                    <strong>
                      {(transaction.montantVerseProprietaire ?? transaction.montantCaution + (transaction.montantPremierLoyer || 0)).toLocaleString(
                        'fr-FR'
                      )}{' '}
                      FCFA
                    </strong>{' '}
                    (caution + premier loyer).
                  </>
                )}
              </p>
              <p className="text-emerald-700">
                Frais de dossier plateforme : <strong>{transaction.fraisDossier.toLocaleString('fr-FR')} FCFA</strong>.
              </p>
              {transaction.referenceTransfertProprietaire && (
                <p className="text-[11px] font-mono text-emerald-800 bg-white/70 px-2.5 py-1.5 rounded-lg border border-emerald-200">
                  Transfert propriétaire : <strong>{transaction.statutTransfertProprietaire || 'TRANSFERT_SIMULE'}</strong> — Réf :{' '}
                  <strong>{transaction.referenceTransfertProprietaire}</strong> (simulation locale sans virement réel)
                </p>
              )}
            </div>
          )}

          {transaction.statutSequestre === 'INITIALISE' && (
            <div className="space-y-2">
              {transaction.dateExpiration && (
                <p className="text-[11px] text-amber-700">
                  Échéance de paiement : <strong>{new Date(transaction.dateExpiration).toLocaleString('fr-FR')}</strong>
                </p>
              )}
              {isLocataire ? (
                <div className="flex flex-col sm:flex-row gap-2">
                  {!isPendingExternalPayment && (
                    <Button
                      variant="primary"
                      className="w-full"
                      isLoading={isPaying}
                      disabled={isCancelling || isCheckingStatus}
                      onClick={handlePaiementCaution}
                    >
                      Initier le paiement ({totalAmount.toLocaleString('fr-FR')} FCFA via PayTech / Wave)
                    </Button>
                  )}
                  <Button
                    variant="outline"
                    className="sm:w-auto shrink-0 border-sand-400 text-sahel-700 hover:bg-sand-100"
                    isLoading={isCancelling}
                    disabled={isPaying || isCheckingStatus}
                    onClick={handleAnnulerTransaction}
                  >
                    Annuler la transaction
                  </Button>
                </div>
              ) : (
                <div className="space-y-2">
                  <p className="text-xs text-sahel-600 italic">
                    En attente du règlement par {isVente ? "l'acheteur" : 'le locataire'} ({transaction.locataireNomComplet}).
                  </p>
                  {isAdmin && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="border-sand-400 text-sahel-700 hover:bg-sand-100"
                      isLoading={isCancelling}
                      onClick={handleAnnulerTransaction}
                    >
                      Annuler la transaction (Admin)
                    </Button>
                  )}
                </div>
              )}
            </div>
          )}

          {(transaction.statutSequestre === 'ANNULE' || transaction.statutSequestre === 'EXPIRE') && (
            <div
              className={`p-3.5 rounded-xl border text-xs space-y-1 ${
                transaction.statutSequestre === 'EXPIRE'
                  ? 'bg-rose-50 border-rose-200 text-rose-900'
                  : 'bg-sand-100 border-sand-300 text-sahel-900'
              }`}
            >
              <div className="flex items-center gap-1.5 font-bold">
                <AlertCircle className={`w-4 h-4 ${transaction.statutSequestre === 'EXPIRE' ? 'text-rose-600' : 'text-sahel-600'}`} />
                <span>{transaction.statutSequestre === 'EXPIRE' ? 'Transaction expirée' : 'Transaction annulée'}</span>
              </div>
              {transaction.dateCloture && <p>Date de clôture : {new Date(transaction.dateCloture).toLocaleString('fr-FR')}</p>}
              {transaction.motifCloture && <p className="italic">Motif : {transaction.motifCloture}</p>}
            </div>
          )}

          {transaction.statutSequestre === 'REMBOURSE' && (
            <div className="p-3.5 rounded-xl bg-sand-100 border border-sand-300 text-xs text-sahel-900 space-y-1.5">
              <div className="flex items-center gap-1.5 font-bold text-sahel-900">
                <RotateCcw className="w-4 h-4 text-terracotta-600" />
                <span>{isSimulatedMode ? 'Remboursement simulé' : 'Paiement remboursé'}</span>
              </div>
              {transaction.referenceRemboursementWave ? (
                <div className="space-y-1">
                  <p>
                    Référence de remboursement : <strong>{transaction.referenceRemboursementWave}</strong>
                  </p>
                  <p>
                    Montant remboursé :{' '}
                    <strong>{(transaction.montantRembourse || transaction.montantTotalSequestre).toLocaleString('fr-FR')} FCFA</strong>
                  </p>
                  {transaction.dateRemboursement && (
                    <p className="text-sahel-600">Date : {new Date(transaction.dateRemboursement).toLocaleString('fr-FR')}</p>
                  )}
                  {transaction.motifRemboursement && (
                    <p className="italic text-sahel-600">Motif : {transaction.motifRemboursement}</p>
                  )}
                </div>
              ) : (
                <div className="space-y-1 text-sahel-700">
                  <p className="font-semibold">Clôture administrative / régularisation</p>
                  {transaction.motifRemboursement && <p className="italic">{transaction.motifRemboursement}</p>}
                </div>
              )}
            </div>
          )}

          {/* Admin Refund Trigger Panel */}
          {isAdmin && transaction.statutSequestre === 'BLOQUE' && transaction.statutBail !== 'SIGNE_COMPLET' && (
            <div className="p-4 rounded-xl border border-terracotta-200 bg-terracotta-50/40 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-terracotta-800 flex items-center gap-1.5">
                  <RotateCcw className="w-3.5 h-3.5 text-terracotta-600" /> Action Administrateur : Remboursement simulé
                </span>
                {!showRefundConfirm && (
                  <Button
                    variant="outline"
                    size="sm"
                    className="border-terracotta-400 text-terracotta-700 hover:bg-terracotta-100 text-xs"
                    onClick={() => {
                      setMotifRemboursementInput('Annulation du dossier et restitution intégrale simulée');
                      setShowRefundConfirm(true);
                    }}
                  >
                    Initier le remboursement
                  </Button>
                )}
              </div>

              {showRefundConfirm && (
                <div className="space-y-2 pt-2 border-t border-terracotta-200">
                  <label className="text-[11px] font-semibold text-sahel-800">Motif obligatoire du remboursement :</label>
                  <input
                    type="text"
                    value={motifRemboursementInput}
                    onChange={(e) => setMotifRemboursementInput(e.target.value)}
                    placeholder="Ex: Désistement d'un commun accord..."
                    className="w-full text-xs px-3 py-2 rounded-lg border border-sand-300 focus:outline-none focus:ring-1 focus:ring-terracotta-500 bg-white"
                    maxLength={500}
                    disabled={isRefunding}
                  />
                  <div className="flex items-center justify-end gap-2 pt-1">
                    <Button variant="ghost" size="sm" onClick={() => setShowRefundConfirm(false)} disabled={isRefunding}>
                      Annuler
                    </Button>
                    <Button
                      variant="primary"
                      size="sm"
                      className="bg-terracotta-600 hover:bg-terracotta-700 text-white"
                      isLoading={isRefunding}
                      onClick={handleRembourser}
                    >
                      Confirmer le remboursement simulé ({(transaction.montantTotalSequestre || 0).toLocaleString('fr-FR')} FCFA)
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Honest regulatory notice (ESCROW-ARCH-001) */}
          <p className="text-[11px] text-sahel-500 italic pt-1 border-t border-sand-100">
            Information : E-Courtier n&apos;est pas un établissement de séquestre bancaire réglementé. Le règlement transite via PayTech
            (Wave) en environnement de test ou par simulation locale sans débit réel.
          </p>
        </div>

        {/* Step 2: Lease or Sale Reservation Signature */}
        <div className="p-4 sm:p-5 rounded-2xl border border-sand-300 bg-white space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="font-display font-bold text-sm text-sahel-900 flex items-center gap-2">
              <FileCheck className="w-4 h-4 text-terracotta-500 shrink-0" />
              <span>
                {isVente
                  ? "2. Validation & Signature du Récapitulatif de Réservation d'Achat"
                  : '2. Validation & Signature du Contrat de Bail'}
              </span>
            </h4>
            {transaction.statutBail === 'SIGNE_COMPLET' ? (
              <span className="text-xs font-bold text-emerald-700 flex items-center gap-1 shrink-0">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" /> {isVente ? 'Document signé complet' : 'Bail signé complet'}
              </span>
            ) : (
              <span className="text-xs font-bold text-amber-700 flex items-center gap-1 shrink-0">
                <Clock className="w-4 h-4 text-amber-600" /> Signature en cours
              </span>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-4 text-xs">
            <div
              className={`p-3.5 rounded-xl border flex flex-wrap items-center justify-between gap-2 ${
                transaction.dateSignatureLocataire
                  ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                  : 'bg-sand-50 border-sand-200 text-sahel-700'
              }`}
            >
              <div className="flex items-center gap-2 min-w-0">
                <UserCheck className={`w-4 h-4 shrink-0 ${transaction.dateSignatureLocataire ? 'text-emerald-600' : 'text-sahel-500'}`} />
                <div className="min-w-0">
                  <p className="font-bold">{isVente ? 'Signature Acheteur' : 'Signature Locataire'}</p>
                  <p className="text-[11px] truncate">{transaction.locataireNomComplet}</p>
                </div>
              </div>
              {transaction.dateSignatureLocataire ? (
                <span className="text-[11px] font-semibold text-emerald-700 shrink-0">
                  {new Date(transaction.dateSignatureLocataire).toLocaleDateString('fr-FR')}
                </span>
              ) : (
                <span className="text-[11px] text-amber-700 font-semibold shrink-0">En attente</span>
              )}
            </div>

            <div
              className={`p-3.5 rounded-xl border flex flex-wrap items-center justify-between gap-2 ${
                transaction.dateSignatureProprietaire
                  ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                  : 'bg-sand-50 border-sand-200 text-sahel-700'
              }`}
            >
              <div className="flex items-center gap-2 min-w-0">
                <UserCheck
                  className={`w-4 h-4 shrink-0 ${transaction.dateSignatureProprietaire ? 'text-emerald-600' : 'text-sahel-500'}`}
                />
                <div className="min-w-0">
                  <p className="font-bold">{isVente ? 'Signature Vendeur' : 'Signature Propriétaire'}</p>
                  <p className="text-[11px] truncate">{transaction.proprietaireNomComplet || (isVente ? 'Vendeur' : 'Propriétaire')}</p>
                </div>
              </div>
              {transaction.dateSignatureProprietaire ? (
                <span className="text-[11px] font-semibold text-emerald-700 shrink-0">
                  {new Date(transaction.dateSignatureProprietaire).toLocaleDateString('fr-FR')}
                </span>
              ) : (
                <span className="text-[11px] text-amber-700 font-semibold shrink-0">En attente</span>
              )}
            </div>
          </div>

          {/* Integrity SHA-256 display & pre-signature preview (ESCROW-SIGN-002) */}
          {transaction.bailDocumentSha256 && (
            <div className="p-2.5 rounded-xl bg-sand-50 border border-sand-200 text-[11px] text-sahel-700 space-y-1">
              <p className="font-mono break-all">
                Empreinte SHA-256 des termes : <strong>{transaction.bailDocumentSha256}</strong>
              </p>
              <p className="italic text-sahel-500">
                Horodatage et empreinte SHA-256 applicatifs internes (ne constituent pas une signature électronique qualifiée certifiée).
              </p>
            </div>
          )}

          {transaction.statutSequestre === 'BLOQUE' && (
            <div className="flex justify-start">
              <Button
                variant="outline"
                size="sm"
                className="text-xs border-sand-400 text-sahel-800 hover:bg-sand-100"
                isLoading={isDownloadingPreview}
                disabled={isAnyActionPending && !isDownloadingPreview}
                onClick={handleDownloadPreviewPdf}
              >
                <Eye className="w-3.5 h-3.5 mr-1.5" />
                Consulter l&apos;aperçu PDF pré-signature
              </Button>
            </div>
          )}

          {/* Action to Sign (ESCROW-SIGN-001: parties only, never ADMIN) */}
          {!isClosedTerminal && transaction.statutBail !== 'SIGNE_COMPLET' && (
            <div>
              {transaction.statutSequestre !== 'BLOQUE' ? (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-center gap-2">
                  <Clock className="w-4 h-4 text-amber-600 shrink-0" />
                  <span>
                    La signature du {isVente ? 'récapitulatif de réservation' : 'contrat de bail'} est débloquée uniquement après
                    confirmation authentifiée du paiement.
                  </span>
                </div>
              ) : (isLocataire && !transaction.dateSignatureLocataire) || (isProprietaire && !transaction.dateSignatureProprietaire) ? (
                <Button variant="primary" className="w-full" isLoading={isSigning} onClick={handleSignerBail}>
                  {isVente ? 'Signer le récapitulatif de réservation' : 'Signer le bail numérique'}
                </Button>
              ) : isLocataire || isProprietaire ? (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
                  <span>Votre signature est déjà enregistrée. En attente de la deuxième partie.</span>
                </div>
              ) : isAdmin ? (
                <div className="p-3 rounded-xl bg-sand-100 border border-sand-300 text-xs text-sahel-800 flex items-center gap-2">
                  <ShieldAlert className="w-4 h-4 text-sahel-600 shrink-0" />
                  <span>Contrôle sécurité : un administrateur ne peut jamais signer le contrat à la place des parties.</span>
                </div>
              ) : null}
            </div>
          )}

          {/* Action Download / Recovery PDF */}
          {transaction.statutBail === 'SIGNE_COMPLET' && (
            <div className="pt-2 space-y-3">
              {transaction.statutDocumentBail === 'ECHEC' && (
                <div role="alert" className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-900 space-y-1">
                  <div className="flex items-center gap-1.5 font-bold">
                    <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                    <span>Génération du document PDF en échec</span>
                  </div>
                  {transaction.pdfBailErreur && <p className="text-[11px] text-rose-800">{transaction.pdfBailErreur}</p>}
                  {typeof transaction.pdfBailTentatives === 'number' && transaction.pdfBailTentatives > 0 && (
                    <p className="text-[11px] text-rose-700">Tentatives effectuées : {transaction.pdfBailTentatives}</p>
                  )}
                </div>
              )}

              {transaction.statutDocumentBail === 'A_GENERER' && (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-center gap-2">
                  <Clock className="w-4 h-4 text-amber-600 shrink-0" />
                  <span>Le document PDF est en attente de génération. Vous pouvez déclencher sa création immédiatement.</span>
                </div>
              )}

              {transaction.statutDocumentBail === 'GENERE' && (
                <div className="p-2.5 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-900 flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>Document PDF généré et prêt au téléchargement.</span>
                </div>
              )}

              <div className="flex flex-col sm:flex-row gap-2">
                <Button
                  variant="outline"
                  className="w-full border-terracotta-500 text-terracotta-700 hover:bg-terracotta-50 flex items-center justify-center gap-2"
                  isLoading={isDownloadingPdf}
                  disabled={isRegeneratingPdf}
                  onClick={handleDownloadPdf}
                >
                  <Download className="w-4 h-4 text-terracotta-600" />
                  <span>
                    {isVente
                      ? "Télécharger le récapitulatif de réservation d'achat (PDF horodaté)"
                      : 'Télécharger le contrat de bail PDF (Horodaté & Numéroté)'}
                  </span>
                </Button>

                {(transaction.statutDocumentBail === 'ECHEC' ||
                  transaction.statutDocumentBail === 'A_GENERER' ||
                  (!transaction.pdfBailPath && transaction.statutDocumentBail !== 'GENERE')) && (
                  <Button
                    variant="secondary"
                    className="sm:w-auto shrink-0 flex items-center justify-center gap-2"
                    isLoading={isRegeneratingPdf}
                    disabled={isDownloadingPdf}
                    onClick={handleRegeneratePdf}
                  >
                    <RefreshCw className="w-4 h-4" />
                    <span>Régénérer le PDF</span>
                  </Button>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Close Modal Button */}
        <div className="flex justify-end pt-2">
          <Button variant="ghost" onClick={handleModalClose}>
            Fermer
          </Button>
        </div>
      </div>
    </Modal>
  );
};
