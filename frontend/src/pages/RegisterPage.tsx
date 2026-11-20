import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Home,
  UserCheck,
  Key,
  Building2,
  Mail,
  Lock,
  User,
  Phone,
  ArrowRight,
  ShieldCheck,
  Check,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { PatternClaustra } from '../components/ui/PatternClaustra';
import { apiFetch } from '../services/api';
import { AuthResponseDTO } from '../types';
import { useAuthStore } from '../store/useAuthStore';
import { toast } from 'sonner';
import { getErrorMessage } from '../lib/errors';
import { getRoleHomeRoute } from '../lib/roleLabels';
import trustInteriorImg from '../assets/home/trust-interior.webp';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();

  const [role, setRole] = useState<'LOCATAIRE' | 'PROPRIETAIRE'>('LOCATAIRE');
  const [prenom, setPrenom] = useState('');
  const [nom, setNom] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const hasMinLength = password.length >= 8;
  const hasUppercase = /[A-Z]/.test(password);
  const hasDigit = /\d/.test(password);
  const isPasswordPolicyValid = hasMinLength && hasUppercase && hasDigit;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const trimmedPrenom = prenom.trim();
    const trimmedNom = nom.trim();
    const trimmedEmail = email.trim();
    const trimmedPhone = phone.trim();

    if (!trimmedPrenom || !trimmedNom || !trimmedEmail || !trimmedPhone || !password) {
      const msg = 'Veuillez remplir tous les champs obligatoires.';
      setFormError(msg);
      toast.error(msg);
      return;
    }

    if (!isPasswordPolicyValid) {
      const msg = 'Le mot de passe doit contenir au moins 8 caractères, une lettre majuscule et un chiffre.';
      setFormError(msg);
      toast.error(msg);
      return;
    }

    setLoading(true);
    try {
      const data = await apiFetch<AuthResponseDTO>('/api/v1/auth/inscription', {
        method: 'POST',
        body: JSON.stringify({
          nom: trimmedNom,
          prenom: trimmedPrenom,
          email: trimmedEmail,
          motDePasse: password,
          telephone: trimmedPhone,
          role,
        }),
      });

      setAuth(data);
      toast.success(`Compte créé avec succès ! Bienvenue ${data.utilisateur.prenom}`);
      navigate(getRoleHomeRoute(data.utilisateur.role), { replace: true });
    } catch (err: unknown) {
      const msg = getErrorMessage(err, "Erreur lors de l'inscription");
      setFormError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-80px)] flex flex-col md:flex-row bg-sand-100">
      {/* LEFT SPLIT: LOCAL VISUAL & PROMISE */}
      <div className="hidden md:flex md:w-5/12 relative bg-[#162823] text-white p-10 lg:p-12 flex-col justify-between overflow-hidden">
        <img
          src={trustInteriorImg}
          alt="Intérieur contemporain lumineux E-Courtier Sénégal"
          className="absolute inset-0 w-full h-full object-cover opacity-30"
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
            Inscription Rapide
          </span>
          <h2 className="font-editorial font-extrabold text-2xl lg:text-3xl leading-tight text-white">
            Rejoignez la 1ère communauté immobilière sécurisée du Sénégal
          </h2>
          <p className="text-sm text-[#D6D0C5] leading-relaxed">
            Trouvez un logement vérifié sans intermédiaire opaque ou publiez vos biens en bénéficiant de notre garantie séquestre et du bail numérique.
          </p>
        </div>

        <div className="relative z-10 flex items-center gap-2 text-xs text-[#D6D0C5]">
          <ShieldCheck className="w-4 h-4 text-[#D5AD72] shrink-0" aria-hidden="true" />
          <span>Annonces contrôlées, visites confirmées &amp; protection des transactions</span>
        </div>
      </div>

      {/* RIGHT SPLIT: ACCESSIBLE ROLE CARDS & FORM */}
      <div className="flex-1 flex items-center justify-center px-4 py-6 xs:px-8 sm:p-10 lg:px-12 lg:pr-16 pb-24 lg:pb-16 overflow-y-auto">
        <div className="w-full max-w-lg space-y-5 my-auto">
          <div className="space-y-1.5">
            <h1 className="font-editorial font-extrabold text-2xl xs:text-3xl text-charcoal-900 tracking-tight">
              Créer votre compte
            </h1>
            <p className="text-xs sm:text-sm text-charcoal-600">
              Sélectionnez votre profil pour personnaliser votre expérience E-Courtier.
            </p>
          </div>

          {formError && (
            <div
              role="alert"
              className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs font-medium flex items-start gap-2.5"
            >
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" aria-hidden="true" />
              <span>{formError}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            {/* ACCESSIBLE ROLE SELECTION CARDS */}
            <div className="space-y-2">
              <span id="register-role-label" className="block text-xs font-semibold text-charcoal-800">
                Je suis un :
              </span>
              <div
                role="group"
                aria-labelledby="register-role-label"
                className="grid grid-cols-1 xs:grid-cols-2 gap-2.5 sm:gap-3"
              >
                <button
                  type="button"
                  onClick={() => setRole('LOCATAIRE')}
                  aria-pressed={role === 'LOCATAIRE'}
                  className={`text-left p-3 sm:p-4 rounded-2xl border-2 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-500 ${
                    role === 'LOCATAIRE'
                      ? 'bg-terracotta-50 border-terracotta-500 shadow-terracotta-glow ring-2 ring-terracotta-500/20'
                      : 'bg-white border-argile-300 hover:border-argile-400'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1.5 sm:mb-2">
                    <div className="w-8 h-8 rounded-lg bg-terracotta-100 text-terracotta-600 flex items-center justify-center">
                      <UserCheck className="w-4 h-4" aria-hidden="true" />
                    </div>
                    {role === 'LOCATAIRE' && (
                      <Check className="w-4 h-4 text-terracotta-600 font-bold" aria-hidden="true" />
                    )}
                  </div>
                  <span className="block font-display font-bold text-sm text-charcoal-900">
                    Locataire / Acheteur
                  </span>
                  <span className="block text-[11px] text-charcoal-600 mt-0.5 sm:mt-1 leading-snug">
                    Je cherche un logement vérifié à louer ou acheter.
                  </span>
                </button>

                <button
                  type="button"
                  onClick={() => setRole('PROPRIETAIRE')}
                  aria-pressed={role === 'PROPRIETAIRE'}
                  className={`text-left p-3 sm:p-4 rounded-2xl border-2 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sahel-700 ${
                    role === 'PROPRIETAIRE'
                      ? 'bg-sahel-50 border-sahel-800 shadow-sahel-glow ring-2 ring-sahel-800/20'
                      : 'bg-white border-argile-300 hover:border-argile-400'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1.5 sm:mb-2">
                    <div className="w-8 h-8 rounded-lg bg-sahel-100 text-sahel-800 flex items-center justify-center">
                      <Key className="w-4 h-4" aria-hidden="true" />
                    </div>
                    {role === 'PROPRIETAIRE' && (
                      <Check className="w-4 h-4 text-sahel-800 font-bold" aria-hidden="true" />
                    )}
                  </div>
                  <span className="block font-display font-bold text-sm text-charcoal-900">
                    Propriétaire
                  </span>
                  <span className="block text-[11px] text-charcoal-600 mt-0.5 sm:mt-1 leading-snug">
                    Je souhaite louer ou vendre mon bien avec la garantie séquestre.
                  </span>
                </button>
              </div>

              <div className="flex items-center gap-2 px-3 py-2 rounded-xl bg-ocher-50/80 border border-ocher-300/60 text-xs text-charcoal-700">
                <Building2 className="w-4 h-4 text-ocher-700 shrink-0" aria-hidden="true" />
                <span>Les comptes agents sont créés par l&rsquo;administrateur</span>
              </div>
            </div>

            {/* PERSONAL INPUTS */}
            <div className="grid grid-cols-1 xs:grid-cols-2 gap-3 sm:gap-4">
              <Input
                label="Prénom"
                type="text"
                required
                autoComplete="given-name"
                placeholder="Ex: Babacar"
                value={prenom}
                onChange={(e) => {
                  setPrenom(e.target.value);
                  if (formError) setFormError(null);
                }}
                leftIcon={<User className="w-4 h-4" />}
              />

              <Input
                label="Nom"
                type="text"
                required
                autoComplete="family-name"
                placeholder="Ex: Ndiaye"
                value={nom}
                onChange={(e) => {
                  setNom(e.target.value);
                  if (formError) setFormError(null);
                }}
                leftIcon={<User className="w-4 h-4" />}
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                label="Adresse Email"
                type="email"
                required
                autoComplete="email"
                placeholder="exemple@domaine.sn"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  if (formError) setFormError(null);
                }}
                leftIcon={<Mail className="w-4 h-4" />}
              />

              <Input
                label="Téléphone (WhatsApp)"
                type="tel"
                required
                autoComplete="tel"
                placeholder="+221 77 123 45 67"
                value={phone}
                onChange={(e) => {
                  setPhone(e.target.value);
                  if (formError) setFormError(null);
                }}
                leftIcon={<Phone className="w-4 h-4" />}
              />
            </div>

            <div className="space-y-2.5">
              <Input
                label="Mot de passe"
                type="password"
                required
                autoComplete="new-password"
                enablePasswordToggle
                placeholder="Min. 8 caractères, 1 majuscule, 1 chiffre"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  if (formError) setFormError(null);
                }}
                leftIcon={<Lock className="w-4 h-4" />}
              />

              <div
                aria-label="Exigences de sécurité"
                className="flex flex-wrap gap-x-4 gap-y-2 text-[11px] bg-white/80 p-3 rounded-xl border border-argile-200"
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
              </div>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full justify-center min-h-[48px]"
              isLoading={loading}
              rightIcon={<ArrowRight className="w-4 h-4" />}
            >
              {loading ? 'Création du compte...' : "S'inscrire et commencer"}
            </Button>
          </form>

          <div className="text-center text-xs text-charcoal-600 pt-1">
            Vous avez déjà un compte ?{' '}
            <Link
              to="/connexion"
              className="font-bold text-terracotta-600 hover:underline inline-flex items-center min-h-[44px] px-1 rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-terracotta-500"
            >
              Se connecter
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
