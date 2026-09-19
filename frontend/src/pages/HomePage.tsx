import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Search, MapPin, Sparkles, ShieldCheck, Lock, FileText, ArrowRight, ChevronRight } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { apiFetch, getPhotoUrl } from '../services/api';
import { AnnonceDTO, PageResponse } from '../types';
import { Button } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { PatternClaustra } from '../components/ui/PatternClaustra';

const NEIGHBORHOODS_LIST = [
  { name: 'Almadies', city: 'Dakar', image: 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=600&q=80', avgPriceRent: '350 000 FCFA' },
  { name: 'Nema', city: 'Ziguinchor', image: 'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=600&q=80', avgPriceRent: '150 000 FCFA' },
  { name: 'Sacré-Cœur', city: 'Dakar', image: 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=600&q=80', avgPriceRent: '250 000 FCFA' },
  { name: 'Boucotte', city: 'Ziguinchor', image: 'https://images.unsplash.com/photo-1570129477492-45c003edd2be?auto=format&fit=crop&w=600&q=80', avgPriceRent: '120 000 FCFA' },
  { name: 'Plateau', city: 'Dakar', image: 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=600&q=80', avgPriceRent: '400 000 FCFA' },
  { name: 'Saly Station', city: 'Saly', image: 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=600&q=80', avgPriceRent: '300 000 FCFA' },
];

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const [transactionType, setTransactionType] = useState<'LOCATION' | 'VENTE'>('LOCATION');
  const [naturalQuery, setNaturalQuery] = useState('');
  const [selectedCity, setSelectedCity] = useState('Dakar');

  // Fetch real featured listings from API
  const { data: featuredData, isLoading } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: ['featured-annonces'],
    queryFn: () => apiFetch<PageResponse<AnnonceDTO>>('/api/v1/annonces?page=0&size=3&tri=dateCreation&direction=DESC'),
  });

  const featuredListings = featuredData?.content || [];

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    navigate(`/recherche?modeTransaction=${transactionType}&q=${encodeURIComponent(naturalQuery)}&city=${selectedCity}`);
  };

  return (
    <div className="space-y-20 pb-20 bg-sand-100/60 overflow-hidden">
      
      {/* 1. ASYMMETRICAL EDITORIAL HERO WITH SIGNATURE CLAUSTRA WATERMARK */}
      <section className="relative bg-gradient-sand-hero border-b border-argile-200/90 pt-10 pb-20 px-4 xs:px-6 lg:px-12 overflow-hidden">
        
        {/* Signature Claustra Motif Backdrop */}
        <PatternClaustra variant="terracotta" opacity={0.05} scale={1.2} />

        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-12 items-center relative z-10">
          
          {/* LEFT 65%: EDITORIAL TYPOGRAPHY & NATURAL SEARCH FORM */}
          <div className="lg:col-span-7 space-y-7 text-left">
            
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-terracotta-100/90 border border-terracotta-200/80 text-terracotta-900 text-xs font-semibold shadow-xs">
              <Sparkles className="w-3.5 h-3.5 text-terracotta-600 animate-pulse" />
              <span>Contrôle cadastral & Séquestre bancaire &bull; Transactions sans arnaque</span>
            </div>

            <h1 className="text-3xl xs:text-4xl sm:text-5xl lg:text-6xl font-extrabold text-charcoal-900 tracking-tight leading-[1.12]">
              L’immobilier au Sénégal, <br className="hidden sm:inline" />
              <span className="text-transparent bg-clip-text gradient-terracotta">
                sans frais de visite abusifs ni cautions envolées.
              </span>
            </h1>

            <p className="text-sm sm:text-base text-charcoal-700 leading-relaxed max-w-xl">
              Fini les faux courtiers et les avances exigées par WhatsApp. E-Courtier sécurise votre location ou achat à <strong>Dakar, Ziguinchor et Saly</strong> avec contrôle cadastral et séquestre bancaire.
            </p>

            {/* ASYMMETRICAL SEARCH BOX */}
            <div className="bg-white p-4 sm:p-6 rounded-2xl shadow-warm-hover border border-argile-300 space-y-4 relative">
              
              {/* Transaction Type & City Bar */}
              <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-argile-200/80">
                <div className="flex rounded-full bg-sand-200/80 p-1 border border-argile-200 text-xs font-semibold">
                  <button
                    type="button"
                    onClick={() => setTransactionType('LOCATION')}
                    className={`px-5 py-1.5 rounded-full transition-all ${
                      transactionType === 'LOCATION'
                        ? 'bg-terracotta-500 text-white shadow-terracotta-glow font-bold'
                        : 'text-charcoal-700 hover:text-charcoal-900'
                    }`}
                  >
                    Louer
                  </button>
                  <button
                    type="button"
                    onClick={() => setTransactionType('VENTE')}
                    className={`px-5 py-1.5 rounded-full transition-all ${
                      transactionType === 'VENTE'
                        ? 'bg-terracotta-500 text-white shadow-terracotta-glow font-bold'
                        : 'text-charcoal-700 hover:text-charcoal-900'
                    }`}
                  >
                    Acheter
                  </button>
                </div>

                <div className="flex items-center gap-1.5 text-xs font-semibold text-charcoal-700">
                  <MapPin className="w-3.5 h-3.5 text-terracotta-500" />
                  <span className="text-charcoal-500">Ville:</span>
                  {['Dakar', 'Ziguinchor', 'Saly'].map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setSelectedCity(c)}
                      className={`px-3 py-1 rounded-full border text-xs transition-all ${
                        selectedCity === c
                          ? 'bg-sahel-800 text-white border-sahel-800 font-bold'
                          : 'bg-sand-100 text-charcoal-700 border-argile-300 hover:bg-sand-200'
                      }`}
                    >
                      {c}
                    </button>
                  ))}
                </div>
              </div>

              {/* Natural Language Search Input */}
              <form onSubmit={handleSearchSubmit} className="space-y-3">
                <div className="relative flex items-center">
                  <input
                    type="text"
                    value={naturalQuery}
                    onChange={(e) => setNaturalQuery(e.target.value)}
                    placeholder="Ex: 3 pièces à Nema Ziguinchor, budget 150 000 FCFA..."
                    className="w-full pl-4 pr-32 py-3.5 rounded-md border border-argile-300 bg-sand-50/50 text-charcoal-900 text-sm font-medium focus:outline-none focus:border-terracotta-500 focus:ring-2 focus:ring-terracotta-500/20 placeholder:text-charcoal-400"
                  />
                  <div className="absolute right-1.5">
                    <Button type="submit" variant="primary" size="md" leftIcon={<Search className="w-4 h-4" />}>
                      Rechercher
                    </Button>
                  </div>
                </div>

                {/* Prompt Chips */}
                <div className="flex items-center gap-2 overflow-x-auto no-scrollbar text-[11px] text-charcoal-500 pt-0.5">
                  <span className="font-semibold text-sahel-800 shrink-0">Suggestions :</span>
                  {[
                    'F3 Nema Ziguinchor < 160k',
                    'Studio meublé Boucotte',
                    'Vue Mer Almadies',
                    'Villa Saly avec piscine',
                  ].map((prompt, i) => (
                    <button
                      key={i}
                      type="button"
                      onClick={() => setNaturalQuery(prompt)}
                      className="shrink-0 px-2.5 py-1 rounded-full bg-sand-200/70 hover:bg-terracotta-100 text-charcoal-700 hover:text-terracotta-800 transition-colors border border-argile-200"
                    >
                      "{prompt}"
                    </button>
                  ))}
                </div>
              </form>

            </div>

          </div>

          {/* RIGHT 35%: ARCHITECTURAL PREVIEW COMPOSITION */}
          <div className="lg:col-span-5 relative">
            <div className="relative mx-auto max-w-md lg:max-w-none">
              
              {/* Backing Accent Frame */}
              <div className="absolute -inset-3 bg-gradient-to-tr from-terracotta-500/15 via-ocher-500/10 to-sahel-800/15 rounded-3xl transform rotate-2 blur-xs pointer-events-none" />

              {/* Main Architectural Showcase Card */}
              <div className="relative bg-white rounded-2xl p-4 shadow-warm-hover border border-argile-300 space-y-4">
                <div className="relative h-64 rounded-xl overflow-hidden bg-sand-200 border border-argile-200">
                  <img
                    src="https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&q=80"
                    alt="Architecture Sénégal"
                    className="w-full h-full object-cover"
                  />
                  <div className="absolute top-3 left-3 bg-sahel-900/90 text-white text-[11px] font-bold px-3 py-1 rounded-full backdrop-blur-xs border border-sahel-700 flex items-center gap-1.5">
                    <ShieldCheck className="w-3.5 h-3.5 text-ocher-400" />
                    <span>Contrôle Cadastral Validé</span>
                  </div>
                  <div className="absolute bottom-3 right-3 bg-charcoal-900/85 text-white font-extrabold px-3 py-1.5 rounded-lg text-xs backdrop-blur-xs">
                    250 000 FCFA / mois
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between text-xs text-sahel-800 font-semibold">
                    <span className="flex items-center gap-1">
                      <MapPin className="w-3.5 h-3.5 text-terracotta-500" />
                      Sacré-Cœur 3, Dakar
                    </span>
                    <Badge variant="sahel">Disponibilité Vérifiée</Badge>
                  </div>
                  <h3 className="font-display font-bold text-base text-charcoal-900">
                    Appartement F3 avec Balcon & Groupe Électrogène
                  </h3>
                  <div className="pt-2 flex items-center justify-between text-xs border-t border-argile-200/80 text-charcoal-600">
                    <span>Garantie Séquestre &bull; 0 CFA Frais caché</span>
                    <Link to="/recherche" className="text-terracotta-600 font-bold hover:underline">
                      Explorer sur la carte &rarr;
                    </Link>
                  </div>
                </div>
              </div>

            </div>
          </div>

        </div>
      </section>

      {/* 2. RECHERCHES POPULAIRES PAR ZONE */}
      <section className="max-w-7xl mx-auto px-4 xs:px-6 lg:px-12 space-y-8">
        <div className="flex items-end justify-between border-b border-argile-200 pb-4">
          <div>
            <Badge variant="sahel" className="mb-2">Territoire & Quartiers</Badge>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-charcoal-900">
              Quartiers recherchés au Sénégal
            </h2>
            <p className="text-xs sm:text-sm text-charcoal-600 mt-1">
              Explorez les logements disponibles par zone, des Almadies à Dakar jusqu’à Nema et Boucotte à Ziguinchor.
            </p>
          </div>
          <Link
            to="/recherche"
            className="hidden sm:flex items-center gap-1 text-xs font-bold text-terracotta-600 hover:text-terracotta-700 transition-colors"
          >
            <span>Carte interactive</span>
            <ChevronRight className="w-4 h-4" />
          </Link>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
          {NEIGHBORHOODS_LIST.map((nh, idx) => (
            <Link
              key={idx}
              to={`/recherche?city=${nh.city}`}
              className="group relative h-48 rounded-xl overflow-hidden border border-argile-200 transition-all duration-300 hover:border-terracotta-400 hover:shadow-terracotta-card"
            >
              <img src={nh.image} alt={nh.name} className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105" />
              <div className="absolute inset-0 bg-gradient-to-t from-charcoal-900/90 via-charcoal-900/40 to-transparent p-3.5 flex flex-col justify-end text-white">
                <span className="text-[10px] font-bold text-ocher-400 uppercase tracking-wider">{nh.city}</span>
                <h3 className="font-display font-bold text-sm text-white group-hover:text-terracotta-200 transition-colors">{nh.name}</h3>
                <p className="text-[10px] text-ocher-300 font-semibold mt-0.5 flex items-center gap-0.5">
                  <span>Voir biens</span> &rarr;
                </p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* 3. NARRATIVE STORYTELLING "COMMENT ÇA MARCHE" */}
      <section className="relative bg-sahel-900 text-white py-20 px-4 xs:px-6 lg:px-12 overflow-hidden">
        
        {/* Subtle Background Pattern */}
        <PatternClaustra variant="sand" opacity={0.04} scale={1.5} />

        <div className="max-w-7xl mx-auto space-y-12 relative z-10">
          
          <div className="max-w-2xl space-y-3">
            <Badge variant="ocher">Sécurité Contractuelle</Badge>
            <h2 className="text-2xl sm:text-4xl font-extrabold text-white leading-tight">
              Comment E-Courtier sécurise votre transaction de A à Z
            </h2>
            <p className="text-sm text-sahel-200 leading-relaxed">
              Un parcours transparent et opposable en justice, éliminant les arnaques au paiement avant visite.
            </p>
          </div>

          {/* OVERLAPPING NARRATIVE CARDS */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            
            {/* Step 1 */}
            <div className="bg-sahel-800 p-6 rounded-2xl border border-sahel-700 space-y-4 hover:border-terracotta-400/60 transition-all duration-300 shadow-sahel-card">
              <div className="flex items-center justify-between">
                <span className="font-display font-black text-3xl text-terracotta-400 tracking-tighter">01</span>
                <div className="p-2 rounded-lg bg-sahel-700/60 text-ocher-400">
                  <Sparkles className="w-5 h-5" />
                </div>
              </div>
              <h3 className="font-display font-bold text-lg text-white">Recherche & Filtre IA</h3>
              <p className="text-xs text-sahel-200 leading-relaxed">
                Recherchez en langage naturel. L’assistant IA extrait vos critères exacts et écarte tout bien non contrôlé.
              </p>
              <div className="pt-2 text-[11px] text-ocher-300 font-semibold border-t border-sahel-700">
                &check; Zéro faux frais de dossier
              </div>
            </div>

            {/* Step 2 */}
            <div className="bg-sahel-800 p-6 rounded-2xl border border-sahel-700 space-y-4 hover:border-terracotta-400/60 transition-all duration-300 shadow-sahel-card">
              <div className="flex items-center justify-between">
                <span className="font-display font-black text-3xl text-terracotta-400 tracking-tighter">02</span>
                <div className="p-2 rounded-lg bg-sahel-700/60 text-ocher-400">
                  <ShieldCheck className="w-5 h-5" />
                </div>
              </div>
              <h3 className="font-display font-bold text-lg text-white">Contrôle Cadastral</h3>
              <p className="text-xs text-sahel-200 leading-relaxed">
                Vérification du titre foncier, de l’identité du bailleur ou de l'agrément de l’agent immobilier avant toute prise de rendez-vous.
              </p>
              <div className="pt-2 text-[11px] text-ocher-300 font-semibold border-t border-sahel-700">
                &check; Identité & Titre vérifiés
              </div>
            </div>

            {/* Step 3 */}
            <div className="bg-sahel-800 p-6 rounded-2xl border border-sahel-700 space-y-4 hover:border-terracotta-400/60 transition-all duration-300 shadow-sahel-card">
              <div className="flex items-center justify-between">
                <span className="font-display font-black text-3xl text-terracotta-400 tracking-tighter">03</span>
                <div className="p-2 rounded-lg bg-sahel-700/60 text-ocher-400">
                  <Lock className="w-5 h-5" />
                </div>
              </div>
              <h3 className="font-display font-bold text-lg text-white">Caution Séquestrée</h3>
              <p className="text-xs text-sahel-200 leading-relaxed">
                Votre dépôt de garantie est consigné sur un compte séquestre bancaire et libéré uniquement après validation de l’état des lieux.
              </p>
              <div className="pt-2 text-[11px] text-ocher-300 font-semibold border-t border-sahel-700">
                &check; Fonds 100% consignés
              </div>
            </div>

            {/* Step 4 */}
            <div className="bg-sahel-800 p-6 rounded-2xl border border-sahel-700 space-y-4 hover:border-terracotta-400/60 transition-all duration-300 shadow-sahel-card">
              <div className="flex items-center justify-between">
                <span className="font-display font-black text-3xl text-terracotta-400 tracking-tighter">04</span>
                <div className="p-2 rounded-lg bg-sahel-700/60 text-ocher-400">
                  <FileText className="w-5 h-5" />
                </div>
              </div>
              <h3 className="font-display font-bold text-lg text-white">Bail Numérique COCC</h3>
              <p className="text-xs text-sahel-200 leading-relaxed">
                Génération du contrat officiel conforme au Code des Obligations Civiles du Sénégal avec double signature électronique.
              </p>
              <div className="pt-2 text-[11px] text-ocher-300 font-semibold border-t border-sahel-700">
                &check; Signature électronique légale
              </div>
            </div>

          </div>

        </div>
      </section>

      {/* 4. REAL RECENT LISTINGS FROM BACKEND */}
      <section className="max-w-7xl mx-auto px-4 xs:px-6 lg:px-12 space-y-8">
        <div className="flex items-end justify-between border-b border-argile-200 pb-4">
          <div>
            <Badge variant="terracotta" className="mb-2">Annonces Vérifiées</Badge>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-charcoal-900">
              Derniers logements vérifiés
            </h2>
            <p className="text-xs sm:text-sm text-charcoal-600 mt-1">
              Des biens avec titre vérifié et disponibilité confirmée en temps réel.
            </p>
          </div>
          <Link
            to="/recherche"
            className="flex items-center gap-1 text-xs font-bold text-terracotta-600 hover:text-terracotta-700 transition-colors"
          >
            <span>Voir tout le catalogue</span>
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} variant="card" className="h-64" />
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {featuredListings.map((prop) => {
              const photoUrl = prop.photos && prop.photos.length > 0 ? getPhotoUrl(prop.photos[0].url) : getPhotoUrl();
              return (
                <Card key={prop.id} className="group hover:border-terracotta-400 hover:shadow-warm-hover transition-all duration-300">
                  <div className="relative h-56 overflow-hidden bg-sand-200 border-b border-argile-200">
                    <img
                      src={photoUrl}
                      alt={prop.titre}
                      className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                    />
                    {prop.statutVerification === 'VERIFIEE' && (
                      <div className="absolute top-3 left-3">
                        <Badge variant="sahel" className="shadow-xs font-bold">
                          <ShieldCheck className="w-3.5 h-3.5 mr-1 inline" />
                          Vérifiée
                        </Badge>
                      </div>
                    )}
                    <div className="absolute bottom-3 right-3 bg-charcoal-900/85 text-white font-extrabold px-3 py-1 rounded-md text-xs backdrop-blur-xs">
                      {prop.prix.toLocaleString('fr-FR')} FCFA {prop.modeTransaction === 'LOCATION' ? '/ mois' : ''}
                    </div>
                  </div>

                  <CardBody className="space-y-3">
                    <div className="text-xs text-sahel-800 font-semibold flex items-center gap-1">
                      <MapPin className="w-3.5 h-3.5 text-terracotta-500" />
                      <span>{prop.quartier}, {prop.ville}</span>
                    </div>
                    <h3 className="font-display font-bold text-base text-charcoal-900 line-clamp-1 group-hover:text-terracotta-600 transition-colors">
                      {prop.titre}
                    </h3>

                    {/* REFINED VECTOR BADGE SYSTEM (NO RAW EMOJIS) */}
                    <div className="flex items-center gap-3 text-xs text-charcoal-600 border-y border-argile-200 py-2">
                      <span className="font-mono bg-sand-200/80 px-2 py-0.5 rounded text-[11px]">{prop.superficie} m²</span>
                      <span className="font-mono bg-sand-200/80 px-2 py-0.5 rounded text-[11px]">{prop.nombrePieces} pièces</span>
                      {prop.meuble && <span className="bg-terracotta-100 text-terracotta-800 text-[10px] font-bold px-2 py-0.5 rounded">Meublé</span>}
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      <span className="text-xs text-charcoal-600 font-medium">{prop.auteur?.prenom} {prop.auteur?.nom}</span>
                      <Link to={`/annonce/${prop.id}`}>
                        <Button variant="outline" size="sm">
                          Consulter la fiche
                        </Button>
                      </Link>
                    </div>
                  </CardBody>
                </Card>
              );
            })}
          </div>
        )}
      </section>

    </div>
  );
};
