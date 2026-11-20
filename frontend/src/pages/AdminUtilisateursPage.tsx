import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Users,
  Search,
  UserPlus,
  ShieldCheck,
  UserCog,
  Power,
  Copy,
  Check,
  ChevronLeft,
  ChevronRight,
  AlertTriangle,
  Building2,
  Briefcase,
  Home,
} from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { adminService } from '../services/admin.service';
import { AdminCreateUtilisateurResponse, AdminStatistiquesDTO, PageResponse, UtilisateurDTO } from '../types';
import { AdminNavTabs } from '../components/admin/AdminNavTabs';
import { Card, CardBody } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Input } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { Skeleton } from '../components/ui/Skeleton';
import { toast } from 'sonner';
import { getErrorMessage } from '../lib/errors';

type RoleType = 'ADMIN' | 'AGENT' | 'PROPRIETAIRE' | 'LOCATAIRE';

export const AdminUtilisateursPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { utilisateur: currentAdmin } = useAuthStore();

  const [recherche, setRecherche] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('TOUS');
  const [statutFilter, setStatutFilter] = useState<'TOUS' | 'ACTIF' | 'INACTIF'>('TOUS');
  const [page, setPage] = useState(0);

  // Create modal state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [nom, setNom] = useState('');
  const [prenom, setPrenom] = useState('');
  const [email, setEmail] = useState('');
  const [telephone, setTelephone] = useState('+221 77 000 00 00');
  const [newRole, setNewRole] = useState<RoleType>('AGENT');
  const [motDePasseTemporaire, setMotDePasseTemporaire] = useState('');
  const [createdResult, setCreatedResult] = useState<AdminCreateUtilisateurResponse | null>(null);
  const [copiedPassword, setCopiedPassword] = useState(false);

  // Change role modal state
  const [roleTarget, setRoleTarget] = useState<UtilisateurDTO | null>(null);
  const [selectedRole, setSelectedRole] = useState<RoleType>('AGENT');

  // Deactivate confirmation modal state (sensitive action)
  const [deactivateTarget, setDeactivateTarget] = useState<UtilisateurDTO | null>(null);

  useEffect(() => {
    if (!currentAdmin) {
      navigate('/connexion');
    } else if (currentAdmin.role !== 'ADMIN') {
      toast.error('Accès refusé : espace réservé aux administrateurs E-Courtier.');
      navigate('/dashboard');
    }
  }, [currentAdmin, navigate]);

  const actifParam =
    statutFilter === 'ACTIF' ? true : statutFilter === 'INACTIF' ? false : undefined;

  // Real users query
  const { data, isLoading, isError, refetch } = useQuery<PageResponse<UtilisateurDTO>>({
    queryKey: ['admin-utilisateurs', page, roleFilter, statutFilter, recherche],
    queryFn: () =>
      adminService.getUtilisateurs({
        page,
        size: 15,
        role: roleFilter,
        actif: actifParam,
        recherche,
      }),
    enabled: currentAdmin?.role === 'ADMIN',
  });

  // Real stats query for KPI metrics
  const { data: statsData } = useQuery<AdminStatistiquesDTO>({
    queryKey: ['admin-statistiques-kpi'],
    queryFn: () => adminService.getStatistiques('30j'),
    enabled: currentAdmin?.role === 'ADMIN',
    staleTime: 60_000,
  });

  const createMutation = useMutation({
    mutationFn: adminService.creerUtilisateur,
    onSuccess: (res) => {
      setCreatedResult(res);
      setCopiedPassword(false);
      toast.success(`Compte ${res.utilisateur.role} créé pour ${res.utilisateur.email}`);
      queryClient.invalidateQueries({ queryKey: ['admin-utilisateurs'] });
      queryClient.invalidateQueries({ queryKey: ['admin-statistiques-kpi'] });
      setNom('');
      setPrenom('');
      setEmail('');
      setTelephone('+221 77 000 00 00');
      setNewRole('AGENT');
      setMotDePasseTemporaire('');
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, 'Échec de la création du compte'));
    },
  });

  const roleMutation = useMutation({
    mutationFn: ({ id, role }: { id: string; role: RoleType }) =>
      adminService.changerRoleUtilisateur(id, role),
    onSuccess: (updated) => {
      toast.success(`Rôle de ${updated.prenom} ${updated.nom} changé en ${updated.role}`);
      queryClient.invalidateQueries({ queryKey: ['admin-utilisateurs'] });
      queryClient.invalidateQueries({ queryKey: ['admin-statistiques-kpi'] });
      setRoleTarget(null);
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, 'Impossible de modifier le rôle'));
    },
  });

  const statutMutation = useMutation({
    mutationFn: ({ id, actif }: { id: string; actif: boolean }) =>
      adminService.changerStatutUtilisateur(id, actif),
    onSuccess: (updated) => {
      toast.success(
        updated.actif
          ? `Compte de ${updated.prenom} ${updated.nom} réactivé`
          : `Compte de ${updated.prenom} ${updated.nom} désactivé immédiatement`
      );
      queryClient.invalidateQueries({ queryKey: ['admin-utilisateurs'] });
      queryClient.invalidateQueries({ queryKey: ['admin-statistiques-kpi'] });
      setDeactivateTarget(null);
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, 'Impossible de modifier le statut du compte'));
    },
  });

  if (currentAdmin?.role !== 'ADMIN') return null;

  const users = data?.content || [];

  const getRoleBadgeVariant = (role: RoleType): 'terracotta' | 'ocher' | 'sahel' | 'neutral' => {
    switch (role) {
      case 'ADMIN':
        return 'terracotta';
      case 'AGENT':
        return 'ocher';
      case 'PROPRIETAIRE':
        return 'sahel';
      default:
        return 'neutral';
    }
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate({
      nom,
      prenom,
      email,
      telephone,
      role: newRole,
      motDePasseTemporaire: motDePasseTemporaire.trim() || undefined,
    });
  };

  const handleCopyTempPassword = async () => {
    if (!createdResult?.motDePasseTemporaire) return;
    try {
      await navigator.clipboard.writeText(createdResult.motDePasseTemporaire);
      setCopiedPassword(true);
      toast.success('Mot de passe temporaire copié dans le presse-papiers');
    } catch {
      toast.info('Sélectionnez et copiez le mot de passe affiché.');
    }
  };

  // KPI Calculations from real data
  const totalUsersCount = data?.totalElements ?? (users.length > 0 ? users.length : 0);
  const totalParRole = statsData?.utilisateurs?.totalParRole;
  const agentsCount = totalParRole?.['AGENT'] ?? users.filter((u) => u.role === 'AGENT').length;
  const proprietairesCount = totalParRole?.['PROPRIETAIRE'] ?? users.filter((u) => u.role === 'PROPRIETAIRE').length;
  const locatairesCount = totalParRole?.['LOCATAIRE'] ?? users.filter((u) => u.role === 'LOCATAIRE').length;

  return (
    <div className="min-h-screen bg-sand-100/60 pb-16 pt-6 sm:pt-8 px-3 sm:px-6 lg:px-8 max-w-7xl mx-auto space-y-6">
      {/* Header & Admin Nav */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="bg-terracotta-100 text-terracotta-800 text-xs font-bold px-2.5 py-0.5 rounded-full inline-flex items-center gap-1">
              <ShieldCheck className="w-3.5 h-3.5 text-terracotta-600" />
              Espace Administration
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-display font-extrabold text-charcoal-900 tracking-tight">
            Gestion des Utilisateurs & Agents
          </h1>
          <p className="text-xs sm:text-sm text-charcoal-600 mt-1">
            Supervisez les comptes du réseau, attribuez les rôles professionnels et gérez les autorisations d'accès.
          </p>
        </div>

        <AdminNavTabs />
      </div>

      {/* KPI Stats Cards (Real Data) */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4" aria-label="Indicateurs clés">
        <Card className="bg-white border border-argile-200/90 shadow-warm-card">
          <CardBody className="p-3.5 sm:p-4 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-sand-100 text-charcoal-800 flex items-center justify-center shrink-0">
              <Users className="w-5 h-5 text-terracotta-600" />
            </div>
            <div className="min-w-0">
              <p className="text-[11px] font-bold text-charcoal-500 uppercase tracking-wider truncate">
                Total Comptes
              </p>
              <p className="text-lg sm:text-2xl font-display font-extrabold text-charcoal-900">
                {totalUsersCount}
              </p>
            </div>
          </CardBody>
        </Card>

        <Card className="bg-white border border-argile-200/90 shadow-warm-card">
          <CardBody className="p-3.5 sm:p-4 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-ocher-50 text-ocher-800 flex items-center justify-center shrink-0">
              <Briefcase className="w-5 h-5 text-ocher-700" />
            </div>
            <div className="min-w-0">
              <p className="text-[11px] font-bold text-charcoal-500 uppercase tracking-wider truncate">
                Agents Agréés
              </p>
              <p className="text-lg sm:text-2xl font-display font-extrabold text-ocher-800">
                {agentsCount}
              </p>
            </div>
          </CardBody>
        </Card>

        <Card className="bg-white border border-argile-200/90 shadow-warm-card">
          <CardBody className="p-3.5 sm:p-4 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-sahel-50 text-sahel-800 flex items-center justify-center shrink-0">
              <Building2 className="w-5 h-5 text-sahel-700" />
            </div>
            <div className="min-w-0">
              <p className="text-[11px] font-bold text-charcoal-500 uppercase tracking-wider truncate">
                Propriétaires
              </p>
              <p className="text-lg sm:text-2xl font-display font-extrabold text-sahel-800">
                {proprietairesCount}
              </p>
            </div>
          </CardBody>
        </Card>

        <Card className="bg-white border border-argile-200/90 shadow-warm-card">
          <CardBody className="p-3.5 sm:p-4 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-sand-100 text-charcoal-700 flex items-center justify-center shrink-0">
              <Home className="w-5 h-5 text-charcoal-600" />
            </div>
            <div className="min-w-0">
              <p className="text-[11px] font-bold text-charcoal-500 uppercase tracking-wider truncate">
                Locataires
              </p>
              <p className="text-lg sm:text-2xl font-display font-extrabold text-charcoal-900">
                {locatairesCount}
              </p>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Toolbar: Search, Filters & Create Button */}
      <Card className="bg-white border border-argile-200/90 shadow-warm-card">
        <CardBody className="p-4 flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <div className="flex-1 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
            <div className="relative flex-1">
              <Search className="w-4 h-4 text-charcoal-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={recherche}
                onChange={(e) => {
                  setRecherche(e.target.value);
                  setPage(0);
                }}
                placeholder="Rechercher par nom, prénom, email ou téléphone..."
                className="w-full pl-9 pr-3 py-2 rounded-xl border border-argile-200 text-xs text-charcoal-900 placeholder:text-charcoal-400 focus:ring-2 focus:ring-terracotta-500/20 focus:border-terracotta-500 outline-none transition-colors min-h-[40px]"
              />
            </div>

            <select
              aria-label="Filtrer par rôle"
              value={roleFilter}
              onChange={(e) => {
                setRoleFilter(e.target.value);
                setPage(0);
              }}
              className="px-3 py-2 rounded-xl border border-argile-200 text-xs font-semibold text-charcoal-800 bg-sand-50/80 focus:ring-2 focus:ring-terracotta-500/20 focus:border-terracotta-500 outline-none min-h-[40px]"
            >
              <option value="TOUS">Tous les rôles</option>
              <option value="AGENT">AGENT (Courtiers)</option>
              <option value="PROPRIETAIRE">PROPRIETAIRE</option>
              <option value="LOCATAIRE">LOCATAIRE</option>
              <option value="ADMIN">ADMIN</option>
            </select>

            <select
              aria-label="Filtrer par statut"
              value={statutFilter}
              onChange={(e) => {
                setStatutFilter(e.target.value as 'TOUS' | 'ACTIF' | 'INACTIF');
                setPage(0);
              }}
              className="px-3 py-2 rounded-xl border border-argile-200 text-xs font-semibold text-charcoal-800 bg-sand-50/80 focus:ring-2 focus:ring-terracotta-500/20 focus:border-terracotta-500 outline-none min-h-[40px]"
            >
              <option value="TOUS">Tous les statuts</option>
              <option value="ACTIF">Actifs uniquement</option>
              <option value="INACTIF">Désactivés uniquement</option>
            </select>
          </div>

          <Button
            variant="primary"
            size="sm"
            leftIcon={<UserPlus className="w-4 h-4" />}
            onClick={() => {
              setCreatedResult(null);
              setCreateModalOpen(true);
            }}
            className="shrink-0 justify-center min-h-[40px]"
          >
            Créer un compte
          </Button>
        </CardBody>
      </Card>

      {/* Users Display: Desktop Table + Tablet/Mobile Cards */}
      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} variant="card" className="h-20" />
          ))}
        </div>
      ) : isError ? (
        <Card className="p-8 text-center bg-white border border-argile-200/90 shadow-warm-card space-y-3">
          <p className="text-xs text-charcoal-600">Erreur lors du chargement des comptes utilisateurs.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            Réessayer
          </Button>
        </Card>
      ) : users.length === 0 ? (
        <Card className="p-12 text-center bg-white border border-argile-200/90 shadow-warm-card space-y-2">
          <Users className="w-10 h-10 text-charcoal-400 mx-auto" />
          <h3 className="font-display font-bold text-base text-charcoal-900">Aucun utilisateur trouvé</h3>
          <p className="text-xs text-charcoal-500">
            Modifiez vos critères de recherche ou créez un nouveau compte agent.
          </p>
        </Card>
      ) : (
        <>
          {/* DESKTOP TABLE (≥ 1024px) */}
          <div className="hidden lg:block bg-white rounded-2xl border border-argile-200/90 shadow-warm-card overflow-x-auto">
            <table className="w-full text-left border-collapse min-w-[860px]">
              <thead>
                <tr className="bg-sand-100/80 border-b border-argile-200/90 text-[11px] uppercase tracking-wider font-extrabold text-charcoal-600">
                  <th className="py-3.5 px-4">Utilisateur</th>
                  <th className="py-3.5 px-4">Contact</th>
                  <th className="py-3.5 px-4">Rôle</th>
                  <th className="py-3.5 px-4">Statut</th>
                  <th className="py-3.5 px-4">Inscription</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand-200/80 text-xs">
                {users.map((u) => {
                  const isSelf = u.id === currentAdmin.id || u.email === currentAdmin.email;
                  const isActif = u.actif !== false;
                  return (
                    <tr key={u.id} className="hover:bg-sand-50/70 transition-colors">
                      <td className="py-3.5 px-4">
                        <div className="font-display font-bold text-charcoal-900">
                          {u.prenom} {u.nom}
                          {isSelf && (
                            <span className="ml-1.5 text-[10px] font-bold text-terracotta-600">(Vous)</span>
                          )}
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="text-charcoal-800 font-medium">{u.email}</div>
                        <div className="text-[11px] text-charcoal-500">{u.telephone}</div>
                      </td>
                      <td className="py-3.5 px-4">
                        <Badge variant={getRoleBadgeVariant(u.role)}>{u.role}</Badge>
                      </td>
                      <td className="py-3.5 px-4">
                        {isActif ? (
                          <Badge variant="success">Actif</Badge>
                        ) : (
                          <Badge variant="rose">Inactif</Badge>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-charcoal-500">
                        {u.dateCreation ? new Date(u.dateCreation).toLocaleDateString('fr-FR') : '—'}
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={isSelf}
                            title={isSelf ? 'Vous ne pouvez pas retirer votre propre rôle ADMIN' : 'Changer le rôle'}
                            onClick={() => {
                              setRoleTarget(u);
                              setSelectedRole(u.role);
                            }}
                            leftIcon={<UserCog className="w-3.5 h-3.5" />}
                          >
                            Changer le rôle
                          </Button>
                          <Button
                            variant={isActif ? 'ghost' : 'primary'}
                            size="sm"
                            disabled={isSelf || statutMutation.isPending}
                            title={isSelf ? 'Vous ne pouvez pas désactiver votre propre compte' : undefined}
                            onClick={() => {
                              if (isActif) {
                                setDeactivateTarget(u);
                              } else {
                                statutMutation.mutate({ id: u.id, actif: true });
                              }
                            }}
                            className={
                              isActif
                                ? 'text-rose-700 hover:bg-rose-50 border border-rose-200'
                                : 'bg-emerald-600 hover:bg-emerald-700 text-white'
                            }
                            leftIcon={<Power className="w-3.5 h-3.5" />}
                          >
                            {isActif ? 'Désactiver' : 'Réactiver'}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* TABLET & MOBILE CARDS (< 1024px: Resolves the 768x1024 924px table clipping issue!) */}
          <div className="lg:hidden space-y-3">
            {users.map((u) => {
              const isSelf = u.id === currentAdmin.id || u.email === currentAdmin.email;
              const isActif = u.actif !== false;
              return (
                <Card key={u.id} className="bg-white border border-argile-200/90 shadow-warm-card">
                  <CardBody className="p-4 space-y-3">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="font-display font-bold text-sm text-charcoal-900 truncate">
                          {u.prenom} {u.nom}{' '}
                          {isSelf && <span className="text-terracotta-600 text-xs font-semibold">(Vous)</span>}
                        </h3>
                        <p className="text-xs text-charcoal-700 break-all mt-0.5">{u.email}</p>
                        <p className="text-[11px] text-charcoal-500 mt-0.5">{u.telephone}</p>
                        {u.dateCreation && (
                          <p className="text-[10px] text-charcoal-400 mt-1">
                            Inscrit le {new Date(u.dateCreation).toLocaleDateString('fr-FR')}
                          </p>
                        )}
                      </div>
                      <div className="flex flex-col items-end gap-1.5 shrink-0">
                        <Badge variant={getRoleBadgeVariant(u.role)}>{u.role}</Badge>
                        {isActif ? <Badge variant="success">Actif</Badge> : <Badge variant="rose">Inactif</Badge>}
                      </div>
                    </div>

                    {/* Action buttons (full tap targets >= 44px, no horizontal scroll needed) */}
                    <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 pt-3 border-t border-sand-200/80">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={isSelf}
                        className="flex-1 justify-center text-xs min-h-[44px]"
                        leftIcon={<UserCog className="w-3.5 h-3.5" />}
                        onClick={() => {
                          setRoleTarget(u);
                          setSelectedRole(u.role);
                        }}
                      >
                        Changer le rôle
                      </Button>
                      <Button
                        variant={isActif ? 'ghost' : 'primary'}
                        size="sm"
                        disabled={isSelf || statutMutation.isPending}
                        className={`flex-1 justify-center text-xs min-h-[44px] ${
                          isActif
                            ? 'text-rose-700 border border-rose-200 hover:bg-rose-50'
                            : 'bg-emerald-600 hover:bg-emerald-700 text-white'
                        }`}
                        leftIcon={<Power className="w-3.5 h-3.5" />}
                        onClick={() => {
                          if (isActif) {
                            setDeactivateTarget(u);
                          } else {
                            statutMutation.mutate({ id: u.id, actif: true });
                          }
                        }}
                      >
                        {isActif ? 'Désactiver' : 'Réactiver'}
                      </Button>
                    </div>
                  </CardBody>
                </Card>
              );
            })}
          </div>

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <div className="flex flex-wrap items-center justify-between gap-3 pt-2 text-xs font-semibold text-charcoal-700">
              <span>
                Page {data.number + 1} sur {data.totalPages} ({data.totalElements} comptes)
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={data.first}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  leftIcon={<ChevronLeft className="w-4 h-4" />}
                  className="min-h-[38px]"
                >
                  Précédent
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={data.last}
                  onClick={() => setPage((p) => p + 1)}
                  rightIcon={<ChevronRight className="w-4 h-4" />}
                  className="min-h-[38px]"
                >
                  Suivant
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      {/* MODAL: CONFIRMER LA DÉSACTIVATION (Action sensible) */}
      <Modal
        isOpen={!!deactivateTarget}
        onClose={() => setDeactivateTarget(null)}
        title="Désactiver le compte utilisateur"
        size="sm"
      >
        {deactivateTarget && (
          <div className="space-y-4">
            <div className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 flex items-start gap-3">
              <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
              <div className="text-xs text-rose-800 space-y-1">
                <p className="font-bold">Attention — Suspension d'accès immédiate</p>
                <p>
                  L'utilisateur sera immédiatement déconnecté et ne pourra plus accéder à son compte ni publier d'annonces.
                </p>
              </div>
            </div>

            <p className="text-xs text-charcoal-700">
              Voulez-vous vraiment désactiver le compte de{' '}
              <strong className="text-charcoal-900">
                {deactivateTarget.prenom} {deactivateTarget.nom}
              </strong>{' '}
              ({deactivateTarget.email}) ?
            </p>

            <div className="flex justify-end gap-2 pt-3 border-t border-sand-200">
              <Button
                variant="ghost"
                onClick={() => setDeactivateTarget(null)}
                disabled={statutMutation.isPending}
              >
                Annuler
              </Button>
              <Button
                variant="primary"
                className="bg-rose-600 hover:bg-rose-700 text-white"
                isLoading={statutMutation.isPending}
                onClick={() => statutMutation.mutate({ id: deactivateTarget.id, actif: false })}
              >
                Confirmer la désactivation
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* MODAL: CRÉER UN COMPTE */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => {
          setCreateModalOpen(false);
          setCreatedResult(null);
        }}
        title={createdResult ? 'Compte créé — Mot de passe temporaire' : 'Créer un compte utilisateur'}
        size="md"
      >
        {createdResult ? (
          <div className="space-y-4">
            <div className="p-4 rounded-2xl bg-amber-50 border border-amber-300 space-y-2">
              <div className="flex items-center gap-2 text-amber-900 font-bold text-xs">
                <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
                <span>Affichage unique — Copiez ce mot de passe maintenant</span>
              </div>
              <p className="text-xs text-amber-800">
                Ce mot de passe temporaire ne sera plus jamais affiché après la fermeture de cette fenêtre. Le titulaire pourra le modifier dans <strong>Mon compte</strong>.
              </p>
            </div>

            <div className="p-4 rounded-2xl bg-sand-100 border border-argile-300 space-y-2 text-xs">
              <div>
                <span className="text-charcoal-500">Compte :</span>{' '}
                <strong className="text-charcoal-900 break-words">
                  {createdResult.utilisateur.prenom} {createdResult.utilisateur.nom} (<span className="break-all">{createdResult.utilisateur.email}</span>)
                </strong>
              </div>
              <div>
                <span className="text-charcoal-500">Rôle attribué :</span>{' '}
                <Badge variant={getRoleBadgeVariant(createdResult.utilisateur.role)}>
                  {createdResult.utilisateur.role}
                </Badge>
              </div>
              <div className="pt-2">
                <span className="text-charcoal-600 font-semibold block mb-1">Mot de passe temporaire :</span>
                <div className="flex flex-wrap items-center justify-between gap-2 p-3 rounded-xl bg-white border border-argile-300">
                  <code className="font-mono font-extrabold text-sm text-sahel-900 select-all break-all min-w-0">
                    {createdResult.motDePasseTemporaire}
                  </code>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleCopyTempPassword}
                    className="shrink-0"
                    leftIcon={copiedPassword ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                  >
                    {copiedPassword ? 'Copié' : 'Copier'}
                  </Button>
                </div>
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <Button
                variant="primary"
                onClick={() => {
                  setCreateModalOpen(false);
                  setCreatedResult(null);
                }}
              >
                J'ai noté le mot de passe
              </Button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleCreateSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                label="Prénom"
                required
                value={prenom}
                onChange={(e) => setPrenom(e.target.value)}
                placeholder="Ex: Fatou"
              />
              <Input
                label="Nom"
                required
                value={nom}
                onChange={(e) => setNom(e.target.value)}
                placeholder="Ex: Sarr"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                label="Adresse Email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="agent.sarr@ecourtier.sn"
              />
              <Input
                label="Téléphone"
                type="tel"
                required
                value={telephone}
                onChange={(e) => setTelephone(e.target.value)}
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1">Rôle du compte</label>
              <select
                value={newRole}
                onChange={(e) => setNewRole(e.target.value as RoleType)}
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-xs font-semibold bg-white focus:ring-2 focus:ring-terracotta-500 outline-none"
              >
                <option value="AGENT">AGENT — Agent immobilier / Courtier agréé</option>
                <option value="PROPRIETAIRE">PROPRIETAIRE — Bailleur / Vendeur</option>
                <option value="LOCATAIRE">LOCATAIRE — Locataire / Acheteur</option>
                <option value="ADMIN">ADMIN — Administrateur plateforme</option>
              </select>
            </div>

            <div>
              <Input
                label="Mot de passe temporaire (optionnel)"
                type="text"
                value={motDePasseTemporaire}
                onChange={(e) => setMotDePasseTemporaire(e.target.value)}
                placeholder="Laisser vide pour générer automatiquement (min. 8 car., 1 maj., 1 chiffre)"
              />
            </div>

            <div className="flex justify-end gap-2 pt-3 border-t border-sand-200">
              <Button type="button" variant="ghost" onClick={() => setCreateModalOpen(false)}>
                Annuler
              </Button>
              <Button type="submit" variant="primary" isLoading={createMutation.isPending}>
                Créer le compte
              </Button>
            </div>
          </form>
        )}
      </Modal>

      {/* MODAL: CHANGER LE RÔLE */}
      <Modal
        isOpen={!!roleTarget}
        onClose={() => setRoleTarget(null)}
        title="Changer le rôle de l'utilisateur"
        size="sm"
      >
        {roleTarget && (
          <div className="space-y-4">
            <p className="text-xs text-charcoal-600">
              Sélectionnez le nouveau rôle pour{' '}
              <strong>
                {roleTarget.prenom} {roleTarget.nom}
              </strong>{' '}
              ({roleTarget.email}).
            </p>

            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1">Nouveau rôle</label>
              <select
                value={selectedRole}
                onChange={(e) => setSelectedRole(e.target.value as RoleType)}
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-xs font-semibold bg-white focus:ring-2 focus:ring-terracotta-500 outline-none"
              >
                <option value="AGENT">AGENT</option>
                <option value="PROPRIETAIRE">PROPRIETAIRE</option>
                <option value="LOCATAIRE">LOCATAIRE</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </div>

            <div className="flex justify-end gap-2 pt-3 border-t border-sand-200">
              <Button variant="ghost" onClick={() => setRoleTarget(null)}>
                Annuler
              </Button>
              <Button
                variant="primary"
                isLoading={roleMutation.isPending}
                onClick={() => roleMutation.mutate({ id: roleTarget.id, role: selectedRole })}
              >
                Enregistrer le rôle
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default AdminUtilisateursPage;
