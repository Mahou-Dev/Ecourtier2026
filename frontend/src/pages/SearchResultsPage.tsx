import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
  SlidersHorizontal,
  MapPin,
  ShieldCheck,
  Search,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  X,
  Check,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { motion, useReducedMotion } from 'framer-motion';
import { apiFetch, getPhotoUrl } from '../services/api';
import { AnnonceDTO, PageResponse } from '../types';
import { LeafletMap } from '../components/map/LeafletMap';
import { Button } from '../components/ui/Button';
import { Slider } from '../components/ui/Slider';
import { Drawer } from '../components/ui/Drawer';
import { Skeleton } from '../components/ui/Skeleton';
import { FavoriteButton } from '../components/ui/FavoriteButton';
import { useReferences } from '../hooks/useReferences';
import { QuartierAutocomplete } from '../components/ui/QuartierAutocomplete';
import { getProximiteMeta } from '../components/ui/ReferenceIcons';

export const SearchResultsPage: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const [searchParams, setSearchParams] = useSearchParams();
  const { data: references } = useReferences();

  const [selectedPropertyId, setSelectedPropertyId] = useState<string | null>(null);
  const [isFilterDrawerOpen, setIsFilterDrawerOpen] = useState(false);

  // Initialize filters from URL params
  const [modeTransaction, setModeTransaction] = useState<string>(
    searchParams.get('modeTransaction') || 'TOUS'
  );

  const [selectedTypes, setSelectedTypes] = useState<string[]>(() => {
    const fromUrl = searchParams.getAll('type');
    if (fromUrl.length > 0) return fromUrl;
    const single = searchParams.get('type');
    return single && single !== 'TOUS' ? [single] : [];
  });

  const [cityFilter, setCityFilter] = useState<string>(
    searchParams.get('ville') || searchParams.get('city') || 'TOUS'
  );

  const [quartierFilter, setQuartierFilter] = useState<string>(
    searchParams.get('quartier') || ''
  );

  const [budgetMin, setBudgetMin] = useState<number | undefined>(() => {
    const val = searchParams.get('budgetMin');
    return val ? Number(val) : undefined;
  });

  const [budgetMax, setBudgetMax] = useState<number | undefined>(() => {
    const val = searchParams.get('budgetMax');
    return val ? Number(val) : undefined;
  });

  const [superficieMin, setSuperficieMin] = useState<number | undefined>(() => {
    const val = searchParams.get('superficieMin');
    return val ? Number(val) : undefined;
  });

  const [nombrePieces, setNombrePieces] = useState<number | undefined>(() => {
    const val = searchParams.get('nombrePieces');
    return val ? Number(val) : undefined;
  });

  const [meubleFilter, setMeubleFilter] = useState<'TOUS' | 'MEUBLE' | 'NON_MEUBLE'>(() => {
    const m = searchParams.get('meuble');
    if (m === 'true') return 'MEUBLE';
    if (m === 'false') return 'NON_MEUBLE';
    return 'TOUS';
  });

  const [animauxAcceptes, setAnimauxAcceptes] = useState<boolean>(() => {
    return searchParams.get('animauxAcceptes') === 'true';
  });

  const [selectedProximites, setSelectedProximites] = useState<string[]>(() => {
    const fromUrl = searchParams.getAll('proximite');
    if (fromUrl.length > 0) return fromUrl;
    const single = searchParams.get('proximite');
    return single && single !== 'TOUS' ? [single] : [];
  });

  const [onlyVerified, setOnlyVerified] = useState<boolean>(() => {
    return (
      searchParams.get('statutVerification') === 'VERIFIEE' ||
      searchParams.get('onlyVerified') === 'true'
    );
  });

  const [page, setPage] = useState<number>(() => {
    const p = searchParams.get('page');
    return p ? Number(p) : 0;
  });

  // Slider budget limits depend on modeTransaction
  const sliderConfig = useMemo(() => {
    if (modeTransaction === 'VENTE') {
      return { min: 0, max: 200_000_000, step: 5_000_000 };
    }
    return { min: 0, max: 2_000_000, step: 25_000 };
  }, [modeTransaction]);

  // Synchronize state when URL search parameters change externally (e.g. Back/Forward button)
  useEffect(() => {
    const mode = searchParams.get('modeTransaction') || 'TOUS';
    const types = searchParams.getAll('type');
    const singleType = searchParams.get('type');
    const finalTypes = types.length > 0 ? types : (singleType && singleType !== 'TOUS' ? [singleType] : []);
    const city = searchParams.get('ville') || searchParams.get('city') || 'TOUS';
    const quartier = searchParams.get('quartier') || '';
    const bMin = searchParams.get('budgetMin') ? Number(searchParams.get('budgetMin')) : undefined;
    const bMax = searchParams.get('budgetMax') ? Number(searchParams.get('budgetMax')) : undefined;
    const sMin = searchParams.get('superficieMin') ? Number(searchParams.get('superficieMin')) : undefined;
    const nPieces = searchParams.get('nombrePieces') ? Number(searchParams.get('nombrePieces')) : undefined;
    const m = searchParams.get('meuble');
    const mFilter = m === 'true' ? 'MEUBLE' : m === 'false' ? 'NON_MEUBLE' : 'TOUS';
    const animaux = searchParams.get('animauxAcceptes') === 'true';
    const proxs = searchParams.getAll('proximite');
    const singleProx = searchParams.get('proximite');
    const finalProxs = proxs.length > 0 ? proxs : (singleProx && singleProx !== 'TOUS' ? [singleProx] : []);
    const verified = searchParams.get('statutVerification') === 'VERIFIEE' || searchParams.get('onlyVerified') === 'true';
    const p = searchParams.get('page') ? Number(searchParams.get('page')) : 0;

    setModeTransaction((prev) => prev !== mode ? mode : prev);
    setSelectedTypes((prev) => JSON.stringify(prev) !== JSON.stringify(finalTypes) ? finalTypes : prev);
    setCityFilter((prev) => prev !== city ? city : prev);
    setQuartierFilter((prev) => prev !== quartier ? quartier : prev);
    setBudgetMin((prev) => prev !== bMin ? bMin : prev);
    setBudgetMax((prev) => prev !== bMax ? bMax : prev);
    setSuperficieMin((prev) => prev !== sMin ? sMin : prev);
    setNombrePieces((prev) => prev !== nPieces ? nPieces : prev);
    setMeubleFilter((prev) => prev !== mFilter ? mFilter : prev);
    setAnimauxAcceptes((prev) => prev !== animaux ? animaux : prev);
    setSelectedProximites((prev) => JSON.stringify(prev) !== JSON.stringify(finalProxs) ? finalProxs : prev);
    setOnlyVerified((prev) => prev !== verified ? verified : prev);
    setPage((prev) => prev !== p ? p : prev);
  }, [searchParams]);

  // Synchronize filter states to URL search parameters
  useEffect(() => {
    const newParams = new URLSearchParams();
    if (page > 0) newParams.set('page', page.toString());
    if (modeTransaction !== 'TOUS') newParams.set('modeTransaction', modeTransaction);
    selectedTypes.forEach((t) => newParams.append('type', t));
    if (cityFilter !== 'TOUS') newParams.set('ville', cityFilter);
    if (quartierFilter.trim()) newParams.set('quartier', quartierFilter.trim());
    if (budgetMin !== undefined && budgetMin > 0) newParams.set('budgetMin', budgetMin.toString());
    if (budgetMax !== undefined && budgetMax > 0) newParams.set('budgetMax', budgetMax.toString());
    if (superficieMin !== undefined && superficieMin > 0) newParams.set('superficieMin', superficieMin.toString());
    if (nombrePieces !== undefined && nombrePieces > 0) newParams.set('nombrePieces', nombrePieces.toString());
    if (meubleFilter === 'MEUBLE') newParams.set('meuble', 'true');
    if (meubleFilter === 'NON_MEUBLE') newParams.set('meuble', 'false');
    if (animauxAcceptes) newParams.set('animauxAcceptes', 'true');
    selectedProximites.forEach((p) => newParams.append('proximite', p));
    if (onlyVerified) newParams.set('statutVerification', 'VERIFIEE');

    if (newParams.toString() !== searchParams.toString()) {
      setSearchParams(newParams);
    }
  }, [
    page,
    modeTransaction,
    selectedTypes,
    cityFilter,
    quartierFilter,
    budgetMin,
    budgetMax,
    superficieMin,
    nombrePieces,
    meubleFilter,
    animauxAcceptes,
    selectedProximites,
    onlyVerified,
    searchParams,
    setSearchParams,
  ]);

  // Query fetching listings with all filters
  const { data, isLoading, isError, refetch } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: [
      'annonces',
      modeTransaction,
      selectedTypes,
      cityFilter,
      quartierFilter,
      budgetMin,
      budgetMax,
      superficieMin,
      nombrePieces,
      meubleFilter,
      animauxAcceptes,
      selectedProximites,
      onlyVerified,
      page,
    ],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '12');
      params.append('tri', 'dateCreation');
      params.append('direction', 'DESC');

      if (modeTransaction !== 'TOUS') params.append('modeTransaction', modeTransaction);
      selectedTypes.forEach((t) => params.append('type', t));
      if (cityFilter !== 'TOUS') params.append('ville', cityFilter);
      if (quartierFilter.trim()) params.append('quartier', quartierFilter.trim());
      if (budgetMin !== undefined && budgetMin > 0) params.append('budgetMin', budgetMin.toString());
      if (budgetMax !== undefined && budgetMax > 0) params.append('budgetMax', budgetMax.toString());
      if (superficieMin !== undefined && superficieMin > 0) params.append('superficieMin', superficieMin.toString());
      if (nombrePieces !== undefined && nombrePieces > 0) params.append('nombrePieces', nombrePieces.toString());
      if (meubleFilter === 'MEUBLE') params.append('meuble', 'true');
      if (meubleFilter === 'NON_MEUBLE') params.append('meuble', 'false');
      if (animauxAcceptes) params.append('animauxAcceptes', 'true');
      selectedProximites.forEach((p) => params.append('proximite', p));
      if (onlyVerified) params.append('statutVerification', 'VERIFIEE');

      return apiFetch<PageResponse<AnnonceDTO>>(`/api/v1/annonces?${params.toString()}`);
    },
  });

  const properties = data?.content || [];

  const handleResetFilters = () => {
    setModeTransaction('TOUS');
    setSelectedTypes([]);
    setCityFilter('TOUS');
    setQuartierFilter('');
    setBudgetMin(undefined);
    setBudgetMax(undefined);
    setSuperficieMin(undefined);
    setNombrePieces(undefined);
    setMeubleFilter('TOUS');
    setAnimauxAcceptes(false);
    setSelectedProximites([]);
    setOnlyVerified(false);
    setPage(0);
  };

  const toggleType = (t: string) => {
    setSelectedTypes((prev) =>
      prev.includes(t) ? prev.filter((item) => item !== t) : [...prev, t]
    );
    setPage(0);
  };

  const toggleProximite = (p: string) => {
    setSelectedProximites((prev) =>
      prev.includes(p) ? prev.filter((item) => item !== p) : [...prev, p]
    );
    setPage(0);
  };

  // Active filter badges list
  const activeChips = useMemo(() => {
    const chips: Array<{ id: string; label: string; onRemove: () => void }> = [];

    if (modeTransaction !== 'TOUS') {
      chips.push({
        id: 'mode',
        label: modeTransaction === 'VENTE' ? 'Vente' : 'Location',
        onRemove: () => setModeTransaction('TOUS'),
      });
    }

    selectedTypes.forEach((t) => {
      const match = references?.typesBien?.find((tb) => tb.valeur === t);
      chips.push({
        id: `type-${t}`,
        label: match?.libelle || t,
        onRemove: () => toggleType(t),
      });
    });

    if (cityFilter !== 'TOUS') {
      chips.push({
        id: 'city',
        label: `Ville : ${cityFilter}`,
        onRemove: () => setCityFilter('TOUS'),
      });
    }

    if (quartierFilter.trim()) {
      chips.push({
        id: 'quartier',
        label: `Quartier : ${quartierFilter}`,
        onRemove: () => setQuartierFilter(''),
      });
    }

    if (budgetMin !== undefined && budgetMin > 0) {
      chips.push({
        id: 'budgetMin',
        label: `Min : ${budgetMin.toLocaleString('fr-FR')} FCFA`,
        onRemove: () => setBudgetMin(undefined),
      });
    }

    if (budgetMax !== undefined && budgetMax > 0) {
      chips.push({
        id: 'budgetMax',
        label: `Max : ${budgetMax.toLocaleString('fr-FR')} FCFA`,
        onRemove: () => setBudgetMax(undefined),
      });
    }

    if (superficieMin !== undefined && superficieMin > 0) {
      chips.push({
        id: 'superficieMin',
        label: `≥ ${superficieMin} m²`,
        onRemove: () => setSuperficieMin(undefined),
      });
    }

    if (nombrePieces !== undefined && nombrePieces > 0) {
      chips.push({
        id: 'nombrePieces',
        label: `${nombrePieces}+ pièces`,
        onRemove: () => setNombrePieces(undefined),
      });
    }

    if (meubleFilter === 'MEUBLE') {
      chips.push({
        id: 'meuble',
        label: 'Meublé',
        onRemove: () => setMeubleFilter('TOUS'),
      });
    } else if (meubleFilter === 'NON_MEUBLE') {
      chips.push({
        id: 'meuble',
        label: 'Non meublé',
        onRemove: () => setMeubleFilter('TOUS'),
      });
    }

    if (animauxAcceptes) {
      chips.push({
        id: 'animaux',
        label: 'Animaux acceptés',
        onRemove: () => setAnimauxAcceptes(false),
      });
    }

    selectedProximites.forEach((p) => {
      const match = references?.proximites?.find((pr) => pr.valeur === p);
      chips.push({
        id: `prox-${p}`,
        label: match?.libelle || p,
        onRemove: () => toggleProximite(p),
      });
    });

    if (onlyVerified) {
      chips.push({
        id: 'verified',
        label: 'Vérifiées uniquement',
        onRemove: () => setOnlyVerified(false),
      });
    }

    return chips;
  }, [
    modeTransaction,
    selectedTypes,
    cityFilter,
    quartierFilter,
    budgetMin,
    budgetMax,
    superficieMin,
    nombrePieces,
    meubleFilter,
    animauxAcceptes,
    selectedProximites,
    onlyVerified,
    references,
  ]);

  return (
    <div className="h-[calc(100vh-80px)] flex flex-col md:flex-row overflow-hidden bg-sand-100">
      
      {/* LEFT COLUMN: LISTINGS & FILTER BAR */}
      <div className="w-full md:w-7/12 lg:w-1/2 flex flex-col h-full border-r border-argile-200">
        
        {/* Top Control & Quick Filters Bar */}
        <div className="p-4 bg-white border-b border-argile-200 space-y-3 shrink-0 min-w-0 max-w-full overflow-hidden">
          <div className="flex items-center justify-between gap-2 min-w-0 max-w-full">
            <div className="min-w-0">
              <h1 className="font-display font-extrabold text-base sm:text-lg text-charcoal-900 truncate">
                Logements Disponibles ({data?.totalElements ?? 0})
              </h1>
              <p className="text-[11px] sm:text-xs text-charcoal-500 truncate">
                Dakar, Ziguinchor, Saly, Thiès &bull; Annonces contrôlées par E-Courtier
              </p>
            </div>
            <Button
              variant="outline"
              size="sm"
              leftIcon={<SlidersHorizontal className="w-4 h-4 text-terracotta-500" />}
              onClick={() => setIsFilterDrawerOpen(true)}
              className="shrink-0"
            >
              <span className="hidden xs:inline">Filtres Avancés</span>
              <span className="xs:hidden">Filtres</span>
            </Button>
          </div>

          {/* Quick Mode & City Filters */}
          <div className="flex items-center gap-2 overflow-x-auto min-w-0 max-w-full no-scrollbar text-xs font-semibold">
            {/* Mode selection buttons */}
            {(['TOUS', 'LOCATION', 'VENTE'] as const).map((m) => (
              <button
                key={m}
                onClick={() => {
                  setModeTransaction(m);
                  setPage(0);
                }}
                className={`px-3 py-1.5 rounded-xl border transition-all whitespace-nowrap ${
                  modeTransaction === m
                    ? 'bg-terracotta-500 text-white border-terracotta-500 shadow-sm'
                    : 'bg-sand-100 text-charcoal-700 border-argile-300 hover:bg-sand-200'
                }`}
              >
                {m === 'TOUS' ? 'Tous les modes' : m === 'LOCATION' ? 'Location' : 'Vente'}
              </button>
            ))}

            <div className="h-4 w-px bg-sand-300 shrink-0" />

            {/* Quick Cities */}
            {['TOUS', 'Dakar', 'Ziguinchor', 'Saly', 'Thiès'].map((c) => (
              <button
                key={c}
                onClick={() => {
                  setCityFilter(c);
                  setPage(0);
                }}
                className={`px-3 py-1.5 rounded-xl border transition-all whitespace-nowrap ${
                  cityFilter === c
                    ? 'bg-sahel-800 text-white border-sahel-800'
                    : 'bg-sand-100 text-charcoal-700 border-argile-300 hover:bg-sand-200'
                }`}
              >
                {c === 'TOUS' ? 'Toutes les villes' : c}
              </button>
            ))}

            <button
              onClick={() => setOnlyVerified(!onlyVerified)}
              className={`px-3 py-1.5 rounded-xl border transition-all whitespace-nowrap flex items-center gap-1 ${
                onlyVerified
                  ? 'bg-emerald-600 text-white border-emerald-600'
                  : 'bg-sand-100 text-charcoal-700 border-argile-300 hover:bg-sand-200'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Vérifiées uniquement</span>
            </button>
          </div>

          {/* Active Filter Pastilles (Chips) */}
          {activeChips.length > 0 && (
            <div className="flex flex-wrap items-center gap-1.5 pt-1 border-t border-sand-200">
              <span className="text-[11px] font-semibold text-charcoal-500 mr-1">Filtres actifs :</span>
              {activeChips.map((chip) => (
                <span
                  key={chip.id}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-terracotta-100 text-terracotta-900 border border-terracotta-200"
                >
                  <span>{chip.label}</span>
                  <button
                    type="button"
                    onClick={chip.onRemove}
                    data-testid={`remove-chip-${chip.id}`}
                    aria-label={`Supprimer le filtre ${chip.label}`}
                    className="p-0.5 hover:bg-terracotta-200 rounded-full transition-colors text-terracotta-700"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </span>
              ))}
              <button
                type="button"
                onClick={handleResetFilters}
                className="text-[11px] font-semibold text-charcoal-500 hover:text-terracotta-600 underline ml-1"
              >
                Effacer tout
              </button>
            </div>
          )}
        </div>

        {/* LISTINGS FEED SCROLLABLE AREA */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          
          {isLoading ? (
            <div className="space-y-4">
              {[1, 2, 3].map((i) => (
                <div key={i} className="flex flex-col sm:flex-row gap-4 bg-white p-4 rounded-2xl border border-argile-200">
                  <Skeleton variant="card" className="w-full sm:w-48 h-36" />
                  <div className="flex-1 space-y-3">
                    <Skeleton variant="text" className="w-3/4" />
                    <Skeleton variant="text" className="w-1/2" />
                    <Skeleton variant="text" className="w-1/4" />
                  </div>
                </div>
              ))}
            </div>
          ) : isError ? (
            <div className="text-center p-8 bg-white rounded-2xl border border-argile-200 space-y-3">
              <p className="text-sm font-semibold text-charcoal-800">
                Impossible de charger les annonces pour le moment.
              </p>
              <Button variant="outline" size="sm" onClick={() => refetch()} leftIcon={<RefreshCw className="w-4 h-4" />}>
                Réessayer
              </Button>
            </div>
          ) : properties.length === 0 ? (
            <div className="text-center p-12 bg-white rounded-2xl border border-argile-200 space-y-4">
              <div className="w-12 h-12 rounded-full bg-sand-200 flex items-center justify-center mx-auto text-charcoal-500">
                <Search className="w-6 h-6" />
              </div>
              <div>
                <h3 className="font-display font-bold text-base text-charcoal-900">Aucun résultat trouvé</h3>
                <p className="text-xs text-charcoal-500 max-w-sm mx-auto mt-1">
                  Aucune annonce ne correspond exactement à vos critères. Essayez d'élargir votre recherche ou de réinitialiser les filtres.
                </p>
              </div>
              <Button variant="outline" size="sm" onClick={handleResetFilters}>
                Réinitialiser les filtres
              </Button>
            </div>
          ) : (
            <>
              <motion.div
                className="space-y-4"
                initial={shouldReduceMotion ? false : { opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.3 }}
              >
                {properties.map((prop) => {
                  const isSelected = selectedPropertyId === prop.id;
                  const photoUrl = prop.photos?.[0] ? getPhotoUrl(prop.photos[0].url) : '/placeholder.jpg';

                  return (
                    <motion.div
                      key={prop.id}
                      onClick={() => setSelectedPropertyId(prop.id)}
                      className={`flex flex-col sm:flex-row gap-4 bg-white p-3.5 sm:p-4 rounded-2xl border transition-all cursor-pointer ${
                        isSelected
                          ? 'border-terracotta-500 ring-2 ring-terracotta-500/20 shadow-warm-hover'
                          : 'border-argile-200 hover:border-argile-300 hover:shadow-warm-card'
                      }`}
                      whileHover={shouldReduceMotion ? undefined : { y: -2 }}
                    >
                      {/* Photo Thumbnail */}
                      <div className="relative w-full sm:w-48 h-44 sm:h-36 shrink-0 rounded-xl overflow-hidden bg-sand-200">
                        <img
                          src={photoUrl}
                          alt={prop.titre}
                          loading="lazy"
                          className="w-full h-full object-cover"
                        />
                        <div className="absolute top-2 left-2 flex flex-col gap-1">
                          <span className={`text-[10px] font-extrabold uppercase px-2 py-0.5 rounded shadow ${
                            prop.modeTransaction === 'VENTE' ? 'bg-amber-600 text-white' : 'bg-sahel-800 text-white'
                          }`}>
                            {prop.modeTransaction === 'VENTE' ? 'Vente' : 'Location'}
                          </span>
                          {prop.statutVerification === 'VERIFIEE' && (
                            <span className="bg-emerald-600 text-white text-[10px] font-bold px-1.5 py-0.5 rounded shadow flex items-center gap-1">
                              <ShieldCheck className="w-3 h-3" /> Vérifié
                            </span>
                          )}
                          {(prop.auteurRole === 'AGENT' || prop.auteur?.role === 'AGENT') && (
                            <span className="bg-amber-700 text-white text-[10px] font-bold px-1.5 py-0.5 rounded shadow">
                              Agent immobilier
                            </span>
                          )}
                        </div>
                        <div className="absolute top-2 right-2">
                          <FavoriteButton
                            annonceId={prop.id}
                            estFavori={!!prop.estFavori}
                            size="sm"
                          />
                        </div>
                      </div>

                      {/* Property Details */}
                      <div className="flex-1 flex flex-col justify-between space-y-2">
                        <div>
                          <div className="flex items-center justify-between gap-2">
                            <span className="font-display font-extrabold text-base text-terracotta-600">
                              {prop.prix.toLocaleString('fr-FR')} FCFA
                              {prop.modeTransaction === 'LOCATION' && <span className="text-xs text-charcoal-500 font-normal"> /mois</span>}
                            </span>
                            <span className="text-xs text-charcoal-500 flex items-center gap-1 truncate max-w-[140px]">
                              <MapPin className="w-3.5 h-3.5 shrink-0 text-terracotta-500" />
                              <span className="truncate">{prop.ville} ({prop.quartier})</span>
                            </span>
                          </div>

                          <h3 className="font-display font-bold text-sm text-charcoal-900 line-clamp-2 mt-1">
                            {prop.titre}
                          </h3>
                        </div>

                        {/* Vector Tags System */}
                        <div className="flex flex-wrap items-center gap-1.5 text-xs text-charcoal-600 bg-sand-100/90 p-2 rounded-lg">
                          <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">{prop.superficie} m²</span>
                          <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">{prop.nombrePieces} pièces</span>
                          {prop.meuble && <span className="bg-terracotta-100 text-terracotta-800 text-[10px] font-bold px-2 py-0.5 rounded">Meublé</span>}
                          {prop.animauxAcceptes && <span className="bg-emerald-100 text-emerald-800 text-[10px] font-bold px-2 py-0.5 rounded">Animaux OK</span>}
                          <span className="ml-auto font-mono text-[10px] uppercase bg-sahel-800 text-white px-2 py-0.5 rounded font-bold">{prop.type}</span>
                        </div>

                        <div className="flex items-center justify-between pt-1">
                          <div className="flex items-center gap-1.5 text-[11px] text-charcoal-600 flex-wrap">
                            <span className="font-semibold text-charcoal-800">{prop.auteur?.prenom} {prop.auteur?.nom}</span>
                            {(prop.auteurRole === 'AGENT' || prop.auteur?.role === 'AGENT') && (
                              <span className="bg-amber-100 text-amber-900 border border-amber-300 text-[10px] font-bold px-1.5 py-0.5 rounded">
                                Publié par un agent immobilier
                              </span>
                            )}
                          </div>
                          <Link to={`/annonce/${prop.id}`}>
                            <Button variant="primary" size="sm" className="text-xs py-1.5 px-3">
                              Voir la fiche
                            </Button>
                          </Link>
                        </div>
                      </div>
                    </motion.div>
                  );
                })}
              </motion.div>

              {/* SPRING PAGEABLE PAGINATION */}
              {data && data.totalPages > 1 && (
                <div className="flex items-center justify-between pt-4 pb-2 text-xs font-semibold text-charcoal-700 border-t border-argile-200">
                  <span>Page {data.number + 1} sur {data.totalPages}</span>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={data.first}
                      onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                      leftIcon={<ChevronLeft className="w-4 h-4" />}
                    >
                      Précédent
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={data.last}
                      onClick={() => setPage((prev) => prev + 1)}
                      rightIcon={<ChevronRight className="w-4 h-4" />}
                    >
                      Suivant
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}

        </div>
      </div>

      {/* RIGHT COLUMN: SYNCHRONIZED LEAFLET MAP */}
      <div className="w-full md:w-5/12 lg:w-1/2 h-64 md:h-full relative shrink-0">
        <LeafletMap
          properties={properties}
          selectedPropertyId={selectedPropertyId}
          onSelectProperty={(id) => setSelectedPropertyId(id)}
        />
      </div>

      {/* ADVANCED FILTERS DRAWER */}
      <Drawer
        isOpen={isFilterDrawerOpen}
        onClose={() => setIsFilterDrawerOpen(false)}
        title="Filtres de Recherche Approfondis"
      >
        <div className="space-y-6">
          
          {/* Mode Transaction */}
          <div>
            <label className="block text-xs font-bold text-sahel-800 mb-1.5">
              Mode de Transaction
            </label>
            <div className="grid grid-cols-3 gap-2">
              {(['TOUS', 'LOCATION', 'VENTE'] as const).map((m) => (
                <button
                  key={m}
                  type="button"
                  onClick={() => {
                    setModeTransaction(m);
                    setPage(0);
                  }}
                  className={`py-2 px-3 rounded-xl border text-xs font-semibold transition-all ${
                    modeTransaction === m
                      ? 'bg-terracotta-500 text-white border-terracotta-500'
                      : 'bg-sand-50 border-sand-300 text-charcoal-700 hover:bg-sand-100'
                  }`}
                >
                  {m === 'TOUS' ? 'Tous' : m === 'LOCATION' ? 'Location' : 'Vente'}
                </button>
              ))}
            </div>
          </div>

          {/* Type de bien (Choix multiple - F7) */}
          <div>
            <label className="block text-xs font-bold text-sahel-800 mb-1.5">
              Types de bien (choix multiple)
            </label>
            <div className="grid grid-cols-3 gap-2">
              {(references?.typesBien || [
                { valeur: 'APPARTEMENT', libelle: 'Appartement' },
                { valeur: 'VILLA', libelle: 'Villa' },
                { valeur: 'STUDIO', libelle: 'Studio' },
              ]).map((t) => {
                const isChecked = selectedTypes.includes(t.valeur);
                return (
                  <button
                    key={t.valeur}
                    type="button"
                    onClick={() => toggleType(t.valeur)}
                    className={`py-2 px-2.5 rounded-xl border text-xs font-medium flex items-center justify-between transition-all ${
                      isChecked
                        ? 'bg-terracotta-50 border-terracotta-500 text-terracotta-800 font-semibold ring-1 ring-terracotta-400'
                        : 'bg-white border-sand-300 text-charcoal-700 hover:bg-sand-50'
                    }`}
                  >
                    <span>{t.libelle}</span>
                    {isChecked && <Check className="w-3.5 h-3.5 text-terracotta-600" />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Ville & Quartier (Autocomplétion - F8) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1.5">Ville</label>
              <select
                value={cityFilter}
                onChange={(e) => {
                  setCityFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-xs focus:ring-2 focus:ring-terracotta-500 outline-none"
              >
                <option value="TOUS">Toutes les villes</option>
                <option value="Dakar">Dakar</option>
                <option value="Ziguinchor">Ziguinchor</option>
                <option value="Saly">Saly</option>
                <option value="Thiès">Thiès</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1.5">Quartier</label>
              <QuartierAutocomplete
                value={quartierFilter}
                onChange={(val) => {
                  setQuartierFilter(val);
                  setPage(0);
                }}
              />
            </div>
          </div>

          {/* Budget Min et Max (Curseur dépendant du mode - F9) */}
          <div className="space-y-3 pt-2 border-t border-sand-200">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-sahel-800">
                Budget (FCFA) {modeTransaction === 'VENTE' ? '— Vente' : '— Location'}
              </label>
              {(budgetMin !== undefined || budgetMax !== undefined) && (
                <button
                  type="button"
                  onClick={() => {
                    setBudgetMin(undefined);
                    setBudgetMax(undefined);
                    setPage(0);
                  }}
                  className="text-[11px] text-terracotta-600 hover:underline"
                >
                  Réinitialiser budget
                </button>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-[11px] text-charcoal-500 mb-1">Budget Min (FCFA)</label>
                <input
                  type="number"
                  placeholder="Min"
                  value={budgetMin !== undefined ? budgetMin : ''}
                  onChange={(e) => {
                    const val = e.target.value ? Number(e.target.value) : undefined;
                    setBudgetMin(val);
                    setPage(0);
                  }}
                  className="w-full px-3 py-1.5 rounded-lg border border-sand-300 text-xs focus:ring-2 focus:ring-terracotta-500 outline-none"
                />
              </div>
              <div>
                <label className="block text-[11px] text-charcoal-500 mb-1">Budget Max (FCFA)</label>
                <input
                  type="number"
                  placeholder="Max (sans plafond)"
                  value={budgetMax !== undefined ? budgetMax : ''}
                  onChange={(e) => {
                    const val = e.target.value ? Number(e.target.value) : undefined;
                    setBudgetMax(val);
                    setPage(0);
                  }}
                  className="w-full px-3 py-1.5 rounded-lg border border-sand-300 text-xs focus:ring-2 focus:ring-terracotta-500 outline-none"
                />
              </div>
            </div>

            <Slider
              label={`Plafond Maximal : ${budgetMax ? `${budgetMax.toLocaleString('fr-FR')} FCFA` : 'Aucun plafond'}`}
              min={sliderConfig.min}
              max={sliderConfig.max}
              step={sliderConfig.step}
              value={budgetMax || sliderConfig.max}
              onChange={(val) => {
                setBudgetMax(val);
                setPage(0);
              }}
              formatValue={(val) => `${(val / 1000).toLocaleString('fr-FR')}k F`}
            />
          </div>

          {/* Superficie & Nombre de pièces (F10, F11) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-sand-200">
            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1.5">Superficie minimale (m²)</label>
              <input
                type="number"
                placeholder="Ex: 80"
                value={superficieMin !== undefined ? superficieMin : ''}
                onChange={(e) => {
                  const val = e.target.value ? Number(e.target.value) : undefined;
                  setSuperficieMin(val);
                  setPage(0);
                }}
                className="w-full px-3 py-2 rounded-xl border border-sand-300 text-xs focus:ring-2 focus:ring-terracotta-500 outline-none"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1.5">Nombre de pièces</label>
              <div className="grid grid-cols-5 gap-1">
                {([1, 2, 3, 4, 5] as const).map((np) => (
                  <button
                    key={np}
                    type="button"
                    onClick={() => {
                      setNombrePieces(nombrePieces === np ? undefined : np);
                      setPage(0);
                    }}
                    className={`py-2 rounded-xl border text-xs font-semibold transition-all ${
                      nombrePieces === np
                        ? 'bg-terracotta-500 text-white border-terracotta-500'
                        : 'bg-sand-50 border-sand-300 text-charcoal-700 hover:bg-sand-100'
                    }`}
                  >
                    {np === 5 ? '5+' : np}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Meublé (Tous / Meublé / Non meublé - F13) */}
          <div className="pt-2 border-t border-sand-200">
            <label className="block text-xs font-bold text-sahel-800 mb-1.5">Ameublement</label>
            <div className="grid grid-cols-3 gap-2">
              {(
                [
                  { val: 'TOUS', label: 'Tous' },
                  { val: 'MEUBLE', label: 'Meublé' },
                  { val: 'NON_MEUBLE', label: 'Non meublé' },
                ] as const
              ).map((opt) => (
                <button
                  key={opt.val}
                  type="button"
                  onClick={() => {
                    setMeubleFilter(opt.val);
                    setPage(0);
                  }}
                  className={`py-2 px-2 rounded-xl border text-xs font-semibold transition-all ${
                    meubleFilter === opt.val
                      ? 'bg-sahel-800 text-white border-sahel-800'
                      : 'bg-sand-50 border-sand-300 text-charcoal-700 hover:bg-sand-100'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Proximités (Cases à cocher depuis /references - F12) */}
          <div className="space-y-2 pt-2 border-t border-sand-200">
            <label className="block text-xs font-bold text-sahel-800 mb-1">
              Proximités & Commodités
            </label>
            <div className="grid grid-cols-2 gap-2">
              {(references?.proximites || [
                { valeur: 'ECOLE', libelle: 'École' },
                { valeur: 'MARCHE', libelle: 'Marché' },
                { valeur: 'HOPITAL', libelle: 'Hôpital / Santé' },
                { valeur: 'TRANSPORT', libelle: 'Transports (BRT/Bus)' },
                { valeur: 'PLAGE', libelle: 'Plage / Mer' },
                { valeur: 'COMMERCE', libelle: 'Commerces' },
                { valeur: 'UNIVERSITE', libelle: 'Université' },
              ]).map((p) => {
                const meta = getProximiteMeta(p.valeur);
                const Icon = meta.icon;
                const isChecked = selectedProximites.includes(p.valeur);
                return (
                  <button
                    key={p.valeur}
                    type="button"
                    onClick={() => toggleProximite(p.valeur)}
                    className={`flex items-center gap-2 px-2.5 py-2 rounded-xl border text-xs text-left transition-all ${
                      isChecked
                        ? 'bg-clay-50 border-sahel-600 text-sahel-900 font-semibold ring-1 ring-sahel-500'
                        : 'bg-white border-sand-300 text-charcoal-700 hover:bg-sand-50'
                    }`}
                  >
                    <Icon className={`w-3.5 h-3.5 shrink-0 ${isChecked ? 'text-sahel-700' : 'text-charcoal-500'}`} />
                    <span className="truncate">{p.libelle}</span>
                    {isChecked && <Check className="w-3.5 h-3.5 text-sahel-700 ml-auto" />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Garanties & Options */}
          <div className="space-y-3 pt-2 border-t border-sand-200">
            <h4 className="text-xs font-bold text-sahel-800 uppercase tracking-wider">
              Options supplémentaires
            </h4>

            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={animauxAcceptes}
                onChange={(e) => {
                  setAnimauxAcceptes(e.target.checked);
                  setPage(0);
                }}
                className="w-4 h-4 rounded text-terracotta-500 focus:ring-terracotta-500"
              />
              <span className="text-xs font-medium text-charcoal-800">Animaux acceptés</span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={onlyVerified}
                onChange={(e) => {
                  setOnlyVerified(e.target.checked);
                  setPage(0);
                }}
                className="w-4 h-4 rounded text-terracotta-500 focus:ring-terracotta-500"
              />
              <span className="text-xs font-medium text-charcoal-800">Uniquement annonces vérifiées</span>
            </label>
          </div>

          <div className="pt-4 flex items-center gap-3 border-t border-sand-200">
            <Button variant="outline" size="md" className="flex-1" onClick={handleResetFilters}>
              Réinitialiser
            </Button>
            <Button variant="primary" size="md" className="flex-1" onClick={() => setIsFilterDrawerOpen(false)}>
              Voir les résultats ({data?.totalElements ?? 0})
            </Button>
          </div>

        </div>
      </Drawer>

    </div>
  );
};

export default SearchResultsPage;
