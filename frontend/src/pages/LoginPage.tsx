import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import {
  Home,
  Lock,
  Mail,
  ArrowRight,
  ShieldCheck,
  AlertCircle,
  UserCheck,
  X,
} from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { apiFetch } from '../services/api';
import { AuthResponseDTO } from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { toast } from 'sonner';
import { getErrorMessage } from '../lib/errors';
import {
  getRoleHomeRoute,
  getRoleLabel,
  resolvePostAuthDestination,
} from '../lib/roleLabels';
import heroAlmadiesImg from '../assets/home/hero-almadies.webp';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { utilisateur, setAuth } = useAuthStore();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [showResetInfo, setShowResetInfo] = useState(false);

  const fromLocation = (location.state as { from?: string } | null)?.from;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const trimmedEmail = email.trim();
    if (!trimmedEmail || !password) {
      const msg = 'Veuillez renseigner votre adresse email et votre mot de passe.';
      setFormError(msg);
      toast.error(msg);
      return;
    }

    setLoading(true);
    try {
      const data = await apiFetch<AuthResponseDTO>('/api/v1/auth/connexion', {
        method: 'POST',
        body: JSON.stringify({
          email: trimmedEmail,
          motDePasse: password,
        }),
      });

      setAuth(data);
      toast.success(`Bienvenue, ${data.utilisateur.prenom} !`);
      const destination = resolvePostAuthDestination(data.utilisateur.role, fromLocation);
      navigate(destination, { replace: true });
    } catch (err: unknown) {
      const msg = getErrorMessage(err, 'Identifiants incorrects');
      setFormError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-80px)] flex flex-col md:flex-row bg-sand-100">
      {/* LEFT SPLIT: LOCAL ARCHITECTURAL PHOTOGRAPHY & TRUST PLEDGE */}
      <div className="hidden md:flex md:w-5/12 lg:w-1/2 relative bg-[#162823] text-white p-10 lg:p-12 flex-col justify-between overflow-hidden">
        <img
          src={heroAlmadiesImg}
          alt="Architecture résidentielle aux Almadies, Dakar"
          className="absolute inset-0 w-full h-full object-cover opacity-35"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#162823]/95 via-[#1C352E]/75 to-[#162823]/80" />
        <PatternClaustra variant="sand" opacity={0.07} scale={1.2} />

        <div className="relative z-10 flex items-center gap-2.5">
          <div className="w-10 h-10 rounded-xl bg-[#B85C42] flex items-center justify-center text-white shadow-terracotta-glow">
            <Home className="w-5 h-5" aria-hidden="true" />
          </div>
          <div className="flex items-center gap-1.5">
            <span className="font-editorial font-extrabold text-2xl tracking-tight text-white">
              E-Courtier
            </span>
            <span className="bg-[#D5AD72] text-[#1C352E] text-[10px] font-extrabold px-1.5 py-0.5 rounded uppercase tracking-wider">
              SN
            </span>
          </div>
        </div>

        <div className="relative z-10 space-y-4 max-w-md">
          <span className="px-3 py-1 rounded-full bg-[#D5AD72]/15 text-[#F0D39A] text-xs font-semibold border border-[#D5AD72]/30 inline-block">
            Espace Sécurisé
          </span>
          <h2 className="font-editorial font-extrabold text-2xl lg:text-3xl leading-tight text-white">
            Connectez-vous à votre espace immobilier sécurisé
          </h2>
          <p className="text-sm text-[#D6D0C5] leading-relaxed">
            Accédez à vos favoris, vos visites programmées, vos dossiers sous séquestre et vos échanges avec les propriétaires ou agents vérifiés.
          </p>
        </div>

        <div className="relative z-10 flex items-center gap-2 text-xs text-[#D6D0C5]">
          <ShieldCheck className="w-4 h-4 text-[#D5AD72] shrink-0" aria-hidden="true" />
          <span>Contrôle cadastral, visites confirmées &amp; bail numérique horodaté</span>
        </div>
      </div>

      {/* RIGHT SPLIT: LOGIN FORM */}
      <div className="flex-1 flex items-center justify-center px-4 py-8 xs:px-8 sm:p-10 lg:px-14 lg:pr-16 pb-24 lg:pb-16">
        <div className="w-full max-w-md space-y-6">
          <div className="space-y-2">
            <h1 className="font-editorial font-extrabold text-2xl xs:text-3xl text-charcoal-900 tracking-tight">
              Connexion
            </h1>
            <p className="text-xs sm:text-sm text-charcoal-600">
              Entrez vos identifiants pour accéder à votre compte E-Courtier.
            </p>
          </div>

          {utilisateur && (
            <div className="p-3.5 rounded-2xl bg-sahel-50 border border-sahel-200 flex flex-col xs:flex-row xs:items-center justify-between gap-3 text-xs">
              <div className="flex items-start gap-2.5 min-w-0">
                <UserCheck className="w-4 h-4 text-sahel-800 shrink-0 mt-0.5" aria-hidden="true" />
                <div className="min-w-0">
                  <p className="font-bold text-sahel-900 truncate">
                    Session active : {utilisateur.prenom} {utilisateur.nom}
                  </p>
                  <p className="text-sahel-700 truncate">
                    {getRoleLabel(utilisateur.role)}
                  </p>
                </div>
              </div>
              <Link
                to={getRoleHomeRoute(utilisateur.role)}
                className="inline-flex items-center justify-center px-3.5 py-2 min-h-[44px] rounded-xl bg-sahel-800 text-white font-semibold hover:bg-sahel-900 transition-colors shrink-0"
              >
                Mon espace
              </Link>
            </div>
          )}

          {formError && (
            <div
              role="alert"
              className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs font-medium flex items-start gap-2.5"
            >
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" aria-hidden="true" />
              <span>{formError}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-5">
            <Input
              label="Adresse Email"
              type="email"
              required
              autoComplete="email"
              placeholder="votre.email@domaine.sn"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (formError) setFormError(null);
              }}
              leftIcon={<Mail className="w-4 h-4" />}
            />

            <div className="space-y-1.5">
              <Input
                label="Mot de passe"
                type="password"
                required
                autoComplete="current-password"
                enablePasswordToggle
                placeholder="••••••••"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  if (formError) setFormError(null);
                }}
                leftIcon={<Lock className="w-4 h-4" />}
              />

              <div className="flex justify-end">
                <button
                  type="button"
                  onClick={() => setShowResetInfo((prev) => !prev)}
                  aria-expanded={showResetInfo}
                  className="text-xs text-terracotta-600 hover:underline font-semibold py-2 px-1 min-h-[44px] inline-flex items-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-500 rounded-lg"
                >
                  Mot de passe oublié ?
                </button>
              </div>

              {showResetInfo && (
                <div
                  role="note"
                  className="p-3.5 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 space-y-1.5"
                >
                  <div className="font-semibold flex items-center justify-between gap-2">
                    <span>Assistance &amp; réinitialisation d&rsquo;accès</span>
                    <button
                      type="button"
                      onClick={() => setShowResetInfo(false)}
                      className="w-7 h-7 rounded-lg text-amber-700 hover:bg-amber-100 inline-flex items-center justify-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-600"
                      aria-label="Fermer l'information"
                    >
                      <X className="w-4 h-4" aria-hidden="true" />
                    </button>
                  </div>
                  <p className="leading-relaxed">
                    La réinitialisation automatisée par courriel n&rsquo;est pas activée sur cette instance. Pour récupérer votre accès, contactez l&rsquo;équipe E-Courtier à{' '}
                    <a
                      href="mailto:contact@e-courtier.sn"
                      className="font-mono font-semibold underline decoration-terracotta-500 underline-offset-2 text-sahel-900"
                    >
                      contact@e-courtier.sn
                    </a>
                    .
                  </p>
                </div>
              )}
            </div>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full justify-center min-h-[48px]"
              isLoading={loading}
              rightIcon={<ArrowRight className="w-4 h-4" />}
            >
              {loading ? 'Connexion en cours...' : 'Se connecter'}
            </Button>
          </form>

          <div className="text-center text-xs text-charcoal-600 pt-1">
            Vous n&rsquo;avez pas encore de compte ?{' '}
            <Link
              to="/inscription"
              className="font-bold text-terracotta-600 hover:underline inline-flex items-center min-h-[44px] px-1 rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-500"
            >
              Créer un compte gratuitement
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
