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
} from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { adminService } from '../services/admin.service';
import { AdminCreateUtilisateurResponse, PageResponse, UtilisateurDTO } from '../types';
import { AdminNavTabs } from '../components/admin/AdminNavTabs';
import { Card, CardBody } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Input } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { Skeleton } from '../components/ui/Skeleton';
import { toast } from 'sonner';

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

  const createMutation = useMutation({
    mutationFn: adminService.creerUtilisateur,
    onSuccess: (res) => {
      setCreatedResult(res);
      setCopiedPassword(false);
      toast.success(`Compte ${res.utilisateur.role} créé pour ${res.utilisateur.email}`);
      queryClient.invalidateQueries({ queryKey: ['admin-utilisateurs'] });
      setNom('');
      setPrenom('');
      setEmail('');
      setTelephone('+221 77 000 00 00');
      setNewRole('AGENT');
      setMotDePasseTemporaire('');
    },
    onError: (err: any) => {
      toast.error(err.message || 'Échec de la création du compte');
    },
  });

  const roleMutation = useMutation({
    mutationFn: ({ id, role }: { id: string; role: RoleType }) =>
      adminService.changerRoleUtilisateur(id, role),
    onSuccess: (updated) => {
      toast.success(`Rôle de ${updated.prenom} ${updated.nom} changé en ${updated.role}`);
      queryClient.invalidateQueries({ queryKey: ['admin-utilisateurs'] });
      setRoleTarget(null);
    },
    onError: (err: any) => {
      toast.error(err.message || 'Impossible de modifier le rôle');
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
    },
    onError: (err: any) => {
      toast.error(err.message || 'Impossible de modifier le statut du compte');
    },
  });

  if (currentAdmin?.role !== 'ADMIN') return null;

  const users = data?.content || [];

  const getRoleBadgeVariant = (role: RoleType) => {
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
    } catch (_) {
      toast.info('Sélectionnez et copiez le mot de passe affiché.');
    }
  };

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
            Créez des comptes agents immobiliers agréés, gérez les rôles et suspendez les comptes en temps réel.
          </p>
        </div>

        <AdminNavTabs />
      </div>

      {/* Toolbar: Search, Filters & Create Button */}
      <Card className="bg-white border border-argile-200 shadow-xs">
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
                className="w-full pl-9 pr-3 py-2 rounded-xl border border-sand-300 text-xs focus:ring-2 focus:ring-terracotta-500 outline-none"
              />
            </div>

            <select
              aria-label="Filtrer par rôle"
              value={roleFilter}
              onChange={(e) => {
                setRoleFilter(e.target.value);
                setPage(0);
              }}
              className="px-3 py-2 rounded-xl border border-sand-300 text-xs font-semibold text-charcoal-800 bg-sand-50 focus:ring-2 focus:ring-terracotta-500 outline-none"
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
              className="px-3 py-2 rounded-xl border border-sand-300 text-xs font-semibold text-charcoal-800 bg-sand-50 focus:ring-2 focus:ring-terracotta-500 outline-none"
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
            className="shrink-0 justify-center"
          >
            Créer un compte
          </Button>
        </CardBody>
      </Card>

      {/* Users Table (Desktop) & Cards (Mobile) */}
      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} variant="card" className="h-20" />
          ))}
        </div>
      ) : isError ? (
        <Card className="p-8 text-center bg-white border border-argile-200 space-y-3">
          <p className="text-xs text-charcoal-600">Erreur lors du chargement des comptes utilisateurs.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            Réessayer
          </Button>
        </Card>
      ) : users.length === 0 ? (
        <Card className="p-12 text-center bg-white border border-argile-200 space-y-2">
          <Users className="w-10 h-10 text-charcoal-400 mx-auto" />
          <h3 className="font-display font-bold text-base text-charcoal-900">Aucun utilisateur trouvé</h3>
          <p className="text-xs text-charcoal-500">Modifiez vos critères de recherche ou créez un nouveau compte.</p>
        </Card>
      ) : (
        <>
          {/* DESKTOP TABLE */}
          <div className="hidden md:block bg-white rounded-2xl border border-argile-200 shadow-warm-card overflow-hidden">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-sand-100/80 border-b border-argile-200 text-[11px] uppercase tracking-wider font-extrabold text-charcoal-600">
                  <th className="py-3.5 px-4">Utilisateur</th>
                  <th className="py-3.5 px-4">Contact</th>
                  <th className="py-3.5 px-4">Rôle</th>
                  <th className="py-3.5 px-4">Statut</th>
                  <th className="py-3.5 px-4">Inscription</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand-200 text-xs">
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
                        <Badge variant={getRoleBadgeVariant(u.role) as any}>{u.role}</Badge>
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
                            onClick={() => statutMutation.mutate({ id: u.id, actif: !isActif })}
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

          {/* MOBILE CARDS */}
          <div className="md:hidden space-y-3">
            {users.map((u) => {
              const isSelf = u.id === currentAdmin.id || u.email === currentAdmin.email;
              const isActif = u.actif !== false;
              return (
                <Card key={u.id} className="bg-white border border-argile-200">
                  <CardBody className="p-4 space-y-3">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <h3 className="font-display font-bold text-sm text-charcoal-900">
                          {u.prenom} {u.nom} {isSelf && <span className="text-terracotta-600 text-xs">(Vous)</span>}
                        </h3>
                        <p className="text-xs text-charcoal-600 break-all">{u.email}</p>
                        <p className="text-[11px] text-charcoal-500">{u.telephone}</p>
                      </div>
                      <div className="flex flex-col items-end gap-1 shrink-0">
                        <Badge variant={getRoleBadgeVariant(u.role) as any}>{u.role}</Badge>
                        {isActif ? <Badge variant="success">Actif</Badge> : <Badge variant="rose">Inactif</Badge>}
                      </div>
                    </div>

                    <div className="flex items-center gap-2 pt-2 border-t border-sand-200">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={isSelf}
                        className="flex-1 justify-center text-xs"
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
                        className={`flex-1 justify-center text-xs ${
                          isActif
                            ? 'text-rose-700 border border-rose-200'
                            : 'bg-emerald-600 text-white'
                        }`}
                        onClick={() => statutMutation.mutate({ id: u.id, actif: !isActif })}
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
            <div className="flex items-center justify-between pt-2 text-xs font-semibold text-charcoal-700">
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
                >
                  Précédent
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={data.last}
                  onClick={() => setPage((p) => p + 1)}
                  rightIcon={<ChevronRight className="w-4 h-4" />}
                >
                  Suivant
                </Button>
              </div>
            </div>
          )}
        </>
      )}

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
                <strong className="text-charcoal-900">
                  {createdResult.utilisateur.prenom} {createdResult.utilisateur.nom} ({createdResult.utilisateur.email})
                </strong>
              </div>
              <div>
                <span className="text-charcoal-500">Rôle attribué :</span>{' '}
                <Badge variant={getRoleBadgeVariant(createdResult.utilisateur.role) as any}>
                  {createdResult.utilisateur.role}
                </Badge>
              </div>
              <div className="pt-2">
                <span className="text-charcoal-600 font-semibold block mb-1">Mot de passe temporaire :</span>
                <div className="flex items-center justify-between gap-2 p-3 rounded-xl bg-white border border-argile-300">
                  <code className="font-mono font-extrabold text-sm text-sahel-900 select-all">
                    {createdResult.motDePasseTemporaire}
                  </code>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleCopyTempPassword}
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
