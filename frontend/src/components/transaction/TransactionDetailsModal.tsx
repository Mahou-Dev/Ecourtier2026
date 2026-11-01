import React, { useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { TransactionResponseDTO } from '../../types';
import { apiFetch, downloadPdfBail } from '../../services/api';
import { toast } from 'sonner';
import { 
  FileCheck, 
  Download, 
  CreditCard, 
  CheckCircle2, 
  Clock, 
  AlertCircle,
  Building,
  UserCheck
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
  transaction,
  onRefresh,
}) => {
  const { utilisateur } = useAuthStore();
  const [isPaying, setIsPaying] = useState(false);
  const [isSigning, setIsSigning] = useState(false);
  const [isDownloadingPdf, setIsDownloadingPdf] = useState(false);

  if (!transaction) return null;

  const isVente = transaction.modeTransaction === 'VENTE';
  const isLocataire = utilisateur?.id === transaction.locataireId;
  const isProprietaire = utilisateur?.id === transaction.proprietaireId;

  // Caution Payment handler
  const handlePaiementCaution = async () => {
    setIsPaying(true);
    try {
      const res = await apiFetch<TransactionResponseDTO>(
        `/api/v1/transactions/${transaction.id}/paiement-caution`,
        { method: 'POST' }
      );
      toast.success(`Paiement du séquestre effectué ! Réf Wave: ${res.referenceWave}`);
      onRefresh();
    } catch (err: any) {
      if (err.status === 403) {
        toast.error("Vous n'êtes pas autorisé à payer la caution pour cette transaction.");
      } else {
        toast.error(err.message || 'Échec du paiement de la caution');
      }
    } finally {
      setIsPaying(false);
    }
  };

  // Lease Sign handler
  const handleSignerBail = async () => {
    setIsSigning(true);
    try {
      await apiFetch<TransactionResponseDTO>(
        `/api/v1/transactions/${transaction.id}/bail/signer`,
        { method: 'POST' }
      );
      toast.success('Félicitations, votre signature électronique a été enregistrée !');
      onRefresh();
    } catch (err: any) {
      if (err.status === 403) {
        toast.error("Accès refusé : vous ne faites pas partie des signataires de ce contrat.");
      } else {
        toast.error(err.message || 'Échec de la signature');
      }
    } finally {
      setIsSigning(false);
    }
  };

  // PDF Download handler
  const handleDownloadPdf = async () => {
    setIsDownloadingPdf(true);
    try {
      await downloadPdfBail(transaction.id);
      toast.success(isVente ? 'Téléchargement du récapitulatif de réservation démarré !' : 'Téléchargement du contrat de bail démarré !');
    } catch (err: any) {
      toast.error(err.message || 'Impossible de télécharger le document PDF');
    } finally {
      setIsDownloadingPdf(false);
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
    <Modal isOpen={isOpen} onClose={onClose} title={`Dossier de Transaction #${transaction.id.slice(0, 8)}`} size="lg">
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
              <Badge variant="success">Séquestre bloqué</Badge>
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
            <div>
              {isLocataire ? (
                <Button
                  variant="primary"
                  className="w-full"
                  isLoading={isPaying}
                  onClick={handlePaiementCaution}
                >
                  Payer {(transaction.montantTotalSequestre ?? (transaction.montantCaution + (transaction.montantPremierLoyer || 0) + transaction.fraisDossier)).toLocaleString('fr-FR')} FCFA via Wave (Sandbox)
                </Button>
              ) : (
                <p className="text-xs text-sahel-600 italic">
                  En attente du paiement du séquestre par {isVente ? "l'acheteur" : "le locataire"} ({transaction.locataireNomComplet}).
                </p>
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
          {transaction.statutBail !== 'SIGNE_COMPLET' && (
            <div>
              {((isLocataire && !transaction.dateSignatureLocataire) || (isProprietaire && !transaction.dateSignatureProprietaire)) ? (
                <Button
                  variant="primary"
                  className="w-full"
                  isLoading={isSigning}
                  onClick={handleSignerBail}
                >
                  {isVente ? "Signer le récapitulatif de réservation" : "Signer le bail numérique"}
                </Button>
              ) : (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
                  <span>Votre signature est déjà enregistrée. En attente de la deuxième partie.</span>
                </div>
              )}
            </div>
          )}

          {/* Action Download PDF */}
          {transaction.statutBail === 'SIGNE_COMPLET' && (
            <div className="pt-2">
              <Button
                variant="outline"
                className="w-full border-terracotta-500 text-terracotta-700 hover:bg-terracotta-50 flex items-center justify-center gap-2"
                isLoading={isDownloadingPdf}
                onClick={handleDownloadPdf}
              >
                <Download className="w-4 h-4 text-terracotta-600" />
                <span>
                  {isVente
                    ? "Télécharger le récapitulatif de réservation d'achat (PDF horodaté)"
                    : "Télécharger le contrat de bail PDF (Horodaté & Numéroté)"}
                </span>
              </Button>
            </div>
          )}
        </div>

        {/* Close Modal Button */}
        <div className="flex justify-end pt-2">
          <Button variant="ghost" onClick={onClose}>
            Fermer
          </Button>
        </div>

      </div>
    </Modal>
  );
};
