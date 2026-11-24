import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { 
  CreditCard, 
  Filter, 
  RefreshCw, 
  AlertCircle 
} from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { adminService } from '../services/admin.service';
import { PageResponse, TransactionResponseDTO } from '../types';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Skeleton } from '../components/ui/Skeleton';
import { AdminNavTabs } from '../components/admin/AdminNavTabs';
import { TransactionDetailsModal } from '../components/transaction/TransactionDetailsModal';
import { toast } from 'sonner';

export const AdminTransactionsPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { utilisateur } = useAuthStore();

  const [page, setPage] = useState(0);
  const [selectedStatut, setSelectedStatut] = useState<string>('TOUS');
  const [selectedTransaction, setSelectedTransaction] = useState<TransactionResponseDTO | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Security check: redirect non-admin
  React.useEffect(() => {
    if (!utilisateur) {
      navigate('/connexion');
    } else if (utilisateur.role !== 'ADMIN') {
      toast.error('Accès refusé : espace réservé aux administrateurs E-Courtier.');
      navigate('/dashboard');
    }
  }, [utilisateur, navigate]);

  const { data, isLoading, isError, refetch, isRefetching } = useQuery<PageResponse<TransactionResponseDTO>>({
    queryKey: ['admin-transactions', page, selectedStatut],
    queryFn: () => adminService.getTransactions({ page, size: 12, statut: selectedStatut }),
    enabled: utilisateur?.role === 'ADMIN',
  });

  if (utilisateur?.role !== 'ADMIN') return null;

  const transactions = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const totalElements = data?.totalElements || 0;

  const formatFCFA = (amount: number) => {
    return new Intl.NumberFormat('fr-FR').format(amount || 0) + ' FCFA';
  };

  const renderSequestreBadge = (tx: TransactionResponseDTO) => {
    if (tx.statutSequestre === 'INITIALISE' && tx.statutPaiementExterne === 'EN_ATTENTE_CONFIRMATION') {
      return <Badge variant="warning">En attente IPN PayTech</Badge>;
    }
    switch (tx.statutSequestre) {
      case 'INITIALISE':
        return <Badge variant="warning">En attente paiement</Badge>;
      case 'BLOQUE':
        return <Badge variant="info">{tx.modeSimulationPaiement === false ? 'Confirmé (PayTech)' : 'Confirmé (Simulé)'}</Badge>;
      case 'LIBERE':
        return <Badge variant="success">Libération logique</Badge>;
      case 'REMBOURSE':
        return <Badge variant="neutral">{tx.modeSimulationPaiement === false ? 'Remboursé' : 'Remboursé (simulé)'}</Badge>;
      case 'ANNULE':
        return <Badge variant="neutral">Annulé</Badge>;
      case 'EXPIRE':
        return <Badge variant="rose">Expiré</Badge>;
      default:
        return <Badge variant="neutral">{tx.statutSequestre}</Badge>;
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-3 sm:px-4 py-6 sm:py-8 pb-24 lg:pb-8 space-y-6">
      {/* Header and Nav */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <h1 className="font-display font-extrabold text-2xl sm:text-3xl text-charcoal-900">
            Gestion des Séquestres & Remboursements
          </h1>
          <p className="text-xs sm:text-sm text-charcoal-600 mt-1">
            Superviser les paiements PayTech (Wave Sandbox) / simulation locale, signatures contractuelles et remboursements simulés.
          </p>
        </div>
        <AdminNavTabs />
      </div>

      {/* Filter Toolbar */}
      <Card className="p-3 sm:p-4 bg-white border-argile-200/80 shadow-xs flex flex-col lg:flex-row lg:items-center justify-between gap-3">
        <div className="flex flex-col sm:flex-row sm:items-center gap-2 min-w-0">
          <div className="flex items-center gap-1.5 shrink-0">
            <Filter className="w-4 h-4 text-terracotta-600 shrink-0" />
            <span className="text-xs font-bold text-charcoal-700 uppercase tracking-wider">Statut séquestre :</span>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {['TOUS', 'BLOQUE', 'REMBOURSE', 'LIBERE', 'INITIALISE', 'ANNULE', 'EXPIRE'].map((st) => (
              <button
                key={st}
                onClick={() => {
                  setSelectedStatut(st);
                  setPage(0);
                }}
                className={`px-2.5 sm:px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                  selectedStatut === st
                    ? 'bg-terracotta-600 text-white shadow-xs'
                    : 'bg-sand-100 text-charcoal-700 hover:bg-sand-200'
                }`}
              >
                {st === 'TOUS' ? 'Tous' : st}
              </button>
            ))}
          </div>
        </div>

        <div className="flex items-center justify-between sm:justify-end gap-2 shrink-0">
          <span className="text-xs text-charcoal-500 font-medium">
            {totalElements} dossier(s)
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isRefetching}
            className="flex items-center gap-1.5 text-xs"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isRefetching ? 'animate-spin' : ''}`} />
            <span>Actualiser</span>
          </Button>
        </div>
      </Card>

      {/* Data Table / Mobile Cards */}
      <Card className="overflow-hidden bg-white border-argile-200/80 shadow-xs">
        {isLoading ? (
          <div className="p-6 space-y-4">
            <Skeleton variant="rect" className="h-10 w-full rounded-xl" />
            <Skeleton variant="rect" className="h-14 w-full rounded-xl" />
            <Skeleton variant="rect" className="h-14 w-full rounded-xl" />
            <Skeleton variant="rect" className="h-14 w-full rounded-xl" />
          </div>
        ) : isError ? (
          <div className="p-8 text-center text-rose-600 space-y-2">
            <AlertCircle className="w-8 h-8 mx-auto" />
            <p className="font-semibold text-sm">Erreur lors de la récupération des transactions.</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Réessayer
            </Button>
          </div>
        ) : transactions.length === 0 ? (
          <div className="p-12 text-center text-charcoal-500 space-y-2">
            <CreditCard className="w-10 h-10 mx-auto text-sand-400" />
            <p className="font-semibold text-sm">Aucune transaction trouvée pour ce filtre.</p>
          </div>
        ) : (
          <>
            {/* Mobile Cards (< md) */}
            <div className="md:hidden divide-y divide-sand-200">
              {transactions.map((tx) => (
                <div key={tx.id} className="p-4 space-y-3 text-xs text-charcoal-700">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <span className="font-mono font-bold text-charcoal-900">#{tx.id.slice(0, 8)}</span>
                      <span className="text-[11px] text-charcoal-400 ml-2">
                        {tx.dateCreation ? new Date(tx.dateCreation).toLocaleDateString('fr-FR') : '-'}
                      </span>
                      <p className="font-semibold text-sm text-charcoal-900 mt-0.5 break-words">
                        {tx.annonceTitre}
                      </p>
                    </div>
                    <span className="text-[10px] px-2 py-0.5 rounded bg-sand-200 font-semibold text-sahel-800 shrink-0">
                      {tx.modeTransaction || 'LOCATION'}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 xs:grid-cols-2 gap-2 bg-sand-50/80 p-2.5 rounded-xl border border-sand-200">
                    <div className="min-w-0">
                      <span className="text-[10px] text-charcoal-500 block">Locataire / Acheteur</span>
                      <span className="font-semibold text-charcoal-900 break-words">{tx.locataireNomComplet}</span>
                    </div>
                    <div className="min-w-0">
                      <span className="text-[10px] text-charcoal-500 block">Propriétaire / Vendeur</span>
                      <span className="font-semibold text-charcoal-900 break-words">{tx.proprietaireNomComplet || 'Agent'}</span>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <span className="text-[10px] text-charcoal-500 block">Montant séquestre</span>
                      <span className="font-bold text-sm text-charcoal-900">
                        {formatFCFA(tx.montantTotalSequestre)}
                      </span>
                      {tx.montantRembourse && (
                        <div className="text-[10px] text-terracotta-700 font-medium">
                          Remboursé : {formatFCFA(tx.montantRembourse)}
                        </div>
                      )}
                    </div>
                    <div className="flex flex-wrap items-center gap-1.5">
                      {renderSequestreBadge(tx)}
                      <span className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                        tx.statutBail === 'SIGNE_COMPLET'
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-amber-100 text-amber-800'
                      }`}>
                        {tx.statutBail === 'SIGNE_COMPLET' ? 'Signé complet' : 'En attente sign.'}
                      </span>
                    </div>
                  </div>

                  {tx.referenceWave && (
                    <div className="text-[11px] font-mono text-emerald-700 break-all">
                      Réf Paiement : {tx.referenceWave}
                    </div>
                  )}

                  <Button
                    variant={tx.statutSequestre === 'BLOQUE' ? 'primary' : 'outline'}
                    size="sm"
                    className={`w-full justify-center text-xs ${
                      tx.statutSequestre === 'BLOQUE' ? 'bg-terracotta-600 hover:bg-terracotta-700 text-white' : ''
                    }`}
                    onClick={() => {
                      setSelectedTransaction(tx);
                      setIsModalOpen(true);
                    }}
                  >
                    {tx.statutSequestre === 'BLOQUE' ? 'Rembourser / Gérer' : 'Détails'}
                  </Button>
                </div>
              ))}
            </div>

            {/* Desktop Table (md+) */}
            <div className="hidden md:block overflow-x-auto">
              <table className="w-full text-left text-xs text-charcoal-700 border-collapse">
                <thead className="bg-sand-50/80 border-b border-sand-200 text-charcoal-500 uppercase text-[10px] tracking-wider font-bold">
                  <tr>
                    <th className="py-3 px-4">Dossier / Date</th>
                    <th className="py-3 px-4">Annonce</th>
                    <th className="py-3 px-4">Parties prenantes</th>
                    <th className="py-3 px-4">Montant séquestre</th>
                    <th className="py-3 px-4">Statut Séquestre</th>
                    <th className="py-3 px-4">Bail / Vente</th>
                    <th className="py-3 px-4">Réf Paiement</th>
                    <th className="py-3 px-4 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-sand-200/70">
                  {transactions.map((tx) => (
                    <tr key={tx.id} className="hover:bg-sand-50/50 transition-colors">
                      <td className="py-3.5 px-4 font-mono">
                        <span className="font-bold text-charcoal-900">#{tx.id.slice(0, 8)}</span>
                        <div className="text-[10px] text-charcoal-400">
                          {tx.dateCreation ? new Date(tx.dateCreation).toLocaleDateString('fr-FR') : '-'}
                        </div>
                      </td>
                      <td className="py-3.5 px-4 max-w-[200px]">
                        <p className="font-semibold text-charcoal-900 truncate">{tx.annonceTitre}</p>
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-sand-200 font-semibold text-sahel-800">
                          {tx.modeTransaction || 'LOCATION'}
                        </span>
                      </td>
                      <td className="py-3.5 px-4">
                        <div>Loc/Ach: <span className="font-medium text-charcoal-900">{tx.locataireNomComplet}</span></div>
                        <div>Prop/Vendeur: <span className="font-medium text-charcoal-900">{tx.proprietaireNomComplet || 'Agent'}</span></div>
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="font-bold text-charcoal-900">
                          {formatFCFA(tx.montantTotalSequestre)}
                        </span>
                        {tx.montantRembourse && (
                          <div className="text-[10px] text-terracotta-700 font-medium">
                            Remboursé : {formatFCFA(tx.montantRembourse)}
                          </div>
                        )}
                      </td>
                      <td className="py-3.5 px-4">
                        {renderSequestreBadge(tx)}
                      </td>
                      <td className="py-3.5 px-4">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                          tx.statutBail === 'SIGNE_COMPLET' 
                            ? 'bg-emerald-100 text-emerald-800' 
                            : 'bg-amber-100 text-amber-800'
                        }`}>
                          {tx.statutBail === 'SIGNE_COMPLET' ? 'Signé complet' : 'En attente sign.'}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 font-mono text-[11px]">
                        {tx.referenceWave ? (
                          <span className="text-emerald-700 font-semibold">{tx.referenceWave}</span>
                        ) : (
                          <span className="text-charcoal-400">-</span>
                        )}
                        {tx.referenceRemboursementWave && (
                          <div className="text-[10px] text-terracotta-700">
                            Remb : {tx.referenceRemboursementWave}
                          </div>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <Button
                          variant={tx.statutSequestre === 'BLOQUE' ? 'primary' : 'outline'}
                          size="sm"
                          className={tx.statutSequestre === 'BLOQUE' ? 'bg-terracotta-600 hover:bg-terracotta-700 text-white text-xs' : 'text-xs'}
                          onClick={() => {
                            setSelectedTransaction(tx);
                            setIsModalOpen(true);
                          }}
                        >
                          {tx.statutSequestre === 'BLOQUE' ? 'Rembourser / Gérer' : 'Détails'}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="p-4 border-t border-sand-200 flex flex-wrap items-center justify-between gap-2 text-xs">
            <span className="text-charcoal-500">
              Page {page + 1} sur {totalPages}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Précédent
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Suivant
              </Button>
            </div>
          </div>
        )}
      </Card>

      {/* Transaction Details & Refund Modal */}
      {selectedTransaction && (
        <TransactionDetailsModal
          isOpen={isModalOpen}
          onClose={() => {
            setIsModalOpen(false);
            setSelectedTransaction(null);
          }}
          transaction={
            transactions.find((t) => t.id === selectedTransaction.id) || selectedTransaction
          }
          onRefresh={() => {
            queryClient.invalidateQueries({ queryKey: ['admin-transactions'] });
            refetch().then((result) => {
              if (result.data?.content && selectedTransaction) {
                const updated = result.data.content.find((t) => t.id === selectedTransaction.id);
                if (updated) setSelectedTransaction(updated);
              }
            });
          }}
        />
      )}
    </div>
  );
};

export default AdminTransactionsPage;
