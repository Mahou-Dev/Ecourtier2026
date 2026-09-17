import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Home, UserCheck, Key, Building2, Mail, Lock, User, Phone, ArrowRight, ShieldCheck, Check } from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const [role, setRole] = useState<'LOCATAIRE' | 'PROPRIETAIRE' | 'COURTIER'>('LOCATAIRE');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('+221 ');
  const [password, setPassword] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    navigate('/dashboard');
  };

  return (
    <div className="min-h-[calc(100vh-80px)] flex flex-col md:flex-row bg-sand-100">
      
      {/* LEFT SPLIT: AMBIENT PHOTO & PROMISE */}
      <div className="hidden md:flex md:w-5/12 relative bg-sahel-900 text-white p-12 flex-col justify-between overflow-hidden">
        <img
          src="https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1200&q=80"
          alt="Inscrivez-vous E-Courtier"
          className="absolute inset-0 w-full h-full object-cover opacity-30"
        />
        <div className="relative z-10 space-y-4">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-xl bg-terracotta-500 flex items-center justify-center text-white shadow-terracotta-glow">
              <Home className="w-5 h-5" />
            </div>
            <span className="font-display font-extrabold text-2xl tracking-tight text-white">E-Courtier SN</span>
          </div>
        </div>

        <div className="relative z-10 space-y-4 max-w-md">
          <span className="px-3 py-1 rounded-full bg-ocher-500/20 text-ocher-300 text-xs font-semibold border border-ocher-400/30 inline-block">
            Inscription Rapide
          </span>
          <h2 className="font-display font-extrabold text-3xl leading-tight text-white">
            Rejoignez la 1ère communauté immobilière certifiée du Sénégal
          </h2>
          <p className="text-sm text-sahel-200 leading-relaxed">
            Trouvez votre logement sans intermédiaire frauduleux ou publiez vos biens en bénéficiant de notre garantie séquestre.
          </p>
        </div>

        <div className="relative z-10 flex items-center gap-2 text-xs text-sahel-300">
          <ShieldCheck className="w-4 h-4 text-ocher-400" />
          <span>Vérification d'identité sécurisée par SMS & WhatsApp</span>
        </div>
      </div>

      {/* RIGHT SPLIT: RICH ROLE SELECTION & FORM */}
      <div className="flex-1 flex items-center justify-center p-6 xs:p-8 sm:p-12 overflow-y-auto">
        <div className="w-full max-w-xl space-y-8 my-auto">
          
          <div className="space-y-2">
            <h1 className="font-display font-extrabold text-2xl xs:text-3xl text-charcoal-900">
              Créer votre compte
            </h1>
            <p className="text-xs text-charcoal-600">
              Sélectionnez votre profil pour personnaliser votre expérience E-Courtier.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-6">
            
            {/* RICH VISUAL ROLE SELECTION CARDS (NO BASIC SELECT) */}
            <div className="space-y-2">
              <label className="block text-xs font-semibold text-charcoal-800">Je suis un :</label>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                
                {/* Role 1: Locataire */}
                <div
                  onClick={() => setRole('LOCATAIRE')}
                  className={`p-4 rounded-2xl border-2 cursor-pointer transition-all ${
                    role === 'LOCATAIRE'
                      ? 'bg-terracotta-50 border-terracotta-500 shadow-terracotta-glow ring-2 ring-terracotta-500/20'
                      : 'bg-white border-argile-300 hover:border-argile-400'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="w-8 h-8 rounded-lg bg-terracotta-100 text-terracotta-600 flex items-center justify-center">
                      <UserCheck className="w-4 h-4" />
                    </div>
                    {role === 'LOCATAIRE' && <Check className="w-4 h-4 text-terracotta-600 font-bold" />}
                  </div>
                  <h4 className="font-display font-bold text-sm text-charcoal-900">Locataire / Acheteur</h4>
                  <p className="text-[11px] text-charcoal-500 mt-1">Je cherche un logement certifié à louer ou acheter.</p>
                </div>

                {/* Role 2: Propriétaire */}
                <div
                  onClick={() => setRole('PROPRIETAIRE')}
                  className={`p-4 rounded-2xl border-2 cursor-pointer transition-all ${
                    role === 'PROPRIETAIRE'
                      ? 'bg-sahel-50 border-sahel-800 shadow-sahel-glow ring-2 ring-sahel-800/20'
                      : 'bg-white border-argile-300 hover:border-argile-400'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="w-8 h-8 rounded-lg bg-sahel-100 text-sahel-800 flex items-center justify-center">
                      <Key className="w-4 h-4" />
                    </div>
                    {role === 'PROPRIETAIRE' && <Check className="w-4 h-4 text-sahel-800 font-bold" />}
                  </div>
                  <h4 className="font-display font-bold text-sm text-charcoal-900">Propriétaire</h4>
                  <p className="text-[11px] text-charcoal-500 mt-1">Je souhaite louer mon bien avec la garantie séquestre.</p>
                </div>

                {/* Role 3: Courtier Agréé */}
                <div
                  onClick={() => setRole('COURTIER')}
                  className={`p-4 rounded-2xl border-2 cursor-pointer transition-all ${
                    role === 'COURTIER'
                      ? 'bg-ocher-50 border-ocher-500 ring-2 ring-ocher-500/20'
                      : 'bg-white border-argile-300 hover:border-argile-400'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="w-8 h-8 rounded-lg bg-ocher-100 text-ocher-900 flex items-center justify-center">
                      <Building2 className="w-4 h-4" />
                    </div>
                    {role === 'COURTIER' && <Check className="w-4 h-4 text-ocher-900 font-bold" />}
                  </div>
                  <h4 className="font-display font-bold text-sm text-charcoal-900">Courtier / Agent</h4>
                  <p className="text-[11px] text-charcoal-500 mt-1">Je suis un professionnel certifié du secteur immobilier.</p>
                </div>

              </div>
            </div>

            {/* Inputs */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                label="Nom et Prénom"
                type="text"
                required
                placeholder="Ex: Babacar Ndiaye"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                leftIcon={<User className="w-4 h-4" />}
              />

              <Input
                label="Téléphone (WhatsApp)"
                type="tel"
                required
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                leftIcon={<Phone className="w-4 h-4" />}
              />
            </div>

            <Input
              label="Adresse Email"
              type="email"
              required
              placeholder="exemple@domaine.sn"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail className="w-4 h-4" />}
            />

            <Input
              label="Mot de passe"
              type="password"
              required
              placeholder="Au moins 8 caractères"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              leftIcon={<Lock className="w-4 h-4" />}
            />

            <Button type="submit" variant="primary" size="lg" className="w-full justify-center" rightIcon={<ArrowRight className="w-4 h-4" />}>
              S'inscrire et commencer
            </Button>
          </form>

          <div className="text-center text-xs text-charcoal-600">
            Vous avez déjà un compte ?{' '}
            <Link to="/connexion" className="font-bold text-terracotta-600 hover:underline">
              Se connecter
            </Link>
          </div>

        </div>
      </div>

    </div>
  );
};
