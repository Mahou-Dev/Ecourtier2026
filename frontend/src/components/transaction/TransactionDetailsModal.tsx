import React, { useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { TransactionResponseDTO } from '../../types';
import { apiFetch, downloadPdfBail, genererPdfBail } from '../../services/api';
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
} from 'lucide-react';
import { useAuthStore } from '../../store/useAuthStore';

interface TransactionDetailsModalProps {
  isOpen: boolean;
  onClose: () => void;
  transaction: TransactionResponseDTO | null;
  onRefresh: () => void;
}

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
  const [isCancelling, setIsCancelling] = useState(false);
  const [isSigning, setIsSigning] = useState(false);
  const [isDownloadingPdf, setIsDownloadingPdf] = useState(false);
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
    isPaying || isCancelling || isSigning || isDownloadingPdf || isRegeneratingPdf || isRefunding;

  const isVente = transaction.modeTransaction === 'VENTE';
  const isLocataire = utilisateur?.id === transaction.locataireId;
  const isProprietaire = utilisateur?.id === transaction.proprietaireId;
  const isAdmin = utilisateur?.role === 'ADMIN';
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
      toast.success(`Séquestre remboursé avec succès ! Réf Wave : ${res.referenceRemboursementWave || 'Enregistré'}`);
      setShowRefundConfirm(false);
      setMotifRemboursementInput('');
      syncTransactionState({ ...transaction, ...res });
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Échec du remboursement du séquestre.'));
    } finally {
      setIsRefunding(false);
    }
  };

  // Caution Payment handler
  const handlePaiementCaution = async () => {
    if (isAnyActionPending) return;
    setIsPaying(true);
    try {
      const res = await apiFetch<TransactionResponseDTO>(
        `/api/v1/transactions/${transaction.id}/paiement-caution`,
        { method: 'POST' }
      );
      toast.success(`Paiement du séquestre effectué ! Réf Wave: ${res.referenceWave}`);
      syncTransactionState({ ...transaction, ...res });
    } catch (err: unknown) {
      if (getErrorStatus(err) === 403) {
        toast.error("Vous n'êtes pas autorisé à payer la caution pour cette transaction.");
      } else {
        toast.error(getErrorMessage(err, 'Échec du paiement de la caution'));
      }
    } finally {
      setIsPaying(false);
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

  // Lease Sign handler
  const handleSignerBail = async () => {
    if (isAnyActionPending) return;
    setIsSigning(true);
    try {
      const res = await apiFetch<TransactionResponseDTO>(
        `/api/v1/transactions/${transaction.id}/bail/signer`,
        { method: 'POST' }
      );
      toast.success('Félicitations, votre signature électronique a été enregistrée !');
      syncTransactionState({ ...transaction, ...res });
    } catch (err: unknown) {
      if (getErrorStatus(err) === 403) {
        toast.error("Accès refusé : vous ne faites pas partie des signataires de ce contrat.");
      } else {
        toast.error(getErrorMessage(err, 'Échec de la signature'));
      }
    } finally {
      setIsSigning(false);
    }
  };

  // PDF Download handler
  const handleDownloadPdf = async () => {
    if (isAnyActionPending) return;
    setIsDownloadingPdf(true);
    try {
      await downloadPdfBail(transaction.id);
      toast.success(isVente ? 'Téléchargement du récapitulatif de réservation démarré !' : 'Téléchargement du contrat de bail démarré !');
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

  // Badge helpers
  const renderSequestreBadge = () => {
    switch (transaction.statutSequestre) {
      case 'INITIALISE':
        return <Badge variant="warning">Séquestre : En attente de dépôt</Badge>;
      case 'BLOQUE':
        return <Badge variant="info">Séquestre : Bloqué sur compte sécurisé</Badge>;
      case 'LIBERE':
        return <Badge variant="success">Séquestre : Fonds libérés</Badge>;
      case 'REMBOURSE':
        return <Badge variant="neutral">Séquestre : Remboursé</Badge>;
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
        return <Badge variant="info">{labelPrefix} : Signé par l'{isVente ? 'acheteur' : 'locataire'}</Badge>;
      case 'SIGNE_PROPRIETAIRE':
        return <Badge variant="info">{labelPrefix} : Signé par le {isVente ? 'vendeur' : 'propriétaire'}</Badge>;
      case 'SIGNE_COMPLET':
        return <Badge variant="success">{labelPrefix} : Signé complet (2/2)</Badge>;
      default:
        return null;
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={handleModalClose} title={`Dossier de Transaction #${transaction.id.slice(0, 8)}`} size="lg">
      <div className="space-y-6">
        
        {/* Header Summary */}
        <div className="p-4 rounded-2xl bg-sand-50 border border-sand-200 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Building className="w-4 h-4 text-terracotta-500" />
              <h3 className="font-display font-bold text-base text-sahel-900">{transaction.annonceTitre}</h3>
            </div>
            <p className="text-xs text-sahel-600">
              {isVente ? (
                <>Acheteur : <span className="font-semibold text-sahel-900">{transaction.locataireNomComplet}</span> | Vendeur : <span className="font-semibold text-sahel-900">{transaction.proprietaireNomComplet || 'Agent'}</span></>
              ) : (
                <>Locataire : <span className="font-semibold text-sahel-900">{transaction.locataireNomComplet}</span> | Propriétaire : <span className="font-semibold text-sahel-900">{transaction.proprietaireNomComplet || 'Agent'}</span></>
              )}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {renderSequestreBadge()}
            {renderBailBadge()}
          </div>
        </div>

        {/* Step 1: Escrow Payment */}
        <div className="p-5 rounded-2xl border border-sand-300 bg-white space-y-4">
          <div className="flex items-center justify-between">
            <h4 className="font-display font-bold text-sm text-sahel-900 flex items-center gap-2">
              <CreditCard className="w-4 h-4 text-terracotta-500" />
              <span>{isVente ? '1. Séquestre Bancaire — Acompte de réservation (10 % du prix)' : '1. Séquestre Bancaire (Caution + 1er Loyer)'}</span>
            </h4>
            {transaction.statutSequestre === 'BLOQUE' || transaction.statutSequestre === 'LIBERE' ? (
              <span className="text-xs font-bold text-emerald-700 flex items-center gap-1">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" /> Dépôt validé
              </span>
            ) : transaction.statutSequestre === 'ANNULE' ? (
              <span className="text-xs font-bold text-sahel-600">Transaction annulée</span>
            ) : transaction.statutSequestre === 'EXPIRE' ? (
              <span className="text-xs font-bold text-rose-700">Délai expiré</span>
            ) : transaction.statutSequestre === 'REMBOURSE' ? (
              <span className="text-xs font-bold text-sahel-600">Séquestre remboursé</span>
            ) : (
              <span className="text-xs font-bold text-amber-700 flex items-center gap-1">
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
                <p className="text-terracotta-800 font-semibold">Total consigné en séquestre</p>
                <p className="font-display font-bold text-sm text-terracotta-700">
                  {(transaction.montantTotalSequestre ?? (transaction.montantCaution + transaction.fraisDossier)).toLocaleString('fr-FR')} FCFA
                </p>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
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
                <p className="text-terracotta-800 font-semibold">Total consigné</p>
                <p className="font-display font-bold text-sm text-terracotta-700">
                  {(transaction.montantTotalSequestre ?? (transaction.montantCaution + (transaction.montantPremierLoyer || 0) + transaction.fraisDossier)).toLocaleString('fr-FR')} FCFA
                </p>
              </div>
            </div>
          )}

          {transaction.referenceWave && (
            <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-900 flex items-center justify-between">
              <span>Référence Wave Sandbox : <strong>{transaction.referenceWave}</strong></span>
              {transaction.statutSequestre === 'BLOQUE' ? (
                <Badge variant="success">Séquestre bloqué</Badge>
              ) : transaction.statutSequestre === 'LIBERE' ? (
                <Badge variant="info">Fonds libérés</Badge>
              ) : transaction.statutSequestre === 'REMBOURSE' ? (
                <Badge variant="neutral">Remboursé</Badge>
              ) : transaction.statutSequestre === 'EXPIRE' ? (
                <Badge variant="rose">Expiré</Badge>
              ) : (
                <Badge variant="warning">{transaction.statutSequestre}</Badge>
              )}
            </div>
          )}

          {transaction.statutSequestre === 'LIBERE' && (
            <div className="p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-900 space-y-1">
              <div className="flex items-center gap-1.5 font-bold">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                <span>Fonds libérés avec succès</span>
              </div>
              <p>
                {isVente ? (
                  <>Montant versé au vendeur ({transaction.proprietaireNomComplet || 'Vendeur'}) : <strong>{(transaction.montantVerseProprietaire ?? transaction.montantCaution).toLocaleString('fr-FR')} FCFA</strong> (acompte de réservation de {transaction.acomptePourcentage || 10} %).</>
                ) : (
                  <>Montant versé au propriétaire ({transaction.proprietaireNomComplet || 'Bailleur'}) : <strong>{(transaction.montantVerseProprietaire ?? (transaction.montantCaution + (transaction.montantPremierLoyer || 0))).toLocaleString('fr-FR')} FCFA</strong> (caution + premier loyer).</>
                )}
              </p>
              <p className="text-emerald-700">
                Frais de plateforme conservés : <strong>{transaction.fraisDossier.toLocaleString('fr-FR')} FCFA</strong>.
              </p>
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
                  <Button
                    variant="primary"
                    className="w-full"
                    isLoading={isPaying}
                    disabled={isCancelling}
                    onClick={handlePaiementCaution}
                  >
                    Payer {(transaction.montantTotalSequestre ?? (transaction.montantCaution + (transaction.montantPremierLoyer || 0) + transaction.fraisDossier)).toLocaleString('fr-FR')} FCFA via Wave (Sandbox)
                  </Button>
                  <Button
                    variant="outline"
                    className="sm:w-auto shrink-0 border-sand-400 text-sahel-700 hover:bg-sand-100"
                    isLoading={isCancelling}
                    disabled={isPaying}
                    onClick={handleAnnulerTransaction}
                  >
                    Annuler la transaction
                  </Button>
                </div>
              ) : (
                <div className="space-y-2">
                  <p className="text-xs text-sahel-600 italic">
                    En attente du paiement du séquestre par {isVente ? "l'acheteur" : "le locataire"} ({transaction.locataireNomComplet}).
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
            <div className={`p-3.5 rounded-xl border text-xs space-y-1 ${
              transaction.statutSequestre === 'EXPIRE'
                ? 'bg-rose-50 border-rose-200 text-rose-900'
                : 'bg-sand-100 border-sand-300 text-sahel-900'
            }`}>
              <div className="flex items-center gap-1.5 font-bold">
                <AlertCircle className={`w-4 h-4 ${transaction.statutSequestre === 'EXPIRE' ? 'text-rose-600' : 'text-sahel-600'}`} />
                <span>{transaction.statutSequestre === 'EXPIRE' ? 'Transaction expirée' : 'Transaction annulée'}</span>
              </div>
              {transaction.dateCloture && (
                <p>Date de clôture : {new Date(transaction.dateCloture).toLocaleString('fr-FR')}</p>
              )}
              {transaction.motifCloture && (
                <p className="italic">Motif : {transaction.motifCloture}</p>
              )}
            </div>
          )}

          {transaction.statutSequestre === 'REMBOURSE' && (
            <div className="p-3.5 rounded-xl bg-sand-100 border border-sand-300 text-xs text-sahel-900 space-y-1.5">
              <div className="flex items-center gap-1.5 font-bold text-sahel-900">
                <RotateCcw className="w-4 h-4 text-terracotta-600" />
                <span>Séquestre Remboursé</span>
              </div>
              {transaction.referenceRemboursementWave ? (
                <div className="space-y-1">
                  <p>Référence Wave de remboursement : <strong>{transaction.referenceRemboursementWave}</strong></p>
                  <p>Montant remboursé : <strong>{(transaction.montantRembourse || transaction.montantTotalSequestre).toLocaleString('fr-FR')} FCFA</strong></p>
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
                  {transaction.motifRemboursement && (
                    <p className="italic">{transaction.motifRemboursement}</p>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Admin Refund Trigger Panel */}
          {isAdmin && transaction.statutSequestre === 'BLOQUE' && transaction.statutBail !== 'SIGNE_COMPLET' && (
            <div className="p-4 rounded-xl border border-terracotta-200 bg-terracotta-50/40 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-terracotta-800 flex items-center gap-1.5">
                  <RotateCcw className="w-3.5 h-3.5 text-terracotta-600" /> Action Administrateur : Rembourser le séquestre
                </span>
                {!showRefundConfirm && (
                  <Button
                    variant="outline"
                    size="sm"
                    className="border-terracotta-400 text-terracotta-700 hover:bg-terracotta-100 text-xs"
                    onClick={() => {
                      setMotifRemboursementInput('Annulation du dossier et restitution intégrale des fonds');
                      setShowRefundConfirm(true);
                    }}
                  >
                    Initier le remboursement
                  </Button>
                )}
              </div>

              {showRefundConfirm && (
                <div className="space-y-2 pt-2 border-t border-terracotta-200">
                  <label className="text-[11px] font-semibold text-sahel-800">
                    Motif obligatoire du remboursement :
                  </label>
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
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setShowRefundConfirm(false)}
                      disabled={isRefunding}
                    >
                      Annuler
                    </Button>
                    <Button
                      variant="primary"
                      size="sm"
                      className="bg-terracotta-600 hover:bg-terracotta-700 text-white"
                      isLoading={isRefunding}
                      onClick={handleRembourser}
                    >
                      Confirmer le remboursement Wave ({(transaction.montantTotalSequestre || 0).toLocaleString('fr-FR')} FCFA)
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Step 2: Lease or Sale Reservation Signature */}
        <div className="p-5 rounded-2xl border border-sand-300 bg-white space-y-4">
          <div className="flex items-center justify-between">
            <h4 className="font-display font-bold text-sm text-sahel-900 flex items-center gap-2">
              <FileCheck className="w-4 h-4 text-terracotta-500" />
              <span>{isVente ? "2. Signature Numérique du Récapitulatif de Réservation d'Achat" : "2. Signature Numérique du Contrat de Bail"}</span>
            </h4>
            {transaction.statutBail === 'SIGNE_COMPLET' ? (
              <span className="text-xs font-bold text-emerald-700 flex items-center gap-1">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" /> {isVente ? 'Document signé complet' : 'Bail signé complet'}
              </span>
            ) : (
              <span className="text-xs font-bold text-amber-700 flex items-center gap-1">
                <Clock className="w-4 h-4 text-amber-600" /> Signature en cours
              </span>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
            <div className={`p-3.5 rounded-xl border flex items-center justify-between ${
              transaction.dateSignatureLocataire ? 'bg-emerald-50 border-emerald-200 text-emerald-900' : 'bg-sand-50 border-sand-200 text-sahel-700'
            }`}>
              <div className="flex items-center gap-2">
                <UserCheck className={`w-4 h-4 ${transaction.dateSignatureLocataire ? 'text-emerald-600' : 'text-sahel-500'}`} />
                <div>
                  <p className="font-bold">{isVente ? 'Signature Acheteur' : 'Signature Locataire'}</p>
                  <p className="text-[11px]">{transaction.locataireNomComplet}</p>
                </div>
              </div>
              {transaction.dateSignatureLocataire ? (
                <span className="text-[11px] font-semibold text-emerald-700">
                  {new Date(transaction.dateSignatureLocataire).toLocaleDateString('fr-FR')}
                </span>
              ) : (
                <span className="text-[11px] text-amber-700 font-semibold">En attente</span>
              )}
            </div>

            <div className={`p-3.5 rounded-xl border flex items-center justify-between ${
              transaction.dateSignatureProprietaire ? 'bg-emerald-50 border-emerald-200 text-emerald-900' : 'bg-sand-50 border-sand-200 text-sahel-700'
            }`}>
              <div className="flex items-center gap-2">
                <UserCheck className={`w-4 h-4 ${transaction.dateSignatureProprietaire ? 'text-emerald-600' : 'text-sahel-500'}`} />
                <div>
                  <p className="font-bold">{isVente ? 'Signature Vendeur' : 'Signature Propriétaire'}</p>
                  <p className="text-[11px]">{transaction.proprietaireNomComplet || (isVente ? 'Vendeur' : 'Propriétaire')}</p>
                </div>
              </div>
              {transaction.dateSignatureProprietaire ? (
                <span className="text-[11px] font-semibold text-emerald-700">
                  {new Date(transaction.dateSignatureProprietaire).toLocaleDateString('fr-FR')}
                </span>
              ) : (
                <span className="text-[11px] text-amber-700 font-semibold">En attente</span>
              )}
            </div>
          </div>

          {/* Action to Sign */}
          {!isClosedTerminal && transaction.statutBail !== 'SIGNE_COMPLET' && (
            <div>
              {transaction.statutSequestre !== 'BLOQUE' ? (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-center gap-2">
                  <Clock className="w-4 h-4 text-amber-600 shrink-0" />
                  <span>
                    La signature numérique du {isVente ? "récapitulatif de réservation" : "contrat de bail"} est débloquée uniquement après confirmation du paiement du séquestre Wave (statut <strong>BLOQUE</strong>).
                  </span>
                </div>
              ) : ((isLocataire && !transaction.dateSignatureLocataire) || (isProprietaire && !transaction.dateSignatureProprietaire)) ? (
                <Button
                  variant="primary"
                  className="w-full"
                  isLoading={isSigning}
                  onClick={handleSignerBail}
                >
                  {isVente ? "Signer le récapitulatif de réservation" : "Signer le bail numérique"}
                </Button>
              ) : (isLocataire || isProprietaire) ? (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
                  <span>Votre signature est déjà enregistrée. En attente de la deuxième partie.</span>
                </div>
              ) : null}
            </div>
          )}

          {/* Action Download / Recovery PDF */}
          {transaction.statutBail === 'SIGNE_COMPLET' && (
            <div className="pt-2 space-y-3">
              {transaction.statutDocumentBail === 'ECHEC' && (
                <div
                  role="alert"
                  className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-900 space-y-1"
                >
                  <div className="flex items-center gap-1.5 font-bold">
                    <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                    <span>Génération du document PDF en échec</span>
                  </div>
                  {transaction.pdfBailErreur && (
                    <p className="text-[11px] text-rose-800">{transaction.pdfBailErreur}</p>
                  )}
                  {typeof transaction.pdfBailTentatives === 'number' && transaction.pdfBailTentatives > 0 && (
                    <p className="text-[11px] text-rose-700">
                      Tentatives effectuées : {transaction.pdfBailTentatives}
                    </p>
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
                      : "Télécharger le contrat de bail PDF (Horodaté & Numéroté)"}
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
