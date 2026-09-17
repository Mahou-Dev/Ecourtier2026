import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Search, MapPin, Sparkles, ShieldCheck, Lock, FileText, ArrowRight, ChevronRight } from 'lucide-react';
import { Button } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { MOCK_PROPERTIES, MOCK_NEIGHBORHOODS } from '../mocks/properties';

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const [transactionType, setTransactionType] = useState<'LOCATION' | 'VENTE'>('LOCATION');
  const [naturalQuery, setNaturalQuery] = useState('');
  const [selectedCity, setSelectedCity] = useState('Dakar');

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    navigate(`/recherche?type=${transactionType}&q=${encodeURIComponent(naturalQuery)}&city=${selectedCity}`);
  };

  return (
    <div className="space-y-16 pb-16">
      
      {/* 1. HERO SECTION WITH HYBRID NATURAL LANGUAGE SEARCH */}
      <section className="relative bg-gradient-sand-hero border-b border-argile-200/80 pt-8 pb-16 px-4 xs:px-6 lg:px-8">
        <div className="max-w-5xl mx-auto text-center space-y-8">
          
          {/* Top Pill Announcement */}
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-terracotta-100/80 border border-terracotta-200 text-terracotta-800 text-xs font-semibold shadow-sm">
            <Sparkles className="w-4 h-4 text-terracotta-500 animate-pulse" />
            <span>Moteur IA & Caution Séquestre N°1 au Sénégal</span>
          </div>

          {/* Main Title */}
          <h1 className="text-3xl xs:text-4xl sm:text-5xl lg:text-6xl font-extrabold text-charcoal-800 tracking-tight leading-[1.15]">
            Trouvez votre futur logement au Sénégal <br className="hidden sm:inline" />
            <span className="text-transparent bg-clip-text gradient-terracotta">
              sans arnaque, avec garanties certifiées.
            </span>
          </h1>

          <p className="text-base sm:text-lg text-charcoal-600 max-w-2xl mx-auto leading-relaxed">
            Trouvez des appartements et villas vérifiés à <strong className="text-sahel-800">Dakar, Ziguinchor, Thiès et Saly</strong>. Votre caution reste sous séquestre sécurisé jusqu’à l’état des lieux.
          </p>

          {/* HYBRID SEARCH CARD */}
          <div className="max-w-3xl mx-auto bg-white p-4 sm:p-5 rounded-3xl shadow-warm-hover border border-argile-300 space-y-4">
            
            {/* Toggle Location / Vente & City Selector */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pb-3 border-b border-argile-200">
              <div className="flex rounded-xl bg-sand-200 p-1 border border-argile-200 w-full sm:w-auto text-xs font-semibold">
                <button
                  type="button"
                  onClick={() => setTransactionType('LOCATION')}
                  className={`px-6 py-2 rounded-lg transition-all ${
                    transactionType === 'LOCATION' ? 'bg-terracotta-500 text-white shadow-terracotta-glow font-bold' : 'text-charcoal-700 hover:text-charcoal-900'
                  }`}
                >
                  Louer
                </button>
                <button
                  type="button"
                  onClick={() => setTransactionType('VENTE')}
                  className={`px-6 py-2 rounded-lg transition-all ${
                    transactionType === 'VENTE' ? 'bg-terracotta-500 text-white shadow-terracotta-glow font-bold' : 'text-charcoal-700 hover:text-charcoal-900'
                  }`}
                >
                  Acheter
                </button>
              </div>

              <div className="flex items-center gap-2 text-xs font-semibold text-charcoal-700 w-full sm:w-auto justify-center">
                <MapPin className="w-4 h-4 text-terracotta-500" />
                <span>Ville :</span>
                {['Dakar', 'Ziguinchor', 'Saly'].map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setSelectedCity(c)}
                    className={`px-3 py-1 rounded-full border transition-all ${
                      selectedCity === c ? 'bg-sahel-800 text-white border-sahel-800' : 'bg-sand-100 text-charcoal-700 border-argile-300 hover:bg-sand-200'
                    }`}
                  >
                    {c}
                  </button>
                ))}
              </div>
            </div>

            {/* Natural Language Free Text Input Form */}
            <form onSubmit={handleSearchSubmit} className="space-y-3">
              <div className="relative flex items-center">
                <div className="absolute left-4 text-terracotta-500 flex items-center gap-1.5 pointer-events-none">
                  <Sparkles className="w-5 h-5" />
                </div>
                <input
                  type="text"
                  value={naturalQuery}
                  onChange={(e) => setNaturalQuery(e.target.value)}
                  placeholder="Ex: 3 pièces à Nema, budget 150 000 FCFA, meublé..."
                  className="w-full pl-12 pr-32 py-4 rounded-2xl border-2 border-argile-300 bg-sand-50/50 text-charcoal-900 text-sm font-medium focus:outline-none focus:border-terracotta-500 focus:ring-4 focus:ring-terracotta-500/10 placeholder:text-charcoal-400 shadow-inner"
                />
                <div className="absolute right-2">
                  <Button type="submit" variant="primary" size="md" leftIcon={<Search className="w-4 h-4" />}>
                    Rechercher
                  </Button>
                </div>
              </div>

              {/* Sample prompt chips */}
              <div className="flex items-center gap-2 overflow-x-auto no-scrollbar pt-1 text-[11px] text-charcoal-500">
                <span className="font-semibold shrink-0 text-sahel-800">Essayez :</span>
                {[
                  'Appart F3 à Nema Ziguinchor < 160k',
                  'Studio meublé Boucotte',
                  'F4 Vue Mer Almadies',
                  'Villa Saly avec piscine',
                ].map((prompt, i) => (
                  <button
                    key={i}
                    type="button"
                    onClick={() => setNaturalQuery(prompt)}
                    className="shrink-0 px-2.5 py-1 rounded-lg bg-sand-200/90 hover:bg-terracotta-100 text-charcoal-700 hover:text-terracotta-800 transition-colors border border-argile-200"
                  >
                    "{prompt}"
                  </button>
                ))}
              </div>
            </form>

          </div>

        </div>
      </section>

      {/* 2. RECHERCHES POPULAIRES PAR QUARTIER */}
      <section className="max-w-7xl mx-auto px-4 xs:px-6 lg:px-8 space-y-6">
        <div className="flex items-end justify-between">
          <div>
            <Badge variant="sahel" className="mb-2">Explorez par zone</Badge>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-charcoal-800">
              Quartiers recherchés au Sénégal
            </h2>
            <p className="text-xs sm:text-sm text-charcoal-600 mt-1">
              Des Almadies à Dakar jusqu’à Nema et Boucotte à Ziguinchor.
            </p>
          </div>
          <Link
            to="/recherche"
            className="hidden sm:flex items-center gap-1 text-xs font-bold text-terracotta-600 hover:text-terracotta-700 transition-colors"
          >
            <span>Voir la carte interactive</span>
            <ChevronRight className="w-4 h-4" />
          </Link>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
          {MOCK_NEIGHBORHOODS.map((nh, idx) => (
            <Link
              key={idx}
              to={`/recherche?neighborhood=${nh.name}`}
              className="group relative h-48 rounded-2xl overflow-hidden shadow-warm-card border border-argile-200 transition-all duration-300 hover:scale-[1.02] hover:shadow-warm-hover"
            >
              <img src={nh.image} alt={nh.name} className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110" />
              <div className="absolute inset-0 bg-gradient-to-t from-charcoal-900/90 via-charcoal-900/40 to-transparent p-4 flex flex-col justify-end text-white">
                <span className="text-[10px] font-bold text-ocher-400 uppercase tracking-wider">{nh.city}</span>
                <h3 className="font-display font-bold text-base text-white group-hover:text-terracotta-200 transition-colors">{nh.name}</h3>
                <p className="text-[11px] text-sand-200 mt-0.5">{nh.propertyCount} logements</p>
                <p className="text-[10px] text-ocher-300 font-mono mt-1">Moy. {nh.avgPriceRent}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* 3. PARCOURS CONFIANCE & SÉQUESTRE (3-4 étapes illustrées) */}
      <section className="bg-sahel-900 text-white py-16 px-4 xs:px-6 lg:px-8">
        <div className="max-w-7xl mx-auto space-y-12">
          
          <div className="text-center space-y-3 max-w-2xl mx-auto">
            <Badge variant="ocher">Sécurité & Transparence</Badge>
            <h2 className="text-2xl sm:text-4xl font-extrabold text-white">
              Comment E-Courtier élimine 100% des arnaques
            </h2>
            <p className="text-sm text-sahel-200 leading-relaxed">
              Un processus transactionnel sécurisé de bout en bout conforme à la réglementation sénégalaise.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            
            {/* Step 1 */}
            <div className="bg-sahel-800 p-6 rounded-2xl border border-sahel-700 space-y-4 relative">
              <div className="w-12 h-12 rounded-xl bg-terracotta-500 text-white font-display font-black text-xl flex items-center justify-center shadow-terracotta-glow">
                01
              </div>
              <h3 className="font-display font-bold text-lg text-white">Assistant IA & Recherche</h3>
              <p className="text-xs text-sahel-200 leading-relaxed">
                Notre assistant IA analyse vos critères exacts et filtre les annonces frauduleuses dispersées sur les réseaux sociaux.
              </p>
              <div className="pt-2 flex items-center gap-1.5 text-[11px] font-semibold text-ocher-400">
                <Sparkles className="w-4 h-4" /> Filtres intelligents
              </div>
            </div>

            {/* Step 2 */}
            <div className="bg-sahel-800 p-6 rounded-2xl border border-sahel-700 space-y-4 relative">
              <div className="w-12 h-12 rounded-xl bg-terracotta-500 text-white font-display font-black text-xl flex items-center justify-center shadow-terracotta-glow">
                02
              </div>
              <h3 className="font-display font-bold text-lg text-white">Certification Cadastrale</h3>
              <p className="text-xs text-sahel-200 leading-relaxed">
                Le titre foncier et l'identité du propriétaire ou courtier sont audités physiquement avec visite vidéo 360° certifiée.
              </p>
              <div className="pt-2 flex items-center gap-1.5 text-[11px] font-semibold text-ocher-400">
                <ShieldCheck className="w-4 h-4" /> Badge de Preuve Validé
              </div>
            </div>

            {/* Step 3 */}
            <div className="bg-sahel-800 p-6 rounded-2xl border border-sahel-700 space-y-4 relative">
              <div className="w-12 h-12 rounded-xl bg-terracotta-500 text-white font-display font-black text-xl flex items-center justify-center shadow-terracotta-glow">
                03
              </div>
              <h3 className="font-display font-bold text-lg text-white">Caution sous Séquestre</h3>
              <p className="text-xs text-sahel-200 leading-relaxed">
                Votre caution et 1er mois d'avance restent bloqués sur un compte séquestre et ne sont transférés qu'après l'état des lieux.
              </p>
              <div className="pt-2 flex items-center gap-1.5 text-[11px] font-semibold text-ocher-400">
                <Lock className="w-4 h-4" /> Fonds 100% Protégés
              </div>
            </div>

            {/* Step 4 */}
            <div className="bg-sahel-800 p-6 rounded-2xl border border-sahel-700 space-y-4 relative">
              <div className="w-12 h-12 rounded-xl bg-terracotta-500 text-white font-display font-black text-xl flex items-center justify-center shadow-terracotta-glow">
                04
              </div>
              <h3 className="font-display font-bold text-lg text-white">Bail Digital COCC</h3>
              <p className="text-xs text-sahel-200 leading-relaxed">
                Génération automatique du contrat de bail sénégalais légal avec signature électronique et délivrance immédiate des clés.
              </p>
              <div className="pt-2 flex items-center gap-1.5 text-[11px] font-semibold text-ocher-400">
                <FileText className="w-4 h-4" /> Conforme Loi Sénégal
              </div>
            </div>

          </div>

        </div>
      </section>

      {/* 4. ANNONCES VÉRIFIÉES À LA UNE */}
      <section className="max-w-7xl mx-auto px-4 xs:px-6 lg:px-8 space-y-6">
        <div className="flex items-end justify-between">
          <div>
            <Badge variant="terracotta" className="mb-2">Nouveautés Certifiées</Badge>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-charcoal-800">
              Dernières annonces vérifiées
            </h2>
          </div>
          <Link
            to="/recherche"
            className="flex items-center gap-1 text-xs font-bold text-terracotta-600 hover:text-terracotta-700 transition-colors"
          >
            <span>Tout afficher ({MOCK_PROPERTIES.length})</span>
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {MOCK_PROPERTIES.slice(0, 3).map((prop) => (
            <Card key={prop.id} className="group hover:border-terracotta-300">
              <div className="relative h-56 overflow-hidden bg-sand-200">
                <img
                  src={prop.images[0]}
                  alt={prop.title}
                  className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                />
                <div className="absolute top-3 left-3 flex flex-wrap gap-1.5">
                  <Badge variant="terracotta">
                    <ShieldCheck className="w-3 h-3 mr-1 inline" />
                    Vérifiée NIVEAU {prop.verificationLevel}
                  </Badge>
                </div>
                <div className="absolute bottom-3 right-3 bg-charcoal-900/80 text-white font-extrabold px-3 py-1.5 rounded-xl text-sm backdrop-blur-xs">
                  {prop.price.toLocaleString('fr-FR')} FCFA{prop.pricePeriod}
                </div>
              </div>

              <CardBody className="space-y-3">
                <div className="text-xs text-sahel-800 font-semibold flex items-center gap-1">
                  <MapPin className="w-3.5 h-3.5 text-terracotta-500" />
                  <span>{prop.neighborhood}, {prop.city}</span>
                </div>
                <h3 className="font-display font-bold text-base text-charcoal-900 line-clamp-1 group-hover:text-terracotta-600 transition-colors">
                  {prop.title}
                </h3>
                <div className="flex items-center gap-3 text-xs text-charcoal-500 border-y border-argile-200 py-2">
                  <span>📐 {prop.surface} m²</span>
                  <span>🚪 {prop.rooms} pièces</span>
                  <span>🛏️ {prop.bedrooms} ch.</span>
                  {prop.hasGenerator && <span>⚡ Groupe</span>}
                </div>
                <div className="flex items-center justify-between pt-1">
                  <div className="flex items-center gap-2">
                    <img src={prop.owner.avatar} alt={prop.owner.name} className="w-7 h-7 rounded-full object-cover" />
                    <span className="text-xs text-charcoal-700 font-medium">{prop.owner.name}</span>
                  </div>
                  <Link to={`/annonce/${prop.id}`}>
                    <Button variant="outline" size="sm">
                      Détails
                    </Button>
                  </Link>
                </div>
              </CardBody>
            </Card>
          ))}
        </div>
      </section>

    </div>
  );
};
