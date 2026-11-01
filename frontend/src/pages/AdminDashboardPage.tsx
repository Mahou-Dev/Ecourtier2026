import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts';
import {
  ShieldCheck, LayoutDashboard, Flag, Users, Building, Calendar,
  TrendingUp, Lock, RefreshCw, Star, Bot
} from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { adminService } from '../services/admin.service';
import { AdminStatistiquesDTO } from '../types';
import { Card } from '../components/ui/Card';
import { Skeleton } from '../components/ui/Skeleton';
import { toast } from 'sonner';

// Brand Palette Tokens for Charts
const PALETTE = {
  terracotta: '#c25e40',
  terracottaLight: '#e48d73',
  sahel: '#2c4c3b',
  sahelLight: '#4d7561',
  ocher: '#d99b26',
  ocherLight: '#f5c66b',
  argile: '#8c7a6b',
  charcoal: '#262322',
  rose: '#e11d48',
  emerald: '#059669',
  pieColors: ['#c25e40', '#2c4c3b', '#d99b26', '#8c7a6b', '#e11d48', '#0284c7']
};

export const AdminDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const { utilisateur } = useAuthStore();
  const [periode, setPeriode] = useState<'7j' | '30j' | '90j' | 'tout'>('30j');

  // Security check: redirect non-admin
  React.useEffect(() => {
    if (!utilisateur) {
      navigate('/connexion');
    } else if (utilisateur.role !== 'ADMIN') {
      toast.error('Accès refusé : espace réservé aux administrateurs E-Courtier.');
      navigate('/dashboard');
    }
  }, [utilisateur, navigate]);

  const { data, isLoading, isError, refetch, isRefetching } = useQuery<AdminStatistiquesDTO>({
    queryKey: ['admin-statistiques', periode],
    queryFn: () => adminService.getStatistiques(periode),
    enabled: utilisateur?.role === 'ADMIN',
    staleTime: 60000,
  });

  if (utilisateur?.role !== 'ADMIN') return null;

  // Format currency FCFA
  const formatFCFA = (amount: number) => {
    return new Intl.NumberFormat('fr-FR').format(amount || 0) + ' FCFA';
  };

  // Prepare chart datasets
  const inscriptionData = data?.utilisateurs?.nouvellesInscriptions || [];

  const publicationStatusData = data?.annonces?.totalParStatutPublication
    ? Object.entries(data.annonces.totalParStatutPublication).map(([key, value]) => ({
        statut: key,
        total: value,
      }))
    : [];

  const signalementsMotifData = data?.moderation?.signalementsParMotif
    ? Object.entries(data.moderation.signalementsParMotif).map(([motif, count]) => ({
        motif: motif.replace(/_/g, ' '),
        count,
      }))
    : [];

  const conversionFunnelData = [
    {
      etape: 'RDV Effectués',
      valeur: data?.activiteCommerciale?.nombreRdvParStatut?.['EFFECTUE'] || 0,
      color: PALETTE.sahel,
    },
    {
      etape: 'Transactions Initiées',
      valeur: (data?.activiteCommerciale?.nombreRdvParStatut?.['EFFECTUE'] || 0) > 0 
        ? Math.round(((data?.activiteCommerciale?.nombreRdvParStatut?.['EFFECTUE'] || 0) * (data?.activiteCommerciale?.tauxConversionRdvToTransaction || 0)) / 100)
        : (data?.activiteCommerciale?.tauxConversionTransactionToBail || 0) > 0 ? 1 : 0,
      color: PALETTE.ocher,
    },
    {
      etape: 'Baux Signés Complètement',
      valeur: data?.activiteCommerciale?.montantTotalLibere
        ? Math.round(Number(data.activiteCommerciale.montantTotalLibere) / 5000)
        : 0,
      color: PALETTE.terracotta,
    },
  ];

  return (
    <div className="min-h-screen bg-sand-100/60 pb-16 pt-6 sm:pt-8 px-3 sm:px-6 lg:px-8 max-w-7xl mx-auto">
      {/* Admin Sub-Header Navigation */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="bg-terracotta-100 text-terracotta-800 text-xs font-bold px-2.5 py-0.5 rounded-full flex items-center gap-1">
              <ShieldCheck className="w-3.5 h-3.5 text-terracotta-600" />
              Espace Administration
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-display font-extrabold text-charcoal-900 tracking-tight">
            Tableau de Bord & Pilotage
          </h1>
          <p className="text-xs sm:text-sm text-charcoal-600 mt-1">
            Métriques d'activité, conversion commerciale, modération et performances en temps réel.
          </p>
        </div>

        {/* Tab switcher */}
        <div className="flex items-center gap-2 bg-white/80 p-1.5 rounded-2xl border border-argile-200/80 shadow-xs self-start md:self-auto">
          <Link
            to="/admin/dashboard"
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold bg-terracotta-500 text-white shadow-xs"
          >
            <LayoutDashboard className="w-4 h-4" />
            <span>Vue d'ensemble</span>
          </Link>
          <Link
            to="/admin/verification"
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold text-charcoal-700 hover:bg-sand-100 transition-colors"
          >
            <ShieldCheck className="w-4 h-4 text-sahel-600" />
            <span>Vérifications</span>
          </Link>
          <Link
            to="/admin/signalements"
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold text-charcoal-700 hover:bg-sand-100 transition-colors"
          >
            <Flag className="w-4 h-4 text-ocher-600" />
            <span>Signalements</span>
          </Link>
        </div>
      </div>

      {/* Period Filter Selector Toolbar */}
      <div className="flex items-center justify-between bg-white p-3 rounded-2xl border border-argile-200/80 mb-8 shadow-xs">
        <div className="flex items-center gap-2">
          <span className="text-xs font-bold text-charcoal-600 hidden sm:inline">Période d'analyse :</span>
          <div className="flex items-center gap-1">
            {(['7j', '30j', '90j', 'tout'] as const).map((p) => (
              <button
                key={p}
                onClick={() => setPeriode(p)}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                  periode === p
                    ? 'bg-charcoal-800 text-white shadow-xs'
                    : 'text-charcoal-600 hover:bg-sand-100'
                }`}
              >
                {p === '7j' ? '7 Derniers Jours' : p === '30j' ? '30 Jours' : p === '90j' ? '90 Jours' : 'Tout l\'historique'}
              </button>
            ))}
          </div>
        </div>

        <button
          onClick={() => refetch()}
          disabled={isRefetching}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold text-charcoal-700 hover:bg-sand-100 transition-colors"
          title="Rafraîchir les métriques"
        >
          <RefreshCw className={`w-3.5 h-3.5 text-terracotta-600 ${isRefetching ? 'animate-spin' : ''}`} />
          <span className="hidden sm:inline">Actualiser</span>
        </button>
      </div>

      {/* Skeleton Loading State */}
      {isLoading ? (
        <div className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {[1, 2, 3, 4].map((i) => (
              <Card key={i} className="p-5 bg-white border-argile-200">
                <Skeleton className="h-4 w-24 mb-3" />
                <Skeleton className="h-8 w-32 mb-2" />
                <Skeleton className="h-3 w-40" />
              </Card>
            ))}
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Card className="p-6 bg-white border-argile-200"><Skeleton className="h-64 w-full" /></Card>
            <Card className="p-6 bg-white border-argile-200"><Skeleton className="h-64 w-full" /></Card>
          </div>
        </div>
      ) : isError || !data ? (
        <Card className="p-8 text-center bg-white border-rose-200">
          <p className="text-rose-600 font-bold mb-3">Erreur lors du chargement des statistiques administrateur.</p>
          <button onClick={() => refetch()} className="px-4 py-2 bg-terracotta-500 text-white rounded-xl text-xs font-bold">
            Réessayer
          </button>
        </Card>
      ) : (
        <div className="space-y-8">
          {/* Key Metric Highlights Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
            
            {/* Card 1: Active Listings */}
            <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}>
              <Card className="p-5 bg-white border-argile-200/80 hover:border-terracotta-300 transition-all shadow-xs">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-bold text-charcoal-500 uppercase tracking-wider">Annonces Actives</span>
                  <div className="w-9 h-9 rounded-xl bg-terracotta-50 text-terracotta-600 flex items-center justify-center">
                    <Building className="w-5 h-5" />
                  </div>
                </div>
                <div className="text-2xl sm:text-3xl font-display font-extrabold text-charcoal-900">
                  {data.annonces?.totalParStatutPublication?.['DISPONIBLE'] || 0}
                </div>
                <div className="flex items-center justify-between mt-2 pt-2 border-t border-sand-200/80 text-xs text-charcoal-500">
                  <span>Vérifiées : {data.annonces?.totalParStatutVerification?.['VERIFIEE'] || 0}</span>
                  <span className="text-amber-600 font-semibold">{data.annonces?.totalParStatutVerification?.['EN_ATTENTE'] || 0} en attente</span>
                </div>
              </Card>
            </motion.div>

            {/* Card 2: Period Visits */}
            <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2, delay: 0.05 }}>
              <Card className="p-5 bg-white border-argile-200/80 hover:border-sahel-300 transition-all shadow-xs">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-bold text-charcoal-500 uppercase tracking-wider">RDV Effectués</span>
                  <div className="w-9 h-9 rounded-xl bg-sahel-50 text-sahel-700 flex items-center justify-center">
                    <Calendar className="w-5 h-5" />
                  </div>
                </div>
                <div className="text-2xl sm:text-3xl font-display font-extrabold text-charcoal-900">
                  {data.activiteCommerciale?.nombreRdvParStatut?.['EFFECTUE'] || 0}
                </div>
                <div className="flex items-center justify-between mt-2 pt-2 border-t border-sand-200/80 text-xs text-charcoal-500">
                  <span>Demandes : {data.activiteCommerciale?.nombreRdvParStatut?.['DEMANDE'] || 0}</span>
                  <span className="text-emerald-600 font-semibold">{data.activiteCommerciale?.nombreRdvParStatut?.['CONFIRME'] || 0} confirmés</span>
                </div>
              </Card>
            </motion.div>

            {/* Card 3: Global Conversion Rate */}
            <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2, delay: 0.1 }}>
              <Card className="p-5 bg-white border-argile-200/80 hover:border-ocher-300 transition-all shadow-xs">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-bold text-charcoal-500 uppercase tracking-wider">Taux Conversion RDV</span>
                  <div className="w-9 h-9 rounded-xl bg-ocher-50 text-ocher-700 flex items-center justify-center">
                    <TrendingUp className="w-5 h-5" />
                  </div>
                </div>
                <div className="text-2xl sm:text-3xl font-display font-extrabold text-charcoal-900">
                  {data.activiteCommerciale?.tauxConversionRdvToTransaction || 0}%
                </div>
                <div className="flex items-center justify-between mt-2 pt-2 border-t border-sand-200/80 text-xs text-charcoal-500">
                  <span>Tx → Bail :</span>
                  <span className="text-terracotta-600 font-bold">{data.activiteCommerciale?.tauxConversionTransactionToBail || 0}%</span>
                </div>
              </Card>
            </motion.div>

            {/* Card 4: Total Locked Escrow Amount */}
            <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2, delay: 0.15 }}>
              <Card className="p-5 bg-white border-argile-200/80 hover:border-charcoal-300 transition-all shadow-xs">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-bold text-charcoal-500 uppercase tracking-wider">Séquestre Bloqué</span>
                  <div className="w-9 h-9 rounded-xl bg-charcoal-50 text-charcoal-800 flex items-center justify-center">
                    <Lock className="w-5 h-5" />
                  </div>
                </div>
                <div className="text-xl sm:text-2xl font-display font-extrabold text-charcoal-900 truncate">
                  {formatFCFA(Number(data.activiteCommerciale?.montantTotalSequestreBloque || 0))}
                </div>
                <div className="flex items-center justify-between mt-2 pt-2 border-t border-sand-200/80 text-xs text-charcoal-500">
                  <span>Revenus libérés :</span>
                  <span className="text-emerald-700 font-extrabold">{formatFCFA(Number(data.activiteCommerciale?.montantTotalLibere || 0))}</span>
                </div>
              </Card>
            </motion.div>
          </div>

          {/* Charts Row 1: Time Series & Funnel */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            
            {/* Chart 1: Inscriptions Curve */}
            <Card className="p-6 bg-white border-argile-200/80 shadow-xs">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h3 className="text-base font-display font-bold text-charcoal-900 flex items-center gap-2">
                    <Users className="w-4 h-4 text-terracotta-600" />
                    Nouvelles Inscriptions dans le temps
                  </h3>
                  <p className="text-xs text-charcoal-500 mt-0.5">Série temporelle quotidienne d'acquisition d'utilisateurs</p>
                </div>
              </div>
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={inscriptionData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <defs>
                      <linearGradient id="gradientTerracotta" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor={PALETTE.terracotta} stopOpacity={0.4} />
                        <stop offset="95%" stopColor={PALETTE.terracotta} stopOpacity={0.0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0ebe1" />
                    <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#6e675f' }} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#6e675f' }} />
                    <Tooltip
                      contentStyle={{ backgroundColor: '#ffffff', borderRadius: '12px', border: '1px solid #e0d8c8', fontSize: '12px' }}
                      formatter={(value: any) => [`${value} utilisateur(s)`, 'Inscriptions']}
                    />
                    <Area type="monotone" dataKey="valeur" stroke={PALETTE.terracotta} strokeWidth={2.5} fillOpacity={1} fill="url(#gradientTerracotta)" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </Card>

            {/* Chart 2: Conversion Pipeline Funnel */}
            <Card className="p-6 bg-white border-argile-200/80 shadow-xs">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h3 className="text-base font-display font-bold text-charcoal-900 flex items-center gap-2">
                    <TrendingUp className="w-4 h-4 text-sahel-700" />
                    Entonnoir de Conversion Commerciale
                  </h3>
                  <p className="text-xs text-charcoal-500 mt-0.5">Visites → Transactions → Signature du bail complet</p>
                </div>
              </div>
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={conversionFunnelData} layout="vertical" margin={{ top: 10, right: 30, left: 40, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0ebe1" />
                    <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11, fill: '#6e675f' }} />
                    <YAxis type="category" dataKey="etape" tick={{ fontSize: 11, fill: '#262322', fontWeight: 600 }} width={120} />
                    <Tooltip
                      contentStyle={{ backgroundColor: '#ffffff', borderRadius: '12px', border: '1px solid #e0d8c8', fontSize: '12px' }}
                      formatter={(valeur: any) => [`${valeur}`, 'Volume']}
                    />
                    <Bar dataKey="valeur" radius={[0, 8, 8, 0]}>
                      {conversionFunnelData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </Card>
          </div>

          {/* Charts Row 2: Listings Status & Moderation Motifs */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

            {/* Chart 3: Listings Publication Status */}
            <Card className="p-6 bg-white border-argile-200/80 shadow-xs">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h3 className="text-base font-display font-bold text-charcoal-900 flex items-center gap-2">
                    <Building className="w-4 h-4 text-ocher-600" />
                    Annonces par Statut de Publication
                  </h3>
                  <p className="text-xs text-charcoal-500 mt-0.5">Répartition du parc immobilier E-Courtier</p>
                </div>
              </div>
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={publicationStatusData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f0ebe1" />
                    <XAxis dataKey="statut" tick={{ fontSize: 11, fill: '#6e675f' }} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#6e675f' }} />
                    <Tooltip
                      contentStyle={{ backgroundColor: '#ffffff', borderRadius: '12px', border: '1px solid #e0d8c8', fontSize: '12px' }}
                    />
                    <Bar dataKey="total" fill={PALETTE.sahel} radius={[8, 8, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </Card>

            {/* Chart 4: Signalements Pie Chart */}
            <Card className="p-6 bg-white border-argile-200/80 shadow-xs">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h3 className="text-base font-display font-bold text-charcoal-900 flex items-center gap-2">
                    <Flag className="w-4 h-4 text-terracotta-600" />
                    Modération : Signalements par Motif
                  </h3>
                  <p className="text-xs text-charcoal-500 mt-0.5">Catégories de signalements soumis par les utilisateurs</p>
                </div>
              </div>
              <div className="h-64 w-full">
                {signalementsMotifData.length === 0 || signalementsMotifData.every(d => d.count === 0) ? (
                  <div className="h-full flex items-center justify-center text-xs text-charcoal-500 italic">
                    Aucun signalement enregistré sur cette période.
                  </div>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={signalementsMotifData}
                        dataKey="count"
                        nameKey="motif"
                        cx="50%"
                        cy="50%"
                        outerRadius={80}
                        innerRadius={45}
                        paddingAngle={4}
                        label={({ name, percent }: { name?: string; percent?: number }) => `${name || ''} (${((percent || 0) * 100).toFixed(0)}%)`}
                      >
                        {signalementsMotifData.map((_, index) => (
                          <Cell key={`pie-cell-${index}`} fill={PALETTE.pieColors[index % PALETTE.pieColors.length]} />
                        ))}
                      </Pie>
                      <Tooltip contentStyle={{ backgroundColor: '#ffffff', borderRadius: '12px', border: '1px solid #e0d8c8', fontSize: '12px' }} />
                    </PieChart>
                  </ResponsiveContainer>
                )}
              </div>
            </Card>
          </div>

          {/* Additional Metrics Grid: IA Assistant, Top Locations & Moderation Details */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">

            {/* AI Assistant Stats */}
            <Card className="p-5 bg-white border-argile-200/80 shadow-xs">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-8 h-8 rounded-lg bg-terracotta-100 text-terracotta-700 flex items-center justify-center">
                  <Bot className="w-4 h-4" />
                </div>
                <h4 className="font-display font-bold text-sm text-charcoal-900">Assistant IA Recherche</h4>
              </div>
              <div className="space-y-3">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-charcoal-600">Conversations initiées :</span>
                  <span className="font-bold text-charcoal-900">{data.assistantIA?.nombreConversations || 0}</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-charcoal-600">Taux conversion shortlist :</span>
                  <span className="font-bold text-terracotta-600">{data.assistantIA?.tauxConversationsShortlistNonVide || 0}%</span>
                </div>
              </div>
            </Card>

            {/* Top 5 Locations */}
            <Card className="p-5 bg-white border-argile-200/80 shadow-xs">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-8 h-8 rounded-lg bg-sahel-100 text-sahel-800 flex items-center justify-center">
                  <Building className="w-4 h-4" />
                </div>
                <h4 className="font-display font-bold text-sm text-charcoal-900">Top 5 Quartiers Prisés</h4>
              </div>
              <div className="space-y-2">
                {(data.annonces?.repartitionParVilleQuartier || []).length === 0 ? (
                  <p className="text-xs text-charcoal-400 italic">Aucune donnée géographique.</p>
                ) : (
                  data.annonces?.repartitionParVilleQuartier?.map((loc, idx) => (
                    <div key={idx} className="flex items-center justify-between text-xs">
                      <span className="text-charcoal-700 truncate">{loc.ville} - <span className="font-semibold">{loc.quartier}</span></span>
                      <span className="font-bold text-sahel-700 bg-sahel-50 px-2 py-0.5 rounded-full">{loc.total} annonce(s)</span>
                    </div>
                  ))
                )}
              </div>
            </Card>

            {/* Verification Performance */}
            <Card className="p-5 bg-white border-argile-200/80 shadow-xs">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-8 h-8 rounded-lg bg-ocher-100 text-ocher-800 flex items-center justify-center">
                  <Star className="w-4 h-4" />
                </div>
                <h4 className="font-display font-bold text-sm text-charcoal-900">Qualité & Délais Admin</h4>
              </div>
              <div className="space-y-3">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-charcoal-600">Temps moyen décision :</span>
                  <span className="font-bold text-charcoal-900">{data.annonces?.tempsMoyenDecisionMinutes || 0} minute(s)</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-charcoal-600">Note moyenne avis clients :</span>
                  <span className="font-bold text-amber-600 flex items-center gap-1">
                    ★ {data.moderation?.noteMoyenneGlobale || 0} / 5
                  </span>
                </div>
              </div>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
};
