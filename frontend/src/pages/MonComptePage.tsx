import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Lock,
  User,
  Mail,
  Phone,
  ShieldCheck,
  CheckCircle2,
  KeyRound,
  Heart,
  MessageSquare,
  LayoutDashboard,
  Calendar,
  Star,
  AlertCircle,
  Info,
  ArrowUpRight,
  Users,
  ShieldAlert,
  FileCheck2,
  Compass,
} from 'lucide-react';
import { useAuthStore } from '../store/useAuthStore';
import { adminService } from '../services/admin.service';
import { apiFetch } from '../services/api';
import { messagerieService } from '../services/messagerie.service';
import { Card, CardBody } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Avatar } from '../components/ui/Avatar';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { toast } from 'sonner';
import { getErrorMessage } from '../lib/errors';
import { ReputationDTO } from '../types';
import {
  getRoleBadgeVariant,
  getRoleDescription,
  getRoleLabel,
} from '../lib/roleLabels';

interface QuickShortcut {
  to: string;
  title: string;
  description: string;
  icon: React.FC<{ className?: string }>;
  badgeText?: string;
}

export const MonComptePage: React.FC = () => {
  const navigate = useNavigate();
  const { utilisateur, logout } = useAuthStore();

  const [ancienMotDePasse, setAncienMotDePasse] = useState('');
  const [nouveauMotDePasse, setNouveauMotDePasse] = useState('');
  const [confirmationMotDePasse, setConfirmationMotDePasse] = useState('');
  const [loading, setLoading] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const isProOrOwner =
    utilisateur?.role === 'PROPRIETAIRE' || utilisateur?.role === 'AGENT';

  const { data: reputation } = useQuery<ReputationDTO>({
    queryKey: ['my-reputation', utilisateur?.id],
    queryFn: () =>
      apiFetch<ReputationDTO>(`/api/v1/utilisateurs/${utilisateur?.id}/reputation`),
    enabled: Boolean(utilisateur?.id && isProOrOwner),
    retry: false,
  });

  const { data: favorisCountData } = useQuery<{ count?: number; totalElements?: number }>({
    queryKey: ['favoris-count'],
    queryFn: () =>
      apiFetch<{ count?: number; totalElements?: number }>('/api/v1/utilisateurs/moi/favoris/count'),
    enabled: Boolean(utilisateur),
  });

  const { data: unreadMessages } = useQuery<number>({
    queryKey: ['messages-unread-count'],
    queryFn: () => messagerieService.compterMessagesNonLus(),
    enabled: Boolean(utilisateur),
  });

  if (!utilisateur) return null;

  const fullName = `${utilisateur.prenom} ${utilisateur.nom}`.trim();
  const roleLabel = getRoleLabel(utilisateur.role);
  const roleDescription = getRoleDescription(utilisateur.role);
  const roleVariant = getRoleBadgeVariant(utilisateur.role);

  const favorisCount =
    typeof favorisCountData?.count === 'number'
      ? favorisCountData.count
      : typeof favorisCountData?.totalElements === 'number'
        ? favorisCountData.totalElements
        : undefined;
  const unreadCount = typeof unreadMessages === 'number' ? unreadMessages : 0;

  const formattedJoinDate = utilisateur.dateCreation
    ? new Date(utilisateur.dateCreation).toLocaleDateString('fr-FR', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      })
    : null;

  const hasMinLength = nouveauMotDePasse.length >= 8;
  const hasUppercase = /[A-Z]/.test(nouveauMotDePasse);
  const hasDigit = /\d/.test(nouveauMotDePasse);
  const isDifferentFromOld =
    nouveauMotDePasse.length > 0 && nouveauMotDePasse !== ancienMotDePasse;
  const isConfirmationMatching =
    confirmationMotDePasse.length > 0 && nouveauMotDePasse === confirmationMotDePasse;
  const isPolicyValid = hasMinLength && hasUppercase && hasDigit;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError(null);

    if (!ancienMotDePasse || !nouveauMotDePasse) {
      const msg = 'Veuillez renseigner votre mot de passe actuel et votre nouveau mot de passe.';
      setPasswordError(msg);
      toast.error(msg);
      return;
    }
    if (!isPolicyValid) {
      const msg = 'Le nouveau mot de passe doit contenir au moins 8 caractères, une majuscule et un chiffre.';
      setPasswordError(msg);
      toast.error(msg);
      return;
    }
    if (nouveauMotDePasse === ancienMotDePasse) {
      const msg = "Le nouveau mot de passe doit être différent de l'ancien mot de passe.";
      setPasswordError(msg);
      toast.error(msg);
      return;
    }
    if (nouveauMotDePasse !== confirmationMotDePasse) {
      const msg = 'La confirmation ne correspond pas au nouveau mot de passe.';
      setPasswordError(msg);
      toast.error(msg);
      return;
    }

    setLoading(true);
    try {
      const res = await adminService.changerMonMotDePasse({
        ancienMotDePasse,
        nouveauMotDePasse,
      });
      toast.success(
        res.message || 'Mot de passe modifié. Vos sessions ont été révoquées, veuillez vous reconnecter.'
      );
      setAncienMotDePasse('');
      setNouveauMotDePasse('');
      setConfirmationMotDePasse('');
      await logout();
      navigate('/connexion');
    } catch (err: unknown) {
      const msg = getErrorMessage(err, 'Impossible de modifier le mot de passe');
      setPasswordError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  const shortcuts: QuickShortcut[] = [
    {
      to: '/favoris',
      title: 'Mes favoris',
      description: 'Retrouvez vos biens sauvegardés et leur disponibilité en direct.',
      icon: Heart,
      badgeText:
        typeof favorisCount === 'number' && favorisCount > 0
          ? `${favorisCount} bien${favorisCount > 1 ? 's' : ''}`
          : undefined,
    },
    {
      to: '/dashboard?tab=messages',
      title: 'Messagerie instantanée',
      description: 'Échangez avec vos interlocuteurs au sujet des annonces.',
      icon: MessageSquare,
      badgeText: unreadCount > 0 ? `${unreadCount} non lu${unreadCount > 1 ? 's' : ''}` : undefined,
    },
  ];

  if (utilisateur.role === 'LOCATAIRE') {
    shortcuts.push(
      {
        to: '/dashboard?tab=visites',
        title: 'Mes visites & dossiers',
        description: 'Suivez vos demandes de rendez-vous, cautions sous séquestre et baux.',
        icon: Calendar,
      },
      {
        to: '/recherche',
        title: 'Explorer le catalogue',
        description: 'Recherchez des logements vérifiés à louer ou acheter.',
        icon: Compass,
      }
    );
  }

  if (utilisateur.role === 'PROPRIETAIRE' || utilisateur.role === 'AGENT') {
    shortcuts.push(
      {
        to: '/dashboard',
        title: 'Espace Partenaire',
        description: 'Gérez vos annonces publiées, pièces justificatives et photos.',
        icon: LayoutDashboard,
      },
      {
        to: '/dashboard?tab=visites',
        title: 'Agenda visites & transactions',
        description: 'Confirmez vos visites et signez vos baux numériques.',
        icon: Calendar,
      }
    );
  }

  if (utilisateur.role === 'ADMIN') {
    shortcuts.push(
      {
        to: '/admin/dashboard',
        title: 'Console Administration',
        description: 'Tableau de pilotage, métriques globales et activité commerciale.',
        icon: ShieldCheck,
      },
      {
        to: '/admin/utilisateurs',
        title: 'Utilisateurs & Agents',
        description: 'Créez des comptes agents agréés, ajustez les rôles et statuts.',
        icon: Users,
      },
      {
        to: '/admin/verification',
        title: 'Vérifications cadastrales',
        description: 'Examinez les justificatifs immobiliers et certifiez les annonces.',
        icon: FileCheck2,
      },
      {
        to: '/admin/signalements',
        title: 'Modération signalements',
        description: 'Traitez les signalements utilisateurs et protégez la plateforme.',
        icon: ShieldAlert,
      }
    );
  }

  return (
    <div className="min-h-[calc(100vh-80px)] bg-sand-100 py-6 sm:py-8 pb-24 sm:pb-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-5xl mx-auto space-y-6">
        {/* EN-TÊTE DU PROFIL */}
        <section
          aria-label="En-tête du profil"
          className="relative rounded-3xl bg-[#1C352E] text-white p-5 sm:p-8 overflow-hidden shadow-sahel-deep border border-sahel-800"
        >
          <PatternClaustra variant="ocher" opacity={0.1} scale={1.1} />

          <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-5">
            <div className="flex flex-col sm:flex-row sm:items-center gap-4 min-w-0">
              <Avatar
                src={utilisateur.photoProfilUrl}
                name={fullName}
                size="xl"
                className="self-start sm:self-center"
              />

              <div className="space-y-2 min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-terracotta-500/20 border border-terracotta-400/40 text-terracotta-200 text-xs font-semibold">
                    <User className="w-3.5 h-3.5" aria-hidden="true" />
                    <span>Mon compte</span>
                  </span>

                  <Badge variant={roleVariant} className="text-xs px-3 py-1">
                    {roleLabel}
                  </Badge>

                  {typeof utilisateur.actif === 'boolean' && (
                    <Badge
                      variant={utilisateur.actif ? 'success' : 'rose'}
                      className="text-xs px-3 py-1"
                    >
                      {utilisateur.actif ? 'Compte actif' : 'Compte désactivé'}
                    </Badge>
                  )}
                </div>

                <h1 className="font-editorial font-extrabold text-2xl sm:text-3xl text-white tracking-tight break-words">
                  {fullName}
                </h1>

                <p className="text-xs sm:text-sm text-[#D6D0C5] max-w-2xl leading-relaxed">
                  {roleDescription}
                </p>

                {formattedJoinDate && (
                  <p className="text-[11px] text-[#D5AD72] font-medium">
                    Membre depuis le {formattedJoinDate}
                  </p>
                )}
              </div>
            </div>

            {/* RÉPUTATION PROPRIÉTAIRE / AGENT SI DISPONIBLE */}
            {isProOrOwner && (
              <div className="bg-white/10 backdrop-blur-sm border border-white/15 rounded-2xl p-4 sm:min-w-[205px] shrink-0 space-y-1">
                <span className="text-[11px] font-bold uppercase tracking-wider text-[#D5AD72] block">
                  Réputation sur la plateforme
                </span>
                {reputation && typeof reputation.noteMoyenne === 'number' && reputation.nombreAvis > 0 ? (
                  <div className="flex items-center gap-2 pt-0.5">
                    <Star className="w-5 h-5 fill-[#D5AD72] text-[#D5AD72] shrink-0" aria-hidden="true" />
                    <span className="font-editorial font-extrabold text-xl text-white">
                      {reputation.noteMoyenne.toFixed(1)} / 5
                    </span>
                    <span className="text-xs text-[#D6D0C5]">
                      ({reputation.nombreAvis} avis)
                    </span>
                  </div>
                ) : (
                  <p className="text-xs text-[#D6D0C5] pt-0.5">
                    Aucun avis reçu pour le moment
                  </p>
                )}
              </div>
            )}
          </div>
        </section>

        {/* RACCOURCIS PERSONNALISÉS SELON LE RÔLE */}
        <section aria-labelledby="shortcuts-heading" className="space-y-3">
          <div className="flex items-center justify-between gap-2">
            <h2
              id="shortcuts-heading"
              className="font-editorial font-bold text-base sm:text-lg text-charcoal-900"
            >
              Vos accès rapides
            </h2>
            <span className="text-xs text-charcoal-500 font-medium">
              Profil : {roleLabel}
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
            {shortcuts.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className="group bg-white rounded-2xl border border-argile-200 p-4 shadow-xs hover:border-terracotta-400 hover:shadow-warm-hover transition-all flex flex-col justify-between gap-3 min-h-[112px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-500"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="w-10 h-10 rounded-xl bg-sand-100 border border-argile-200 text-terracotta-600 flex items-center justify-center group-hover:bg-terracotta-50 transition-colors shrink-0">
                      <Icon className="w-5 h-5" aria-hidden="true" />
                    </div>
                    <div className="flex items-center gap-1.5">
                      {item.badgeText && (
                        <Badge variant="terracotta" size="sm">
                          {item.badgeText}
                        </Badge>
                      )}
                      <ArrowUpRight
                        className="w-4 h-4 text-charcoal-400 group-hover:text-terracotta-600 transition-colors"
                        aria-hidden="true"
                      />
                    </div>
                  </div>
                  <div className="space-y-0.5">
                    <h3 className="font-display font-bold text-sm text-charcoal-900 group-hover:text-terracotta-700 transition-colors">
                      {item.title}
                    </h3>
                    <p className="text-xs text-charcoal-600 leading-relaxed">
                      {item.description}
                    </p>
                  </div>
                </Link>
              );
            })}
          </div>
        </section>

        {/* GRILLE PRINCIPALE : IDENTITÉ & SÉCURITÉ */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* IDENTITÉ ET COORDONNÉES (CONSULTATION HONNÊTE) */}
          <Card className="lg:col-span-5 border border-argile-200 bg-white">
            <CardBody className="p-5 sm:p-6 space-y-5">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h2 className="font-editorial font-bold text-base text-charcoal-900 flex items-center gap-2">
                    <ShieldCheck className="w-4 h-4 text-sahel-700 shrink-0" aria-hidden="true" />
                    <span>Identité &amp; coordonnées</span>
                  </h2>
                  <p className="text-xs text-charcoal-500 mt-1">
                    Données vérifiées associées à votre compte.
                  </p>
                </div>
                <Badge variant="outline" size="sm" className="shrink-0">
                  Consultation
                </Badge>
              </div>

              <dl className="space-y-3 text-xs">
                <div className="p-3.5 rounded-xl bg-sand-50 border border-sand-200 min-w-0">
                  <dt className="text-charcoal-500 mb-1">Nom complet</dt>
                  <dd className="font-bold text-charcoal-900 break-words text-sm">{fullName}</dd>
                </div>

                <div className="p-3.5 rounded-xl bg-sand-50 border border-sand-200 min-w-0">
                  <dt className="text-charcoal-500 mb-1 flex items-center gap-1.5">
                    <Mail className="w-3.5 h-3.5 text-terracotta-500 shrink-0" aria-hidden="true" />
                    <span>Adresse Email</span>
                  </dt>
                  <dd className="font-bold text-charcoal-900 break-all">{utilisateur.email}</dd>
                </div>

                <div className="p-3.5 rounded-xl bg-sand-50 border border-sand-200 min-w-0">
                  <dt className="text-charcoal-500 mb-1 flex items-center gap-1.5">
                    <Phone className="w-3.5 h-3.5 text-terracotta-500 shrink-0" aria-hidden="true" />
                    <span>Téléphone</span>
                  </dt>
                  <dd className="font-bold text-charcoal-900">
                    {utilisateur.telephone || 'Non renseigné'}
                  </dd>
                </div>

                <div className="p-3.5 rounded-xl bg-sand-50 border border-sand-200 flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <dt className="text-charcoal-500 mb-0.5">Profil E-Courtier</dt>
                    <dd className="font-bold text-sahel-800">{roleLabel}</dd>
                  </div>
                  <Badge variant={roleVariant} size="sm">
                    {roleLabel}
                  </Badge>
                </div>

                {utilisateur.photoProfilUrl && (
                  <div className="p-3.5 rounded-xl bg-sand-50 border border-sand-200 min-w-0">
                    <dt className="text-charcoal-500 mb-1">Photo de profil</dt>
                    <dd className="text-charcoal-700 font-medium truncate">
                      Photo associée au compte
                    </dd>
                  </div>
                )}
              </dl>

              <div className="p-3.5 rounded-xl bg-sand-100/90 border border-argile-200 text-xs text-charcoal-700 flex items-start gap-2.5 leading-relaxed">
                <Info className="w-4 h-4 text-sahel-700 shrink-0 mt-0.5" aria-hidden="true" />
                <div>
                  <p>
                    La modification directe du nom, de l&rsquo;adresse email, du numéro de téléphone ou de la photo de profil n&rsquo;est pas encore disponible en libre-service.
                  </p>
                  <p className="mt-1.5">
                    Pour toute mise à jour administrative de vos coordonnées, contactez notre support à{' '}
                    <a
                      href="mailto:contact@e-courtier.sn"
                      className="font-semibold text-terracotta-700 underline decoration-terracotta-400 underline-offset-2 hover:text-terracotta-800"
                    >
                      contact@e-courtier.sn
                    </a>
                    .
                  </p>
                </div>
              </div>
            </CardBody>
          </Card>

          {/* SÉCURITÉ DU COMPTE & MOT DE PASSE */}
          <Card className="lg:col-span-7 border border-argile-200 bg-white">
            <CardBody className="p-5 sm:p-6 space-y-5">
              <div>
                <h2 className="font-editorial font-bold text-base text-charcoal-900 flex items-center gap-2">
                  <KeyRound className="w-4 h-4 text-terracotta-600 shrink-0" aria-hidden="true" />
                  <span>Changer mon mot de passe</span>
                </h2>
                <p className="text-xs text-charcoal-600 mt-1 leading-relaxed">
                  Si votre compte a été créé par un administrateur avec un mot de passe temporaire, ou si vous souhaitez renouveler vos accès, définissez ici un mot de passe personnel.
                </p>
              </div>

              <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-start gap-2.5 leading-relaxed">
                <ShieldCheck className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" aria-hidden="true" />
                <span>
                  <strong>Révocation de session :</strong> par mesure de sécurité, l&rsquo;enregistrement d&rsquo;un nouveau mot de passe révoque l&rsquo;ensemble de vos sessions actives et provoque une déconnexion automatique pour vous reconnecter avec vos nouveaux identifiants.
                </span>
              </div>

              {passwordError && (
                <div
                  role="alert"
                  className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs font-medium flex items-start gap-2.5"
                >
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" aria-hidden="true" />
                  <span>{passwordError}</span>
                </div>
              )}

              <form onSubmit={handleSubmit} noValidate className="space-y-4">
                <Input
                  label="Mot de passe actuel"
                  type="password"
                  required
                  autoComplete="current-password"
                  enablePasswordToggle
                  value={ancienMotDePasse}
                  onChange={(e) => {
                    setAncienMotDePasse(e.target.value);
                    if (passwordError) setPasswordError(null);
                  }}
                  placeholder="Saisissez votre mot de passe actuel"
                  leftIcon={<Lock className="w-4 h-4" />}
                />

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Input
                    label="Nouveau mot de passe"
                    type="password"
                    required
                    autoComplete="new-password"
                    enablePasswordToggle
                    value={nouveauMotDePasse}
                    onChange={(e) => {
                      setNouveauMotDePasse(e.target.value);
                      if (passwordError) setPasswordError(null);
                    }}
                    placeholder="Min. 8 car., 1 majuscule, 1 chiffre"
                    leftIcon={<Lock className="w-4 h-4" />}
                  />
                  <Input
                    label="Confirmer le nouveau mot de passe"
                    type="password"
                    required
                    autoComplete="new-password"
                    enablePasswordToggle
                    value={confirmationMotDePasse}
                    onChange={(e) => {
                      setConfirmationMotDePasse(e.target.value);
                      if (passwordError) setPasswordError(null);
                    }}
                    placeholder="Répétez le nouveau mot de passe"
                    leftIcon={<Lock className="w-4 h-4" />}
                  />
                </div>

                <div
                  aria-label="Exigences de sécurité"
                  className="flex flex-wrap gap-x-4 gap-y-2 text-[11px] bg-sand-50 p-3.5 rounded-xl border border-sand-200"
                >
                  <span
                    className={`flex items-center gap-1.5 font-semibold ${
                      hasMinLength ? 'text-emerald-700' : 'text-charcoal-500'
                    }`}
                  >
                    <CheckCircle2 className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                    8 caractères minimum
                  </span>
                  <span
                    className={`flex items-center gap-1.5 font-semibold ${
                      hasUppercase ? 'text-emerald-700' : 'text-charcoal-500'
                    }`}
                  >
                    <CheckCircle2 className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                    1 lettre majuscule
                  </span>
                  <span
                    className={`flex items-center gap-1.5 font-semibold ${
                      hasDigit ? 'text-emerald-700' : 'text-charcoal-500'
                    }`}
                  >
                    <CheckCircle2 className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                    1 chiffre
                  </span>
                  {ancienMotDePasse.length > 0 && (
                    <span
                      className={`flex items-center gap-1.5 font-semibold ${
                        isDifferentFromOld ? 'text-emerald-700' : 'text-charcoal-500'
                      }`}
                    >
                      <CheckCircle2 className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                      Différent de l&rsquo;actuel
                    </span>
                  )}
                  {confirmationMotDePasse.length > 0 && (
                    <span
                      className={`flex items-center gap-1.5 font-semibold ${
                        isConfirmationMatching ? 'text-emerald-700' : 'text-rose-600'
                      }`}
                    >
                      <CheckCircle2 className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                      Confirmation identique
                    </span>
                  )}
                </div>

                <div className="flex justify-end pt-2">
                  <Button
                    type="submit"
                    variant="primary"
                    size="md"
                    isLoading={loading}
                    className="w-full sm:w-auto justify-center min-h-[44px]"
                  >
                    Enregistrer le nouveau mot de passe
                  </Button>
                </div>
              </form>
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default MonComptePage;
