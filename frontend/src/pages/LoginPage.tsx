import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Home, Lock, Mail, ArrowRight, ShieldCheck } from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    navigate('/dashboard');
  };

  return (
    <div className="min-h-[calc(100vh-80px)] flex flex-col md:flex-row bg-sand-100">
      
      {/* LEFT SPLIT: AMBIENT SENEGAL ARCHITECTURE PHOTO & BRAND PLEDGE */}
      <div className="hidden md:flex md:w-1/2 relative bg-sahel-900 text-white p-12 flex-col justify-between overflow-hidden">
        <img
          src="https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80"
          alt="Immobilier Sénégal"
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
            Espace Sécurisé
          </span>
          <h2 className="font-display font-extrabold text-3xl leading-tight text-white">
            Connectez-vous à votre espace immobilier certifié
          </h2>
          <p className="text-sm text-sahel-200 leading-relaxed">
            Accédez à vos annonces, suivez l'avancement de vos dossiers sous séquestre et échangez directement avec les propriétaires vérifiés.
          </p>
        </div>

        <div className="relative z-10 flex items-center gap-2 text-xs text-sahel-300">
          <ShieldCheck className="w-4 h-4 text-ocher-400" />
          <span>Plateforme 100% conforme au Code des Obligations Civiles du Sénégal</span>
        </div>
      </div>

      {/* RIGHT SPLIT: CLEAN ELEGANT FORM */}
      <div className="flex-1 flex items-center justify-center p-6 xs:p-8 sm:p-12">
        <div className="w-full max-w-md space-y-8">
          
          <div className="space-y-2">
            <h1 className="font-display font-extrabold text-2xl xs:text-3xl text-charcoal-900">
              Connexion
            </h1>
            <p className="text-xs text-charcoal-600">
              Entrez vos identifiants pour accéder à votre compte E-Courtier.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <Input
              label="Adresse Email"
              type="email"
              required
              placeholder="votre.email@domaine.sn"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail className="w-4 h-4" />}
            />

            <div className="space-y-1">
              <Input
                label="Mot de passe"
                type="password"
                required
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                leftIcon={<Lock className="w-4 h-4" />}
              />
              <div className="text-right">
                <a href="#reset" className="text-xs text-terracotta-600 hover:underline font-medium">
                  Mot de passe oublié ?
                </a>
              </div>
            </div>

            <Button type="submit" variant="primary" size="lg" className="w-full justify-center" rightIcon={<ArrowRight className="w-4 h-4" />}>
              Se connecter
            </Button>
          </form>

          <div className="text-center text-xs text-charcoal-600">
            Vous n'avez pas encore de compte ?{' '}
            <Link to="/inscription" className="font-bold text-terracotta-600 hover:underline">
              Créer un compte gratuitement
            </Link>
          </div>

        </div>
      </div>

    </div>
  );
};
