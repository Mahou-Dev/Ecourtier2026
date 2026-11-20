import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  Building2,
  CalendarCheck,
  CalendarClock,
  Lock,
  LogOut,
  Mail,
  Plus,
  ShieldCheck,
} from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { getRoleLabel } from '../lib/roleLabels';
import {
  canManageListings,
  canManageVisit,
  canPublishListings,
  countOwnPendingRequests,
  countPendingRequestsToProcess,
  countUpcomingConfirmedVisits,
  isTransactionOpen,
  resolveDashboardTab,
  type DashboardTabId,
} from '../lib/dashboardModel';
import {
  useMyAnnoncesCount,
  useMyTransactions,
  useMyVisites,
  useUnreadMessagesCount,
} from '../hooks/useDashboardData';
import type { TransactionResponseDTO } from '../types';
import { Avatar } from '../components/ui/Avatar';
import { Button } from '../components/ui/Button';
import { CreateAnnonceModal } from '../components/dashboard/CreateAnnonceModal';
import { TransactionDetailsModal } from '../components/transaction/TransactionDetailsModal';
import { ConversationsTab } from '../components/messagerie/ConversationsTab';
import { SmsSimulesTab } from '../components/dashboard/SmsSimulesTab';
import {
  DASHBOARD_TAB_ICONS,
  DashboardNav,
  type DashboardNavItem,
} from '../components/dashboard/DashboardNav';
import { DashboardSummary, type DashboardMetric } from '../components/dashboard/DashboardSummary';
import { AnnoncesTab } from '../components/dashboard/AnnoncesTab';
import { VisitesTab } from '../components/dashboard/VisitesTab';
import { TransactionsTab } from '../components/dashboard/TransactionsTab';

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const { utilisateur, logout } = useAuthStore();

  const isStaff = canManageListings(utilisateur?.role);
  const canPublish = canPublishListings(utilisateur?.role);

  // 1. Résolution stricte et synchronisation de l'onglet actif avec l'URL
  const rawTab = searchParams.get('tab');
  const { tab: activeTab, needsNormalization } = useMemo(
    () => resolveDashboardTab(rawTab, utilisateur?.role),
    [rawTab, utilisateur?.role]
  );

  useEffect(() => {
    if (needsNormalization && utilisateur) {
      const next = new URLSearchParams(searchParams);
      next.set('tab', activeTab);
      setSearchParams(next, { replace: true });
    }
  }, [needsNormalization, activeTab, searchParams, setSearchParams, utilisateur]);

  const handleSelectTab = (newTab: DashboardTabId) => {
    const next = new URLSearchParams(searchParams);
    next.set('tab', newTab);
    setSearchParams(next);
  };

  // 2. Modales de gestion
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [selectedTransaction, setSelectedTransaction] = useState<TransactionResponseDTO | null>(null);
  const [isTxModalOpen, setIsTxModalOpen] = useState(false);

  // 3. Données métier réelles — chargement conditionnel par rôle
  // Pour un LOCATAIRE, useMyAnnoncesCount est désactivé (enabled: false)
  const { data: annoncesCount, isLoading: isLoadingAnnoncesCount } = useMyAnnoncesCount(
    {},
    Boolean(utilisateur && isStaff)
  );

  const {
    data: visitsData,
    isLoading: isLoadingVisits,
    isError: isVisitsError,
    isRefetching: isVisitsRefetching,
    refetch: refetchVisites,
  } = useMyVisites(Boolean(utilisateur));

  const {
    data: transactionsData,
    isLoading: isLoadingTransactions,
    isError: isTxError,
    isRefetching: isTxRefetching,
    refetch: refetchTransactions,
  } = useMyTransactions(Boolean(utilisateur));

  const { data: unreadCountData } = useUnreadMessagesCount(Boolean(utilisateur));

  const visitsList = useMemo(() => visitsData ?? [], [visitsData]);
  const transactionsList = useMemo(() => transactionsData ?? [], [transactionsData]);
  const unreadCount = typeof unreadCountData === 'number' ? unreadCountData : 0;

  // Calcul réel des demandes à traiter par annonce
  const pendingRequestsByAnnonce = useMemo(() => {
    if (!utilisateur || !isStaff) return {};
    const map: Record<string, number> = {};
    visitsList.forEach((v) => {
      if (v.statut === 'DEMANDE' && canManageVisit(v, utilisateur)) {
        map[v.annonceId] = (map[v.annonceId] ?? 0) + 1;
      }
    });
    return map;
  }, [visitsList, utilisateur, isStaff]);

  // 4. Configuration de la navigation selon le rôle
  const navItems = useMemo<DashboardNavItem[]>(() => {
    const items: DashboardNavItem[] = [];
    if (isStaff) {
      items.push({
        id: 'annonces',
        label: utilisateur?.role === 'AGENT' ? 'Mon Portefeuille' : 'Mes Annonces',
        icon: DASHBOARD_TAB_ICONS.annonces,
        count: annoncesCount ?? null,
      });
    }
    items.push(
      {
        id: 'visites',
        label: utilisateur?.role === 'LOCATAIRE' ? 'Mes Visites' : 'Agenda Visites',
        icon: DASHBOARD_TAB_ICONS.visites,
        count: visitsList.length,
      },
      {
        id: 'transactions',
        label: 'Suivi Transactions',
        icon: DASHBOARD_TAB_ICONS.transactions,
        count: transactionsList.length,
      },
      {
        id: 'messages',
        label: 'Messagerie',
        icon: DASHBOARD_TAB_ICONS.messages,
        count: unreadCount,
        alert: unreadCount > 0,
      },
      {
        id: 'sms',
        label: 'SMS simulés',
        icon: DASHBOARD_TAB_ICONS.sms,
      }
    );
    return items;
  }, [isStaff, utilisateur?.role, annoncesCount, visitsList.length, transactionsList.length, unreadCount]);

  // 5. Indicateurs synthétiques strictement calculés sur les données réelles
  const summaryMetrics = useMemo<DashboardMetric[]>(() => {
    if (!utilisateur) return [];
    const openTxCount = transactionsList.filter(isTransactionOpen).length;

    if (utilisateur.role === 'LOCATAIRE') {
      const upcoming = countUpcomingConfirmedVisits(visitsList);
      const pending = countOwnPendingRequests(visitsList, utilisateur);
      return [
        {
          id: 'loc-visites-confirmees',
          label: 'Visites confirmées',
          value: isLoadingVisits ? undefined : upcoming,
          hint: upcoming > 0 ? 'À venir' : 'Aucune visite prévue',
          icon: CalendarCheck,
          tab: 'visites',
        },
        {
          id: 'loc-demandes-attente',
          label: 'Demandes en attente',
          value: isLoadingVisits ? undefined : pending,
          hint: pending > 0 ? 'En cours de réponse' : 'Toutes traitées',
          icon: CalendarClock,
          tab: 'visites',
        },
        {
          id: 'loc-transactions',
          label: 'Dossiers en cours',
          value: isLoadingTransactions ? undefined : openTxCount,
          hint: openTxCount > 0 ? 'Séquestre / bail' : 'Aucun dossier',
          icon: Lock,
          tab: 'transactions',
        },
        {
          id: 'loc-messages',
          label: 'Messages non lus',
          value: unreadCount,
          icon: Mail,
          tab: 'messages',
          tone: unreadCount > 0 ? 'attention' : 'default',
        },
      ];
    }

    // PROPRIETAIRE & AGENT
    const toProcess = countPendingRequestsToProcess(visitsList, utilisateur);
    const isAgent = utilisateur.role === 'AGENT';

    return [
      {
        id: 'prop-annonces',
        label: isAgent ? 'Biens en gestion' : 'Annonces enregistrées',
        value: isLoadingAnnoncesCount ? undefined : annoncesCount,
        icon: Building2,
        tab: 'annonces',
      },
      {
        id: 'prop-demandes',
        label: 'Demandes à traiter',
        value: isLoadingVisits ? undefined : toProcess,
        hint: toProcess > 0 ? 'Réponse attendue' : 'À jour',
        icon: CalendarClock,
        tab: 'visites',
        tone: toProcess > 0 ? 'attention' : 'default',
      },
      {
        id: 'prop-transactions',
        label: 'Dossiers actifs',
        value: isLoadingTransactions ? undefined : openTxCount,
        hint: openTxCount > 0 ? 'Sous séquestre' : 'Aucun dossier',
        icon: Lock,
        tab: 'transactions',
      },
      {
        id: 'prop-messages',
        label: 'Messages non lus',
        value: unreadCount,
        icon: Mail,
        tab: 'messages',
        tone: unreadCount > 0 ? 'attention' : 'default',
      },
    ];
  }, [
    utilisateur,
    visitsList,
    transactionsList,
    annoncesCount,
    unreadCount,
    isLoadingVisits,
    isLoadingTransactions,
    isLoadingAnnoncesCount,
  ]);

  const handleOpenTransaction = (tx: TransactionResponseDTO) => {
    setSelectedTransaction(tx);
    setIsTxModalOpen(true);
  };

  const handleLogout = async () => {
    await logout();
    navigate('/connexion');
  };

  if (!utilisateur) return null;

  return (
    <div className="min-h-[calc(100vh-80px)] flex flex-col lg:flex-row bg-sand-100">
      {/* BARRE SUPÉRIEURE TABLETTE ET MOBILE (< 1024px) */}
      <header className="lg:hidden bg-sahel-900 text-white border-b border-sahel-800 px-3.5 py-3 space-y-3 shrink-0">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <Avatar
              src={utilisateur.photoProfilUrl}
              name={`${utilisateur.prenom} ${utilisateur.nom}`}
              size="sm"
            />
            <div className="min-w-0">
              <h2 className="font-display font-bold text-xs sm:text-sm text-white truncate">
                {utilisateur.prenom} {utilisateur.nom}
              </h2>
              <p className="text-[10px] sm:text-[11px] text-ocher-300 font-semibold truncate">
                {getRoleLabel(utilisateur.role)}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            {canPublish && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => setIsCreateModalOpen(true)}
                className="min-h-[38px] text-[11px] px-2.5 py-1"
                leftIcon={<Plus className="w-3.5 h-3.5" aria-hidden="true" />}
              >
                <span>Créer</span>
              </Button>
            )}
            {utilisateur.role === 'ADMIN' && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate('/admin/verification')}
                className="border-ocher-500 text-ocher-300 hover:bg-sahel-800 text-[11px] px-2 py-1 min-h-[38px]"
                leftIcon={<ShieldCheck className="w-3.5 h-3.5 text-ocher-400" aria-hidden="true" />}
              >
                <span>Modération</span>
              </Button>
            )}
            <button
              type="button"
              onClick={handleLogout}
              title="Déconnexion"
              aria-label="Déconnexion"
              className="min-h-[38px] min-w-[38px] p-2 rounded-xl text-rose-300 hover:bg-rose-950/40 transition-colors flex items-center justify-center focus:outline-none focus:ring-2 focus:ring-rose-400"
            >
              <LogOut className="w-4 h-4" aria-hidden="true" />
            </button>
          </div>
        </div>

        <DashboardNav items={navItems} activeTab={activeTab} onSelect={handleSelectTab} />
      </header>

      {/* PANNEAU LATÉRAL DESKTOP (>= 1024px) */}
      <aside
        aria-label="Navigation principale du tableau de bord"
        className="hidden lg:flex lg:w-72 bg-sahel-900 text-white shrink-0 border-r border-sahel-800 p-5 flex-col justify-between"
      >
        <div className="space-y-6">
          {/* Fiche d'identité utilisateur */}
          <div className="flex items-center gap-3 p-3.5 rounded-2xl bg-sahel-800/90 border border-sahel-700">
            <Avatar
              src={utilisateur.photoProfilUrl}
              name={`${utilisateur.prenom} ${utilisateur.nom}`}
              size="md"
            />
            <div className="min-w-0 flex-1">
              <h2 className="font-display font-bold text-sm text-white truncate">
                {utilisateur.prenom} {utilisateur.nom}
              </h2>
              <p className="text-[11px] text-ocher-300 font-semibold truncate">{getRoleLabel(utilisateur.role)}</p>
            </div>
          </div>

          {/* Raccourci d'action rapide selon le rôle */}
          {canPublish && (
            <Button
              variant="primary"
              size="sm"
              onClick={() => setIsCreateModalOpen(true)}
              className="w-full min-h-[44px] text-xs flex items-center justify-center gap-2"
              leftIcon={<Plus className="w-4 h-4" aria-hidden="true" />}
            >
              <span>Créer une annonce</span>
            </Button>
          )}

          {utilisateur.role === 'ADMIN' && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/admin/verification')}
              className="w-full min-h-[44px] border-ocher-500 text-ocher-300 hover:bg-sahel-800 text-xs flex items-center justify-center gap-2"
              leftIcon={<ShieldCheck className="w-4 h-4 text-ocher-400" aria-hidden="true" />}
            >
              <span>Modération Annonces</span>
            </Button>
          )}

          <DashboardNav items={navItems} activeTab={activeTab} onSelect={handleSelectTab} />
        </div>

        {/* Bouton de déconnexion */}
        <button
          type="button"
          onClick={handleLogout}
          className="w-full min-h-[44px] flex items-center gap-2.5 px-4 py-2.5 rounded-xl text-xs font-semibold text-rose-300 hover:bg-rose-950/40 transition-colors focus:outline-none focus:ring-2 focus:ring-rose-400"
        >
          <LogOut className="w-4 h-4 shrink-0" aria-hidden="true" />
          <span>Déconnexion</span>
        </button>
      </aside>

      {/* ZONE CENTRALE DE CONTENU */}
      <main
        tabIndex={-1}
        className="flex-1 min-w-0 p-4 sm:p-6 lg:p-8 space-y-6 overflow-y-auto outline-none"
      >
        {/* Résumé des indicateurs métier */}
        <DashboardSummary metrics={summaryMetrics} onSelectTab={handleSelectTab} />

        {/* CONTENU DE L'ONGLET ACTIF */}
        {activeTab === 'annonces' && isStaff && (
          <AnnoncesTab
            utilisateur={utilisateur}
            pendingRequestsByAnnonce={pendingRequestsByAnnonce}
            onCreate={() => setIsCreateModalOpen(true)}
          />
        )}

        {activeTab === 'visites' && (
          <VisitesTab
            utilisateur={utilisateur}
            visits={visitsList}
            transactions={transactionsList}
            isLoading={isLoadingVisits}
            isError={isVisitsError}
            isRefetching={isVisitsRefetching}
            onRetry={() => void refetchVisites()}
            onSelectTab={handleSelectTab}
          />
        )}

        {activeTab === 'transactions' && (
          <TransactionsTab
            utilisateur={utilisateur}
            transactions={transactionsList}
            isLoading={isLoadingTransactions}
            isError={isTxError}
            isRefetching={isTxRefetching}
            onRetry={() => void refetchTransactions()}
            onOpen={handleOpenTransaction}
          />
        )}

        {activeTab === 'messages' && <ConversationsTab />}

        {activeTab === 'sms' && <SmsSimulesTab />}
      </main>

      {/* MODALE DE CRÉATION D'ANNONCE */}
      {canPublish && (
        <CreateAnnonceModal
          isOpen={isCreateModalOpen}
          onClose={() => setIsCreateModalOpen(false)}
          onSuccess={() => {
            void queryClient.invalidateQueries({ queryKey: ['my-annonces'] });
          }}
        />
      )}

      {/* MODALE DE DÉTAIL TRANSACTION ET SIGNATURE */}
      <TransactionDetailsModal
        isOpen={isTxModalOpen}
        onClose={() => {
          setIsTxModalOpen(false);
          setSelectedTransaction(null);
        }}
        transaction={
          (selectedTransaction &&
            transactionsList.find((tx) => tx.id === selectedTransaction.id)) ||
          selectedTransaction
        }
        onRefresh={() => {
          void queryClient.invalidateQueries({ queryKey: ['my-transactions'] });
          void queryClient.invalidateQueries({ queryKey: ['my-annonces'] });
        }}
      />
    </div>
  );
};

export default DashboardPage;
