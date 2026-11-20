import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, MapPin, Search, ShieldCheck, Wallet } from 'lucide-react';
import { motion, useReducedMotion } from 'framer-motion';
import { useReferences } from '../../hooks/useReferences';
import {
  BUDGET_OPTIONS_LOCATION,
  BUDGET_OPTIONS_VENTE,
  HOME_CITIES,
  SEARCH_SUGGESTIONS,
} from './homeData';

const DEFAULT_PROPERTY_TYPES = [
  { valeur: 'APPARTEMENT', libelle: 'Appartement' },
  { valeur: 'VILLA', libelle: 'Villa' },
  { valeur: 'STUDIO', libelle: 'Studio' },
  { valeur: 'CHAMBRE', libelle: 'Chambre' },
];

export const HomeSearchEngine: React.FC = () => {
  const navigate = useNavigate();
  const shouldReduceMotion = useReducedMotion();
  const { data: references } = useReferences();

  const [modeTransaction, setModeTransaction] = useState<'LOCATION' | 'VENTE' | 'TOUS'>('LOCATION');
  const [city, setCity] = useState<string>('TOUS');
  const [propertyType, setPropertyType] = useState<string>('');
  const [budgetMax, setBudgetMax] = useState<string>('');
  const [naturalQuery, setNaturalQuery] = useState<string>('');
  const [onlyVerified, setOnlyVerified] = useState<boolean>(false);

  const propertyTypes = references?.typesBien?.length ? references.typesBien : DEFAULT_PROPERTY_TYPES;
  const budgetOptions = modeTransaction === 'VENTE' ? BUDGET_OPTIONS_VENTE : BUDGET_OPTIONS_LOCATION;

  const handleModeChange = (nextMode: 'LOCATION' | 'VENTE' | 'TOUS') => {
    setModeTransaction(nextMode);
    setBudgetMax('');
  };

  const buildAndNavigate = (overrides?: {
    mode?: 'LOCATION' | 'VENTE' | 'TOUS';
    ville?: string;
    type?: string;
    budgetMax?: string;
    q?: string;
    verified?: boolean;
  }) => {
    const finalMode = overrides?.mode ?? modeTransaction;
    const finalCity = overrides?.ville ?? city;
    const finalType = overrides?.type ?? propertyType;
    const finalBudget = overrides?.budgetMax ?? budgetMax;
    const finalQuery = (overrides?.q ?? naturalQuery).trim();
    const finalVerified = overrides?.verified ?? onlyVerified;

    const params = new URLSearchParams();
    if (finalMode !== 'TOUS') params.set('modeTransaction', finalMode);
    if (finalCity && finalCity !== 'TOUS') params.set('ville', finalCity);
    if (finalType) params.set('type', finalType);
    if (finalBudget) params.set('budgetMax', finalBudget);
    if (finalQuery) params.set('q', finalQuery);
    if (finalVerified) params.set('statutVerification', 'VERIFIEE');

    const queryString = params.toString();
    navigate(queryString ? `/recherche?${queryString}` : '/recherche');
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    buildAndNavigate();
  };

  const handleSuggestionClick = (suggestion: (typeof SEARCH_SUGGESTIONS)[number]) => {
    const nextMode = suggestion.mode;
    const nextCity = suggestion.ville;
    const nextType = 'type' in suggestion ? suggestion.type : '';
    const nextBudget = 'budgetMax' in suggestion ? suggestion.budgetMax : '';
    const nextVerified = 'verified' in suggestion ? Boolean(suggestion.verified) : onlyVerified;
    const nextQ = suggestion.q;

    setModeTransaction(nextMode);
    setCity(nextCity);
    setPropertyType(nextType);
    setBudgetMax(nextBudget);
    setNaturalQuery(nextQ);
    setOnlyVerified(nextVerified);

    buildAndNavigate({
      mode: nextMode,
      ville: nextCity,
      type: nextType,
      budgetMax: nextBudget,
      q: nextQ,
      verified: nextVerified,
    });
  };

  return (
    <motion.div
      initial={shouldReduceMotion ? false : { opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
      className="relative z-20 max-w-6xl mx-auto px-3 xs:px-5 sm:px-6 -mt-9 sm:-mt-12"
    >
      <div className="bg-white rounded-2xl border border-[#E2DACB] shadow-[0_24px_60px_-15px_rgba(37,42,41,0.16)] p-4 sm:p-5 lg:p-6 space-y-4">
        {/* Top Mode Switch + Verified Toggle */}
        <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-[#EFE8DC]">
          <div
            className="inline-flex rounded-full bg-[#F5F0E8] p-1 border border-[#E2DACB]"
            role="group"
            aria-label="Mode de transaction"
          >
            {[
              { key: 'LOCATION', label: 'Louer' },
              { key: 'VENTE', label: 'Acheter' },
              { key: 'TOUS', label: 'Tout' },
            ].map((item) => {
              const active = modeTransaction === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  aria-pressed={active}
                  onClick={() => handleModeChange(item.key as 'LOCATION' | 'VENTE' | 'TOUS')}
                  className={`min-h-[36px] px-4 sm:px-5 py-1.5 rounded-full text-xs font-semibold transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] focus-visible:ring-offset-1 ${
                    active
                      ? 'bg-[#B85C42] text-white shadow-sm'
                      : 'text-[#252A29]/85 hover:text-[#252A29] hover:bg-white/60'
                  }`}
                >
                  {item.label}
                </button>
              );
            })}
          </div>

          <button
            type="button"
            role="switch"
            aria-checked={onlyVerified}
            onClick={() => setOnlyVerified((prev) => !prev)}
            className={`min-h-[36px] inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-medium border transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] focus-visible:ring-offset-1 ${
              onlyVerified
                ? 'bg-[#1C352E] text-white border-[#1C352E]'
                : 'bg-[#FAF7F2] text-[#252A29] border-[#E2DACB] hover:border-[#B85C42]/50'
            }`}
          >
            <ShieldCheck
              className={`w-3.5 h-3.5 ${onlyVerified ? 'text-[#D5AD72]' : 'text-[#1C352E]'}`}
              aria-hidden="true"
            />
            <span>Biens vérifiés uniquement</span>
          </button>
        </div>

        {/* Multi-Criteria SaaS Search Form */}
        <form onSubmit={handleSubmit} className="space-y-4" role="search" aria-label="Moteur de recherche immobilière">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-12 gap-3.5 lg:gap-3 items-end">
            {/* Ville */}
            <div className="lg:col-span-2">
              <label
                htmlFor="home-search-city"
                className="flex items-center gap-1.5 text-xs font-semibold text-[#3F4745] mb-1.5"
              >
                <MapPin className="w-3.5 h-3.5 text-[#B85C42]" aria-hidden="true" />
                <span>Ville</span>
              </label>
              <select
                id="home-search-city"
                value={city}
                onChange={(e) => setCity(e.target.value)}
                className="w-full h-11 sm:h-12 px-3.5 rounded-xl border border-[#D8CEBC] bg-[#FAF7F2] text-sm font-medium text-[#252A29] transition-colors focus:outline-none focus:border-[#B85C42] focus:ring-2 focus:ring-[#B85C42]/20"
              >
                <option value="TOUS">Toutes les villes</option>
                {HOME_CITIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>

            {/* Type de bien */}
            <div className="lg:col-span-2">
              <label
                htmlFor="home-search-type"
                className="flex items-center gap-1.5 text-xs font-semibold text-[#3F4745] mb-1.5"
              >
                <Building2 className="w-3.5 h-3.5 text-[#B85C42]" aria-hidden="true" />
                <span>Type</span>
              </label>
              <select
                id="home-search-type"
                value={propertyType}
                onChange={(e) => setPropertyType(e.target.value)}
                className="w-full h-11 sm:h-12 px-3.5 rounded-xl border border-[#D8CEBC] bg-[#FAF7F2] text-sm font-medium text-[#252A29] transition-colors focus:outline-none focus:border-[#B85C42] focus:ring-2 focus:ring-[#B85C42]/20"
              >
                <option value="">Tous les types</option>
                {propertyTypes.map((t) => (
                  <option key={t.valeur} value={t.valeur}>
                    {t.libelle}
                  </option>
                ))}
              </select>
            </div>

            {/* Budget */}
            <div className="lg:col-span-2">
              <label
                htmlFor="home-search-budget"
                className="flex items-center gap-1.5 text-xs font-semibold text-[#3F4745] mb-1.5"
              >
                <Wallet className="w-3.5 h-3.5 text-[#B85C42]" aria-hidden="true" />
                <span>Budget max</span>
              </label>
              <select
                id="home-search-budget"
                value={budgetMax}
                onChange={(e) => setBudgetMax(e.target.value)}
                className="w-full h-11 sm:h-12 px-3.5 rounded-xl border border-[#D8CEBC] bg-[#FAF7F2] text-sm font-medium text-[#252A29] transition-colors focus:outline-none focus:border-[#B85C42] focus:ring-2 focus:ring-[#B85C42]/20"
              >
                {budgetOptions.map((opt) => (
                  <option key={opt.label} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            {/* Free-text query */}
            <div className="lg:col-span-4">
              <label
                htmlFor="home-search-query"
                className="flex items-center gap-1.5 text-xs font-semibold text-[#3F4745] mb-1.5"
              >
                <Search className="w-3.5 h-3.5 text-[#B85C42]" aria-hidden="true" />
                <span>Quartier ou mot-clé</span>
              </label>
              <input
                id="home-search-query"
                type="text"
                value={naturalQuery}
                onChange={(e) => setNaturalQuery(e.target.value)}
                placeholder="Ex : Almadies, Mermoz, meublé, F3…"
                className="w-full h-11 sm:h-12 px-3.5 rounded-xl border border-[#D8CEBC] bg-[#FAF7F2] text-sm font-medium text-[#252A29] placeholder:text-[#737B78] transition-colors focus:outline-none focus:border-[#B85C42] focus:ring-2 focus:ring-[#B85C42]/20"
              />
            </div>

            {/* Primary CTA */}
            <div className="sm:col-span-2 lg:col-span-2">
              <button
                type="submit"
                className="w-full h-11 sm:h-12 px-5 rounded-xl bg-[#B85C42] hover:bg-[#9D4A32] active:scale-[0.98] text-white text-sm font-semibold inline-flex items-center justify-center gap-2 shadow-[0_8px_20px_-6px_rgba(184,92,66,0.5)] transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42] focus-visible:ring-offset-2"
              >
                <Search className="w-4 h-4 shrink-0" aria-hidden="true" />
                <span>Rechercher</span>
              </button>
            </div>
          </div>

          {/* Quick Suggestion Chips separated cleanly from main criteria */}
          <div className="flex items-center gap-2 pt-3 border-t border-[#F0EAE0] overflow-x-auto sm:overflow-visible sm:flex-wrap no-scrollbar">
            <span className="text-xs font-semibold text-[#4F5654] shrink-0">
              Accès rapides :
            </span>
            {SEARCH_SUGGESTIONS.map((item) => (
              <button
                key={item.label}
                type="button"
                onClick={() => handleSuggestionClick(item)}
                className="shrink-0 min-h-[32px] px-3 py-1 rounded-full bg-[#F5F0E8] hover:bg-[#EFE4D3] border border-[#E2DACB] hover:border-[#B85C42]/50 text-xs font-medium text-[#252A29] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#B85C42]"
              >
                {item.label}
              </button>
            ))}
          </div>
        </form>
      </div>
    </motion.div>
  );
};
