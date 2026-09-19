import React, { useState } from 'react';
import { 
  Home, 
  Calendar, 
  Lock, 
  Plus, 
  Eye, 
  LogOut,
  ShieldCheck,
  CheckCircle2,
  Clock,
  Building
} from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, getPhotoUrl } from '../services/api';
import { AnnonceDTO, PageResponse, RendezVousResponseDTO, TransactionResponseDTO } from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Card, CardBody } from '../components/ui/Card';
import { Avatar } from '../components/ui/Avatar';
import { Skeleton } from '../components/ui/Skeleton';
import { useNavigate } from 'react-router-dom';
import { CreateAnnonceModal } from '../components/dashboard/CreateAnnonceModal';
import { TransactionDetailsModal } from '../components/transaction/TransactionDetailsModal';
import { toast } from 'sonner';

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { utilisateur, logout } = useAuthStore();
  const [activeTab, setActiveTab] = useState<'ANNONCES' | 'VISITES' | 'SEQUESTRES'>('ANNONCES');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [selectedTransaction, setSelectedTransaction] = useState<TransactionResponseDTO | null>(null);
  const [isTxModalOpen, setIsTxModalOpen] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);

  // Redirect to login if unauthenticated
  React.useEffect(() => {
    if (!utilisateur) {
      navigate('/connexion');
    }
  }, [utilisateur, navigate]);

  // 1. Fetch user listings
  const { data: annoncesData, isLoading: isLoadingAnnonces } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: ['my-annonces'],
    queryFn: () => apiFetch<PageResponse<AnnonceDTO>>('/api/v1/annonces?mes_annonces=true&page=0&size=50'),
    enabled: !!utilisateur,
  });

  // 2. Fetch user visits
  const { data: visitsData, isLoading: isLoadingVisits } = useQuery<RendezVousResponseDTO[]>({
    queryKey: ['my-visites'],
    queryFn: () => apiFetch<RendezVousResponseDTO[]>('/api/v1/rendez-vous/mes-rendez-vous'),
    enabled: !!utilisateur,
  });

  // 3. Fetch user transactions
  const { data: transactionsData, isLoading: isLoadingTransactions } = useQuery<TransactionResponseDTO[]>({
    queryKey: ['my-transactions'],
    queryFn: () => apiFetch<TransactionResponseDTO[]>('/api/v1/transactions/mes-transactions'),
    enabled: !!utilisateur,
  });

  const annoncesList = annoncesData?.content || [];
  const visitsList = visitsData || [];
  const transactionsList = transactionsData || [];

  // Action Handlers for Visits
  const handleConfirmVisit = async (visitId: string) => {
    setActionLoadingId(visitId);
    try {
      await apiFetch<RendezVousResponseDTO>(`/api/v1/rendez-vous/${visitId}/confirmer`, { method: 'PUT' });
      toast.success('Rendez-vous confirmé avec succès !');
      queryClient.invalidateQueries({ queryKey: ['my-visites'] });
    } catch (err: any) {
      toast.error(err.message || 'Échec de la confirmation du rendez-vous');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleCancelVisit = async (visitId: string) => {
    setActionLoadingId(visitId);
    try {
      await apiFetch<RendezVousResponseDTO>(`/api/v1/rendez-vous/${visitId}/annuler`, {
        method: 'PUT',
        body: JSON.stringify({ motif: 'Annulé depuis le tableau de bord' }),
      });
      toast.success('Rendez-vous annulé.');
      queryClient.invalidateQueries({ queryKey: ['my-visites'] });
    } catch (err: any) {
      toast.error(err.message || 'Échec de l\'annulation');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleMarkEffectueVisit = async (visitId: string) => {
    setActionLoadingId(visitId);
    try {
      await apiFetch<RendezVousResponseDTO>(`/api/v1/rendez-vous/${visitId}/marquer-effectue`, { method: 'PUT' });
      toast.success('Rendez-vous marqué comme EFFECTUÉ !');
      queryClient.invalidateQueries({ queryKey: ['my-visites'] });
    } catch (err: any) {
      toast.error(err.message || 'Échec du changement de statut');
    } finally {
      setActionLoadingId(null);
    }
  };

  const openTransactionModal = (tx: TransactionResponseDTO) => {
    setSelectedTransaction(tx);
    setIsTxModalOpen(true);
  };

  const handleLogout = () => {
    logout();
    navigate('/connexion');
  };

  if (!utilisateur) return null;

  return (
    <div className="min-h-[calc(100vh-80px)] flex flex-col md:flex-row bg-sand-100">
      
      {/* SIDEBAR NAVIGATION */}
      <aside className="w-full md:w-64 bg-sahel-900 text-white shrink-0 border-r border-sahel-800 p-4 space-y-6 flex flex-col justify-between">
        <div className="space-y-6">
          {/* User profile snippet */}
          <div className="flex items-center gap-3 p-3 rounded-2xl bg-sahel-800 border border-sahel-700">
            <Avatar
              src={utilisateur?.photoProfilUrl}
              name={utilisateur ? `${utilisateur.prenom} ${utilisateur.nom}` : 'Utilisateur'}
              size="md"
              isVerified
            />
            <div className="min-w-0">
              <h3 className="font-display font-bold text-sm text-white truncate">{utilisateur?.prenom} {utilisateur?.nom}</h3>
              <p className="text-[11px] text-ocher-300 font-semibold">{utilisateur?.role}</p>
            </div>
          </div>

          {/* Admin Moderation Button shortcut */}
          {utilisateur.role === 'ADMIN' && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/admin/verification')}
              className="w-full border-ocher-500 text-ocher-300 hover:bg-sahel-800 text-xs flex items-center justify-center gap-2"
            >
              <ShieldCheck className="w-4 h-4 text-ocher-400" />
              <span>Modération Annonces</span>
            </Button>
          )}

          {/* Sidebar Nav Items */}
          <nav className="space-y-1">
            <button
              onClick={() => setActiveTab('ANNONCES')}
              className={`w-full flex items-center justify-between px-4 py-3 rounded-xl text-xs font-semibold transition-colors ${
                activeTab === 'ANNONCES' ? 'bg-terracotta-500 text-white shadow-terracotta-glow' : 'text-sahel-200 hover:bg-sahel-800'
              }`}
            >
              <div className="flex items-center gap-2.5">
                <Home className="w-4 h-4" />
                <span>Mes Annonces</span>
              </div>
              <Badge variant="neutral" className="bg-sahel-800 text-white border-0 text-[10px]">
                {annoncesList.length}
              </Badge>
            </button>

            <button
              onClick={() => setActiveTab('VISITES')}
              className={`w-full flex items-center justify-between px-4 py-3 rounded-xl text-xs font-semibold transition-colors ${
                activeTab === 'VISITES' ? 'bg-terracotta-500 text-white shadow-terracotta-glow' : 'text-sahel-200 hover:bg-sahel-800'
              }`}
            >
              <div className="flex items-center gap-2.5">
                <Calendar className="w-4 h-4" />
                <span>Agenda Visites</span>
              </div>
              <Badge variant="neutral" className="bg-sahel-800 text-white border-0 text-[10px]">
                {visitsList.length}
              </Badge>
            </button>

            <button
              onClick={() => setActiveTab('SEQUESTRES')}
              className={`w-full flex items-center justify-between px-4 py-3 rounded-xl text-xs font-semibold transition-colors ${
                activeTab === 'SEQUESTRES' ? 'bg-terracotta-500 text-white shadow-terracotta-glow' : 'text-sahel-200 hover:bg-sahel-800'
              }`}
            >
              <div className="flex items-center gap-2.5">
                <Lock className="w-4 h-4" />
                <span>Suivi Transactions</span>
              </div>
              <Badge variant="neutral" className="bg-sahel-800 text-white border-0 text-[10px]">
                {transactionsList.length}
              </Badge>
            </button>
          </nav>
        </div>

        {/* Logout Button */}
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-2 px-4 py-3 rounded-xl text-xs font-semibold text-rose-300 hover:bg-rose-950/40 transition-colors"
        >
          <LogOut className="w-4 h-4" />
          <span>Déconnexion</span>
        </button>
      </aside>

      {/* MAIN DASHBOARD CONTENT */}
      <main className="flex-1 p-6 md:p-8 space-y-6 overflow-y-auto">
        
        {/* TAB 1: MES ANNONCES */}
        {activeTab === 'ANNONCES' && (
          <div className="space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h1 className="font-display font-bold text-2xl text-sahel-900">Mes Annonces Immobilières</h1>
                <p className="text-xs text-sahel-600">Gérez vos propriétés, vérifiez le statut de publication et contrôlez vos annonces.</p>
              </div>
              {(utilisateur.role === 'PROPRIETAIRE' || utilisateur.role === 'AGENT' || utilisateur.role === 'ADMIN') && (
                <Button
                  variant="primary"
                  onClick={() => setIsCreateModalOpen(true)}
                  className="flex items-center gap-2 shrink-0"
                >
                  <Plus className="w-4 h-4" />
                  <span>Créer une annonce</span>
                </Button>
              )}
            </div>

            {isLoadingAnnonces ? (
              <div className="space-y-4">
                <Skeleton variant="card" className="h-24" />
                <Skeleton variant="card" className="h-24" />
              </div>
            ) : annoncesList.length === 0 ? (
              <Card className="p-12 text-center space-y-4 border border-sand-300 bg-sand-50">
                <Building className="w-12 h-12 text-sahel-400 mx-auto" />
                <h3 className="font-display font-bold text-lg text-sahel-900">Aucune annonce enregistrée</h3>
                <p className="text-xs text-sahel-600 max-w-md mx-auto">
                  Vous n'avez pas encore publié de propriété. Déposez votre premier bien pour recevoir des demandes de visite.
                </p>
                {(utilisateur.role === 'PROPRIETAIRE' || utilisateur.role === 'AGENT' || utilisateur.role === 'ADMIN') && (
                  <Button variant="primary" onClick={() => setIsCreateModalOpen(true)} className="inline-flex items-center gap-2">
                    <Plus className="w-4 h-4" /> Publier un bien
                  </Button>
                )}
              </Card>
            ) : (
              <div className="grid grid-cols-1 gap-4">
                {annoncesList.map((item) => (
                  <Card key={item.id} className="overflow-hidden border border-sand-300 hover:shadow-warm-hover transition-all">
                    <CardBody className="p-4 sm:p-5">
                      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                        <div className="flex items-center gap-4">
                          <img
                            src={getPhotoUrl(item.photos?.[0]?.url)}
                            alt={item.titre}
                            className="w-16 h-16 rounded-xl object-cover bg-sand-200 shrink-0"
                          />
                          <div>
                            <h3 className="font-display font-bold text-sm text-sahel-900">{item.titre}</h3>
                            <p className="text-xs text-sahel-600">
                              {item.ville} ({item.quartier}) • <strong className="text-terracotta-700">{item.prix.toLocaleString('fr-FR')} FCFA</strong>
                            </p>
                            <div className="flex flex-wrap items-center gap-2 mt-2">
                              {item.statutPublication === 'DISPONIBLE' && <Badge variant="success">Publication: Disponible</Badge>}
                              {item.statutPublication === 'RESERVEE' && <Badge variant="info">Publication: Réservée</Badge>}
                              {item.statutPublication === 'LOUEE' && <Badge variant="neutral">Publication: Louée</Badge>}
                              {item.statutPublication === 'VENDUE' && <Badge variant="neutral">Publication: Vendue</Badge>}

                              {item.statutVerification === 'VERIFIEE' && (
                                <Badge variant="success" className="flex items-center gap-1">
                                  <ShieldCheck className="w-3 h-3 text-emerald-600" />
                                  <span>Cadastre Vérifié</span>
                                </Badge>
                              )}
                              {item.statutVerification === 'EN_ATTENTE' && (
                                <Badge variant="warning" className="flex items-center gap-1">
                                  <Clock className="w-3 h-3 text-amber-600" />
                                  <span>Vérification en cours</span>
                                </Badge>
                              )}
                              {item.statutVerification === 'REJETEE' && <Badge variant="rose">Vérification Rejetée</Badge>}
                            </div>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 w-full sm:w-auto justify-end border-t sm:border-t-0 pt-3 sm:pt-0 border-sand-200">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => navigate(`/annonce/${item.id}`)}
                            className="text-xs flex items-center gap-1.5"
                          >
                            <Eye className="w-4 h-4 text-sahel-600" />
                            <span>Consulter</span>
                          </Button>
                        </div>
                      </div>
                    </CardBody>
                  </Card>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: AGENDA DES VISITES */}
        {activeTab === 'VISITES' && (
          <div className="space-y-6">
            <div>
              <h1 className="font-display font-bold text-2xl text-sahel-900">Agenda des Visites</h1>
              <p className="text-xs text-sahel-600">Suivez et gérez les demandes de rendez-vous de visite programmées sur vos biens.</p>
            </div>

            {isLoadingVisits ? (
              <Skeleton variant="card" className="h-32" />
            ) : visitsList.length === 0 ? (
              <Card className="p-12 text-center space-y-4 border border-sand-300 bg-sand-50">
                <Calendar className="w-12 h-12 text-sahel-400 mx-auto" />
                <h3 className="font-display font-bold text-lg text-sahel-900">Aucun rendez-vous prévu</h3>
                <p className="text-xs text-sahel-600 max-w-md mx-auto">
                  Vos demandes de visite apparaîtront ici au fur et à mesure des réservations des utilisateurs.
                </p>
              </Card>
            ) : (
              <div className="space-y-4">
                {visitsList.map((rdv) => (
                  <Card key={rdv.id} className="overflow-hidden border border-sand-300">
                    <CardBody className="p-5">
                      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                        <div className="space-y-1">
                          <div className="flex items-center gap-2">
                            <Building className="w-4 h-4 text-terracotta-500 shrink-0" />
                            <h3 className="font-display font-bold text-sm text-sahel-900">{rdv.annonceTitre}</h3>
                          </div>
                          <p className="text-xs text-sahel-600">
                            Demandeur : <strong className="text-sahel-900">{rdv.demandeurNomComplet}</strong> ({rdv.demandeurEmail})
                          </p>
                          <p className="text-xs text-terracotta-700 font-semibold flex items-center gap-1">
                            <Clock className="w-3.5 h-3.5" />
                            {new Date(rdv.dateHeure).toLocaleString('fr-FR', {
                              dateStyle: 'full',
                              timeStyle: 'short',
                            })}
                          </p>
                        </div>

                        <div className="flex flex-wrap items-center gap-3">
                          {rdv.statut === 'DEMANDE' && <Badge variant="warning">Demandé</Badge>}
                          {rdv.statut === 'CONFIRME' && <Badge variant="info">Confirmé</Badge>}
                          {rdv.statut === 'ANNULE' && <Badge variant="rose">Annulé</Badge>}
                          {rdv.statut === 'EFFECTUE' && <Badge variant="success">Visite Effectuée</Badge>}

                          {/* Actions depending on role and status */}
                          <div className="flex items-center gap-2">
                            {rdv.statut === 'DEMANDE' && (
                              <>
                                <Button
                                  variant="primary"
                                  size="sm"
                                  isLoading={actionLoadingId === rdv.id}
                                  onClick={() => handleConfirmVisit(rdv.id)}
                                >
                                  Confirmer
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  className="text-rose-600 hover:bg-rose-50"
                                  isLoading={actionLoadingId === rdv.id}
                                  onClick={() => handleCancelVisit(rdv.id)}
                                >
                                  Annuler
                                </Button>
                              </>
                            )}

                            {rdv.statut === 'CONFIRME' && (
                              <Button
                                variant="outline"
                                size="sm"
                                className="border-emerald-600 text-emerald-700 hover:bg-emerald-50"
                                isLoading={actionLoadingId === rdv.id}
                                onClick={() => handleMarkEffectueVisit(rdv.id)}
                              >
                                <CheckCircle2 className="w-3.5 h-3.5 mr-1" />
                                Marquer effectué
                              </Button>
                            )}

                            {rdv.statut === 'EFFECTUE' && utilisateur.role === 'LOCATAIRE' && (
                              <Button
                                variant="primary"
                                size="sm"
                                onClick={() => navigate(`/annonce/${rdv.annonceId}`)}
                              >
                                Initier location / achat
                              </Button>
                            )}
                          </div>
                        </div>
                      </div>
                    </CardBody>
                  </Card>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 3: SUIVI DES DOSSIERS / TRANSACTIONS */}
        {activeTab === 'SEQUESTRES' && (
          <div className="space-y-6">
            <div>
              <h1 className="font-display font-bold text-2xl text-sahel-900">Suivi des Transactions & Séquestres</h1>
              <p className="text-xs text-sahel-600">Contrôlez les dépôts de caution consignés et suivez la signature du contrat de bail numérique.</p>
            </div>

            {isLoadingTransactions ? (
              <Skeleton variant="card" className="h-32" />
            ) : transactionsList.length === 0 ? (
              <Card className="p-12 text-center space-y-4 border border-sand-300 bg-sand-50">
                <Lock className="w-12 h-12 text-sahel-400 mx-auto" />
                <h3 className="font-display font-bold text-lg text-sahel-900">Aucune transaction en cours</h3>
                <p className="text-xs text-sahel-600 max-w-md mx-auto">
                  Les dossiers de location initiés suite aux visites effectuées s'afficheront ici.
                </p>
              </Card>
            ) : (
              <div className="space-y-4">
                {transactionsList.map((tx) => (
                  <Card key={tx.id} className="overflow-hidden border border-sand-300 hover:shadow-warm-hover transition-all">
                    <CardBody className="p-5">
                      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                        <div className="space-y-1">
                          <div className="flex items-center gap-2">
                            <Building className="w-4 h-4 text-terracotta-500 shrink-0" />
                            <h3 className="font-display font-bold text-base text-sahel-900">{tx.annonceTitre}</h3>
                          </div>
                          <p className="text-xs text-sahel-600">
                            Locataire: <strong>{tx.locataireNomComplet}</strong> | Propriétaire: <strong>{tx.proprietaireNomComplet || 'Agent'}</strong>
                          </p>
                          <p className="text-xs text-terracotta-700 font-bold">
                            Caution : {tx.montantCaution.toLocaleString('fr-FR')} FCFA (+ 5 000 FCFA frais)
                          </p>
                        </div>

                        <div className="flex flex-wrap items-center gap-3">
                          {tx.statutSequestre === 'INITIALISE' && <Badge variant="warning">Caution : À consigner</Badge>}
                          {tx.statutSequestre === 'BLOQUE' && <Badge variant="info">Caution : Consignée</Badge>}
                          {tx.statutSequestre === 'LIBERE' && <Badge variant="success">Caution : Libérée</Badge>}
                          {tx.statutSequestre === 'REMBOURSE' && <Badge variant="neutral">Caution : Remboursée</Badge>}

                          {tx.statutBail === 'NON_SIGNE' && <Badge variant="neutral">Bail : Non signé</Badge>}
                          {(tx.statutBail === 'SIGNE_LOCATAIRE' || tx.statutBail === 'SIGNE_PROPRIETAIRE') && (
                            <Badge variant="info">Bail : 1/2 Signature</Badge>
                          )}
                          {tx.statutBail === 'SIGNE_COMPLET' && <Badge variant="success">Bail : Signé complet</Badge>}

                          <Button
                            variant="primary"
                            size="sm"
                            onClick={() => openTransactionModal(tx)}
                          >
                            Ouvrir le dossier
                          </Button>
                        </div>
                      </div>
                    </CardBody>
                  </Card>
                ))}
              </div>
            )}
          </div>
        )}

      </main>

      {/* CREATE LISTING MODAL */}
      <CreateAnnonceModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: ['my-annonces'] });
        }}
      />

      {/* TRANSACTION DETAILS & SIGNATURE MODAL */}
      <TransactionDetailsModal
        isOpen={isTxModalOpen}
        onClose={() => setIsTxModalOpen(false)}
        transaction={selectedTransaction}
        onRefresh={() => {
          queryClient.invalidateQueries({ queryKey: ['my-transactions'] });
        }}
      />

    </div>
  );
};

export default DashboardPage;
