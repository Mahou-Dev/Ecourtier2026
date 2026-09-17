import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchHealthStatus } from '../services/api';
import { Button } from '../components/ui/Button';
import { Card, CardHeader, CardBody } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { 
  ShieldCheck, 
  Sparkles, 
  Lock, 
  FileText, 
  CheckCircle2, 
  Activity, 
  ArrowRight,
  Search,
  KeyRound
} from 'lucide-react';

export const ComingSoonPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<'CHERCHEUR' | 'PROPRIETAIRE' | 'COURTIER'>('CHERCHEUR');
  const [submitted, setSubmitted] = useState(false);

  // TanStack Query integrating with Backend API Health Check endpoint
  const { data: healthData, isError, isLoading } = useQuery({
    queryKey: ['healthCheck'],
    queryFn: fetchHealthStatus,
    refetchInterval: 15000,
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (email) {
      setSubmitted(true);
    }
  };

  return (
    <div className="space-y-16 py-8 xs:py-12 sm:py-16">
      
      {/* HERO SECTION */}
      <section className="max-w-4xl mx-auto text-center px-4 xs:px-6 space-y-6 sm:space-y-8">
        
        {/* Top Announcement Badge */}
        <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-terracotta-50 border border-terracotta-200 text-terracotta-800 text-xs sm:text-sm font-semibold shadow-sm">
          <Sparkles className="w-4 h-4 text-terracotta-500 animate-pulse" />
          <span>Lancement Imminent au Sénégal &bull; Dakar & Régions</span>
        </div>

        {/* Hero Title */}
        <h1 className="text-3xl xs:text-4xl sm:text-5xl lg:text-6xl font-extrabold text-charcoal-800 tracking-tight leading-[1.15]">
          Trouver un logement au Sénégal n'a jamais été aussi <br className="hidden sm:inline" />
          <span className="text-transparent bg-clip-text gradient-terracotta">
            simple, clair et sécurisé.
          </span>
        </h1>

        {/* Hero Subtitle */}
        <p className="text-base sm:text-xl text-charcoal-600 max-w-2xl mx-auto leading-relaxed">
          Fini les arnaques sur WhatsApp et Facebook. <strong className="text-sahel-800 font-semibold">E-Courtier</strong> digitalise le marché immobilier avec un assistant IA, des annonces certifiées et une caution séquestre garantie.
        </p>

        {/* Early Access Form */}
        <div className="max-w-md mx-auto pt-4">
          {!submitted ? (
            <form onSubmit={handleSubmit} className="space-y-3">
              
              {/* Role selector pill */}
              <div className="flex rounded-xl bg-sand-200 p-1 border border-argile-200 text-xs font-semibold text-charcoal-700">
                <button
                  type="button"
                  onClick={() => setRole('CHERCHEUR')}
                  className={`flex-1 py-1.5 rounded-lg transition-all ${role === 'CHERCHEUR' ? 'bg-white shadow-sm text-terracotta-600' : 'hover:text-charcoal-900'}`}
                >
                  Locataire / Acheteur
                </button>
                <button
                  type="button"
                  onClick={() => setRole('PROPRIETAIRE')}
                  className={`flex-1 py-1.5 rounded-lg transition-all ${role === 'PROPRIETAIRE' ? 'bg-white shadow-sm text-terracotta-600' : 'hover:text-charcoal-900'}`}
                >
                  Propriétaire
                </button>
                <button
                  type="button"
                  onClick={() => setRole('COURTIER')}
                  className={`flex-1 py-1.5 rounded-lg transition-all ${role === 'COURTIER' ? 'bg-white shadow-sm text-terracotta-600' : 'hover:text-charcoal-900'}`}
                >
                  Courtier Certifié
                </button>
              </div>

              {/* Input & Submit */}
              <div className="flex flex-col xs:flex-row gap-2">
                <input
                  type="email"
                  required
                  placeholder="Entrez votre adresse email..."
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="flex-1 px-4 py-3 rounded-xl border border-argile-300 bg-white text-charcoal-800 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta-500 focus:border-transparent placeholder:text-charcoal-400 shadow-sm"
                />
                <Button type="submit" variant="primary" size="md" rightIcon={<ArrowRight className="w-4 h-4" />}>
                  Rejoindre
                </Button>
              </div>
              <p className="text-[11px] text-charcoal-500">
                🔒 Soyez averti en priorité le jour du lancement officiel.
              </p>
            </form>
          ) : (
            <div className="p-4 rounded-2xl bg-sahel-50 border border-sahel-200 text-sahel-900 space-y-2">
              <div className="flex items-center justify-center gap-2 font-display font-bold text-base text-sahel-800">
                <CheckCircle2 className="w-5 h-5 text-sahel-600" />
                <span>Demande d'accès anticipé enregistrée !</span>
              </div>
              <p className="text-xs text-sahel-700">
                Merci ! Vous recevrez nos invitations exclusives pour tester la version beta à Dakar.
              </p>
            </div>
          )}
        </div>

      </section>

      {/* BACKEND API STATUS TESTER BADGE */}
      <section className="max-w-2xl mx-auto px-4">
        <div className="p-4 rounded-2xl bg-white border border-argile-200 shadow-warm-card flex flex-col sm:flex-row items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-2.5">
            <div className={`w-3 h-3 rounded-full animate-ping shrink-0 ${isError ? 'bg-red-500' : 'bg-emerald-500'}`} />
            <div className="flex items-center gap-1.5 font-semibold text-charcoal-700">
              <Activity className="w-4 h-4 text-sahel-800" />
              <span>Backend API Health Indicator:</span>
            </div>
          </div>
          
          <div>
            {isLoading ? (
              <span className="text-charcoal-400">Interrogation du backend...</span>
            ) : isError ? (
              <Badge variant="terracotta">Backend déconnecté / en attente Docker</Badge>
            ) : (
              <div className="flex items-center gap-2">
                <Badge variant="sahel">
                  API {healthData?.status} ({healthData?.version})
                </Badge>
                <span className="text-charcoal-400 hidden xs:inline">
                  [{healthData?.environment}]
                </span>
              </div>
            )}
          </div>
        </div>
      </section>

      {/* CORE FEATURE CARDS SHOWCASE */}
      <section id="vision" className="max-w-6xl mx-auto px-4 xs:px-6 space-y-8">
        
        <div className="text-center space-y-3">
          <Badge variant="ocher">Vision & Innovation</Badge>
          <h2 className="text-2xl xs:text-3xl font-extrabold text-charcoal-800">
            Une révolution en 4 piliers pour le marché sénégalais
          </h2>
          <p className="text-sm text-charcoal-600 max-w-xl mx-auto">
            Nous combinons intelligence artificielle et garanties financières pour sécuriser la location et la vente.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          
          {/* Card 1 */}
          <Card className="hover:border-terracotta-300">
            <CardHeader className="flex items-center justify-between">
              <div className="w-12 h-12 rounded-xl bg-terracotta-100 flex items-center justify-center text-terracotta-600">
                <Search className="w-6 h-6" />
              </div>
              <Badge variant="terracotta">Assistant IA</Badge>
            </CardHeader>
            <CardBody className="space-y-3">
              <h3 className="font-display font-bold text-lg text-charcoal-800">
                Recherche Intelligente & Agrégation
              </h3>
              <p className="text-sm text-charcoal-600 leading-relaxed">
                Notre assistant IA analyse vos critères (quartier, budget, nombre de chambres, commodités) et filtre les offres dispersées pour vous proposer uniquement des biens pertinents et vérifiés.
              </p>
            </CardBody>
          </Card>

          {/* Card 2 */}
          <Card className="hover:border-sahel-300">
            <CardHeader className="flex items-center justify-between">
              <div className="w-12 h-12 rounded-xl bg-sahel-100 flex items-center justify-center text-sahel-800">
                <ShieldCheck className="w-6 h-6" />
              </div>
              <Badge variant="sahel">Anti-Arnaque</Badge>
            </CardHeader>
            <CardBody className="space-y-3">
              <h3 className="font-display font-bold text-lg text-charcoal-800">
                Vérification Cadastrale & Titres
              </h3>
              <p className="text-sm text-charcoal-600 leading-relaxed">
                Chaque annonce est contrôlée avant d'être publiée : validation de l'identité du propriétaire ou du courtier mandaté, et vérification du titre de propriété pour stopper les fausses annonces.
              </p>
            </CardBody>
          </Card>

          {/* Card 3 */}
          <Card className="hover:border-ocher-300">
            <CardHeader className="flex items-center justify-between">
              <div className="w-12 h-12 rounded-xl bg-ocher-100 flex items-center justify-center text-ocher-900">
                <Lock className="w-6 h-6" />
              </div>
              <Badge variant="ocher">Séquestre Sécurisé</Badge>
            </CardHeader>
            <CardBody className="space-y-3">
              <h3 className="font-display font-bold text-lg text-charcoal-800">
                Gestion des Cautions sous Séquestre
              </h3>
              <p className="text-sm text-charcoal-600 leading-relaxed">
                Votre mois d'avance et votre caution restent bloqués en sécurité sur un compte séquestre dédié et ne sont transférés au propriétaire qu'après remise des clés et signature de l'état des lieux.
              </p>
            </CardBody>
          </Card>

          {/* Card 4 */}
          <Card className="hover:border-sahel-300">
            <CardHeader className="flex items-center justify-between">
              <div className="w-12 h-12 rounded-xl bg-sand-300 flex items-center justify-center text-charcoal-900">
                <FileText className="w-6 h-6" />
              </div>
              <Badge variant="sand">Bail Légal SN</Badge>
            </CardHeader>
            <CardBody className="space-y-3">
              <h3 className="font-display font-bold text-lg text-charcoal-800">
                Signature de Bail Électronique
              </h3>
              <p className="text-sm text-charcoal-600 leading-relaxed">
                Génération automatique du contrat de bail à usage d'habitation conforme au Code des Obligations Civiles et Commerciales (COCC) du Sénégal, avec signature électronique à valeur légale.
              </p>
            </CardBody>
          </Card>

        </div>

      </section>

      {/* DESIGN SYSTEM PALETTE DEMO BANNER */}
      <section className="max-w-4xl mx-auto px-4 xs:px-6">
        <div className="p-6 sm:p-8 rounded-3xl bg-sahel-800 text-white shadow-sahel-glow space-y-6">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <KeyRound className="w-5 h-5 text-ocher-400" />
                <h3 className="font-display text-xl font-bold text-white">
                  Identité Visuelle E-Courtier SN
                </h3>
              </div>
              <p className="text-xs text-sahel-200 mt-1">
                Direction artistique chaleureuse inspirée de l'architecture et de la terre sénégalaise.
              </p>
            </div>
            <Button variant="accent" size="sm">
              Charte Certifiée
            </Button>
          </div>

          {/* Swatches */}
          <div className="grid grid-cols-2 xs:grid-cols-4 gap-3 pt-2 text-xs font-semibold">
            <div className="p-3 rounded-xl bg-terracotta-500 text-white flex flex-col justify-between h-20 shadow-sm">
              <span>Terre Cuite</span>
              <span className="text-[10px] font-mono opacity-80">#C85A32</span>
            </div>
            <div className="p-3 rounded-xl bg-sahel-800 text-white border border-sahel-700 flex flex-col justify-between h-20 shadow-sm">
              <span>Vert Sahel</span>
              <span className="text-[10px] font-mono opacity-80">#1D3E35</span>
            </div>
            <div className="p-3 rounded-xl bg-sand-100 text-charcoal-800 flex flex-col justify-between h-20 shadow-sm">
              <span>Sable Chaud</span>
              <span className="text-[10px] font-mono opacity-80">#FDFBF7</span>
            </div>
            <div className="p-3 rounded-xl bg-ocher-500 text-charcoal-900 flex flex-col justify-between h-20 shadow-sm">
              <span>Ocre Solaire</span>
              <span className="text-[10px] font-mono opacity-80">#E09F3E</span>
            </div>
          </div>
        </div>
      </section>

    </div>
  );
};
