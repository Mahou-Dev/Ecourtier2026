import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
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
  List,
  Map as MapIcon,
  ArrowUpDown,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { motion, useReducedMotion } from 'framer-motion';
import { apiFetch } from '../services/api';
import { AnnonceDTO, PageResponse } from '../types';
import { Button } from '../components/ui/Button';
import { PropertyVisual } from '../components/property/PropertyVisual';
import { mapPropertyCategory, selectFallbackPhoto } from '../lib/propertyMedia';

const LazyLeafletMap = React.lazy(() => import('../components/map/LeafletMap').then((m) => ({ default: m.LeafletMap })));
import { Slider } from '../components/ui/Slider';
import { Drawer } from '../components/ui/Drawer';
import { Skeleton } from '../components/ui/Skeleton';
import { FavoriteButton } from '../components/ui/FavoriteButton';
import { useReferences } from '../hooks/useReferences';
import { QuartierAutocomplete } from '../components/ui/QuartierAutocomplete';
import { getProximiteMeta } from '../lib/referenceMeta';

const SEARCH_SORT_OPTIONS = [
  { value: 'dateCreation,desc', label: 'Plus récents' },
  { value: 'prix,asc', label: 'Prix croissant' },
  { value: 'prix,desc', label: 'Prix décroissant' },
  { value: 'superficie,desc', label: 'Plus grande superficie' },
] as const;

type SortValue = (typeof SEARCH_SORT_OPTIONS)[number]['value'];
const DEFAULT_SORT: SortValue = 'dateCreation,desc';
const RENT_BUDGET_THRESHOLD = 2_000_000;

function parsePositiveNumberParam(raw: string | null): number | undefined {
  if (!raw) return undefined;
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}

function parseSortParam(raw: string | null): SortValue {
  return SEARCH_SORT_OPTIONS.some((opt) => opt.value === raw) ? (raw as SortValue) : DEFAULT_SORT;
}

export const SearchResultsPage: React.FC = () => {
  const shouldReduceMotion = useReducedMotion();
  const [searchParams, setSearchParams] = useSearchParams();
  const { data: references } = useReferences();

  const [selectedPropertyId, setSelectedPropertyId] = useState<string | null>(null);
  const [isFilterDrawerOpen, setIsFilterDrawerOpen] = useState(false);
  const [mobileView, setMobileView] = useState<'list' | 'map'>('list');

  // Derive committed filters directly from URL searchParams (single source of truth, back/forward safe)
  const modeTransaction = searchParams.get('modeTransaction') || 'TOUS';
  const selectedTypes = useMemo(() => {
    const fromUrl = searchParams.getAll('type').filter((t) => t && t !== 'TOUS');
    return fromUrl;
  }, [searchParams]);
  const cityFilter = searchParams.get('ville') || searchParams.get('city') || 'TOUS';
  const quartierFilter = searchParams.get('quartier') || '';
  const qFilter = searchParams.get('q') || '';
  const budgetMin = parsePositiveNumberParam(searchParams.get('budgetMin'));
  const budgetMax = parsePositiveNumberParam(searchParams.get('budgetMax'));
  const superficieMin = parsePositiveNumberParam(searchParams.get('superficieMin'));
  const nombrePieces = parsePositiveNumberParam(searchParams.get('nombrePieces'));
  const meubleFilter: 'TOUS' | 'MEUBLE' | 'NON_MEUBLE' = useMemo(() => {
    const m = searchParams.get('meuble');
    if (m === 'true') return 'MEUBLE';
    if (m === 'false') return 'NON_MEUBLE';
    return 'TOUS';
  }, [searchParams]);
  const animauxAcceptes = searchParams.get('animauxAcceptes') === 'true';
  const selectedProximites = useMemo(
    () => searchParams.getAll('proximite').filter((p) => p && p !== 'TOUS'),
    [searchParams]
  );
  const onlyVerified =
    searchParams.get('statutVerification') === 'VERIFIEE' ||
    searchParams.get('onlyVerified') === 'true';
  const sort = parseSortParam(searchParams.get('sort'));
  const page = useMemo(() => {
    const raw = searchParams.get('page');
    const parsed = raw ? Number.parseInt(raw, 10) : 0;
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
  }, [searchParams]);

  // Controlled local drafts for text & numeric typing (synced cleanly without useEffect loops)
  const [qInput, setQInput] = useState(qFilter);
  const [prevUrlQ, setPrevUrlQ] = useState(qFilter);
  if (qFilter !== prevUrlQ) {
    setPrevUrlQ(qFilter);
    if (qInput.trim() !== qFilter) {
      setQInput(qFilter);
    }
  }

  const budgetMinUrlStr = budgetMin !== undefined ? String(budgetMin) : '';
  const [budgetMinDraft, setBudgetMinDraft] = useState(budgetMinUrlStr);
  const [prevBudgetMinStr, setPrevBudgetMinStr] = useState(budgetMinUrlStr);
  if (budgetMinUrlStr !== prevBudgetMinStr) {
    setPrevBudgetMinStr(budgetMinUrlStr);
    setBudgetMinDraft(budgetMinUrlStr);
  }

  const budgetMaxUrlStr = budgetMax !== undefined ? String(budgetMax) : '';
  const [budgetMaxDraft, setBudgetMaxDraft] = useState(budgetMaxUrlStr);
  const [prevBudgetMaxStr, setPrevBudgetMaxStr] = useState(budgetMaxUrlStr);
  if (budgetMaxUrlStr !== prevBudgetMaxStr) {
    setPrevBudgetMaxStr(budgetMaxUrlStr);
    setBudgetMaxDraft(budgetMaxUrlStr);
  }

  const superficieUrlStr = superficieMin !== undefined ? String(superficieMin) : '';
  const [superficieDraft, setSuperficieDraft] = useState(superficieUrlStr);
  const [prevSuperficieStr, setPrevSuperficieStr] = useState(superficieUrlStr);
  if (superficieUrlStr !== prevSuperficieStr) {
    setPrevSuperficieStr(superficieUrlStr);
    setSuperficieDraft(superficieUrlStr);
  }

  const qTimerRef = useRef<number | null>(null);
  const budgetMinTimerRef = useRef<number | null>(null);
  const budgetMaxTimerRef = useRef<number | null>(null);
  const superficieTimerRef = useRef<number | null>(null);

  const clearDebounceTimers = useCallback(() => {
    [qTimerRef, budgetMinTimerRef, budgetMaxTimerRef, superficieTimerRef].forEach((ref) => {
      if (ref.current !== null) {
        window.clearTimeout(ref.current);
        ref.current = null;
      }
    });
  }, []);

  useEffect(() => clearDebounceTimers, [clearDebounceTimers]);

  const commitParams = useCallback(
    (mutator: (next: URLSearchParams) => void, opts?: { resetPage?: boolean; replace?: boolean }) => {
      const resetPage = opts?.resetPage ?? true;
      const replace = opts?.replace ?? false;
      const next = new URLSearchParams(searchParams);
      mutator(next);
      if (resetPage) {
        next.delete('page');
      }
      if (next.toString() !== searchParams.toString()) {
        setSearchParams(next, { replace });
      }
    },
    [searchParams, setSearchParams]
  );

  // Slider budget limits depend on modeTransaction (and honor active >2M ceiling in TOUS)
  const sliderConfig = useMemo(() => {
    if (
      modeTransaction === 'VENTE' ||
      (modeTransaction === 'TOUS' &&
        ((budgetMax ?? 0) > RENT_BUDGET_THRESHOLD || (budgetMin ?? 0) > RENT_BUDGET_THRESHOLD))
    ) {
      return { min: 0, max: 200_000_000, step: 5_000_000 };
    }
    return { min: 0, max: RENT_BUDGET_THRESHOLD, step: 25_000 };
  }, [modeTransaction, budgetMin, budgetMax]);

  // Query fetching listings with all filters
  const { data, isLoading, isError, refetch } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: [
      'annonces',
      modeTransaction,
      selectedTypes,
      cityFilter,
      quartierFilter,
      qFilter,
      budgetMin,
      budgetMax,
      superficieMin,
      nombrePieces,
      meubleFilter,
      animauxAcceptes,
      selectedProximites,
      onlyVerified,
      sort,
      page,
    ],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '12');
      params.append('sort', sort);

      if (modeTransaction !== 'TOUS') params.append('modeTransaction', modeTransaction);
      selectedTypes.forEach((t) => params.append('type', t));
      if (cityFilter !== 'TOUS') params.append('ville', cityFilter);
      if (quartierFilter.trim()) params.append('quartier', quartierFilter.trim());
      if (qFilter.trim()) params.append('q', qFilter.trim());
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

  const handleResetFilters = useCallback(() => {
    clearDebounceTimers();
    setQInput('');
    setBudgetMinDraft('');
    setBudgetMaxDraft('');
    setSuperficieDraft('');
    const next = new URLSearchParams();
    if (sort !== DEFAULT_SORT) {
      next.set('sort', sort);
    }
    if (next.toString() !== searchParams.toString()) {
      setSearchParams(next);
    }
  }, [clearDebounceTimers, searchParams, setSearchParams, sort]);

  const handleModeChange = useCallback(
    (nextMode: 'TOUS' | 'LOCATION' | 'VENTE') => {
      commitParams((next) => {
        if (nextMode === 'TOUS') {
          next.delete('modeTransaction');
        } else {
          next.set('modeTransaction', nextMode);
        }

        // Prevent keeping an incompatible budget threshold when switching VENTE <-> LOCATION
        if (nextMode === 'LOCATION') {
          if (budgetMin !== undefined && budgetMin > RENT_BUDGET_THRESHOLD) {
            next.delete('budgetMin');
            if (budgetMinTimerRef.current !== null) window.clearTimeout(budgetMinTimerRef.current);
          }
          if (budgetMax !== undefined && budgetMax > RENT_BUDGET_THRESHOLD) {
            next.delete('budgetMax');
            if (budgetMaxTimerRef.current !== null) window.clearTimeout(budgetMaxTimerRef.current);
          }
        } else if (nextMode === 'VENTE') {
          if (budgetMin !== undefined && budgetMin <= RENT_BUDGET_THRESHOLD) {
            next.delete('budgetMin');
            if (budgetMinTimerRef.current !== null) window.clearTimeout(budgetMinTimerRef.current);
          }
          if (budgetMax !== undefined && budgetMax <= RENT_BUDGET_THRESHOLD) {
            next.delete('budgetMax');
            if (budgetMaxTimerRef.current !== null) window.clearTimeout(budgetMaxTimerRef.current);
          }
        }
      });
    },
    [budgetMax, budgetMin, commitParams]
  );

  const toggleType = useCallback(
    (t: string) => {
      commitParams((next) => {
        const current = next.getAll('type').filter((item) => item && item !== 'TOUS');
        const updated = current.includes(t)
          ? current.filter((item) => item !== t)
          : [...current, t];
        next.delete('type');
        updated.forEach((item) => next.append('type', item));
      });
    },
    [commitParams]
  );

  const toggleProximite = useCallback(
    (p: string) => {
      commitParams((next) => {
        const current = next.getAll('proximite').filter((item) => item && item !== 'TOUS');
        const updated = current.includes(p)
          ? current.filter((item) => item !== p)
          : [...current, p];
        next.delete('proximite');
        updated.forEach((item) => next.append('proximite', item));
      });
    },
    [commitParams]
  );

  const commitKeyword = useCallback(
    (rawVal: string, replace = false) => {
      const trimmed = rawVal.trim();
      commitParams(
        (next) => {
          if (trimmed) {
            next.set('q', trimmed);
          } else {
            next.delete('q');
          }
        },
        { resetPage: true, replace }
      );
    },
    [commitParams]
  );

  const handleKeywordChange = (val: string) => {
    setQInput(val);
    if (qTimerRef.current !== null) {
      window.clearTimeout(qTimerRef.current);
    }
    qTimerRef.current = window.setTimeout(() => {
      qTimerRef.current = null;
      commitKeyword(val, true);
    }, 350);
  };

  const handleKeywordSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (qTimerRef.current !== null) {
      window.clearTimeout(qTimerRef.current);
      qTimerRef.current = null;
    }
    commitKeyword(qInput, false);
  };

  const handleClearKeyword = useCallback(() => {
    if (qTimerRef.current !== null) {
      window.clearTimeout(qTimerRef.current);
      qTimerRef.current = null;
    }
    setQInput('');
    commitKeyword('', false);
  }, [commitKeyword]);

  const handleSortChange = (nextSort: string) => {
    const valid = parseSortParam(nextSort);
    commitParams((next) => {
      if (valid === DEFAULT_SORT) {
        next.delete('sort');
      } else {
        next.set('sort', valid);
      }
    });
  };

  const commitNumericParam = useCallback(
    (paramName: 'budgetMin' | 'budgetMax' | 'superficieMin', rawStr: string, replace = false) => {
      const parsed = rawStr.trim() ? Number(rawStr) : undefined;
      commitParams(
        (next) => {
          if (parsed !== undefined && Number.isFinite(parsed) && parsed > 0) {
            next.set(paramName, String(Math.round(parsed)));
          } else {
            next.delete(paramName);
          }
        },
        { resetPage: true, replace }
      );
    },
    [commitParams]
  );

  // Active filter badges list (all onRemove handlers reset page to 0 via commitParams/helpers)
  const activeChips = useMemo(() => {
    const chips: Array<{ id: string; label: string; onRemove: () => void }> = [];

    if (qFilter.trim()) {
      chips.push({
        id: 'q',
        label: `Mot-clé : ${qFilter.trim()}`,
        onRemove: () => {
          setQInput('');
          commitParams((next) => next.delete('q'));
        },
      });
    }

    if (modeTransaction !== 'TOUS') {
      chips.push({
        id: 'mode',
        label: modeTransaction === 'VENTE' ? 'Vente' : 'Location',
        onRemove: () => commitParams((next) => next.delete('modeTransaction')),
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
        onRemove: () =>
          commitParams((next) => {
            next.delete('ville');
            next.delete('city');
          }),
      });
    }

    if (quartierFilter.trim()) {
      chips.push({
        id: 'quartier',
        label: `Quartier : ${quartierFilter}`,
        onRemove: () => commitParams((next) => next.delete('quartier')),
      });
    }

    if (budgetMin !== undefined && budgetMin > 0) {
      chips.push({
        id: 'budgetMin',
        label: `Min : ${budgetMin.toLocaleString('fr-FR')} FCFA`,
        onRemove: () => commitParams((next) => next.delete('budgetMin')),
      });
    }

    if (budgetMax !== undefined && budgetMax > 0) {
      chips.push({
        id: 'budgetMax',
        label: `Max : ${budgetMax.toLocaleString('fr-FR')} FCFA`,
        onRemove: () => commitParams((next) => next.delete('budgetMax')),
      });
    }

    if (superficieMin !== undefined && superficieMin > 0) {
      chips.push({
        id: 'superficieMin',
        label: `≥ ${superficieMin} m²`,
        onRemove: () => commitParams((next) => next.delete('superficieMin')),
      });
    }

    if (nombrePieces !== undefined && nombrePieces > 0) {
      chips.push({
        id: 'nombrePieces',
        label: `${nombrePieces}+ pièces`,
        onRemove: () => commitParams((next) => next.delete('nombrePieces')),
      });
    }

    if (meubleFilter === 'MEUBLE') {
      chips.push({
        id: 'meuble',
        label: 'Meublé',
        onRemove: () => commitParams((next) => next.delete('meuble')),
      });
    } else if (meubleFilter === 'NON_MEUBLE') {
      chips.push({
        id: 'meuble',
        label: 'Non meublé',
        onRemove: () => commitParams((next) => next.delete('meuble')),
      });
    }

    if (animauxAcceptes) {
      chips.push({
        id: 'animaux',
        label: 'Animaux acceptés',
        onRemove: () => commitParams((next) => next.delete('animauxAcceptes')),
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
        onRemove: () =>
          commitParams((next) => {
            next.delete('statutVerification');
            next.delete('onlyVerified');
          }),
      });
    }

    return chips;
  }, [
    qFilter,
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
    commitParams,
    toggleType,
    toggleProximite,
  ]);

  // Count advanced filters configured inside the Drawer
  const activeAdvancedCount =
    selectedTypes.length +
    (quartierFilter.trim() ? 1 : 0) +
    (budgetMin !== undefined ? 1 : 0) +
    (budgetMax !== undefined ? 1 : 0) +
    (superficieMin !== undefined ? 1 : 0) +
    (nombrePieces !== undefined ? 1 : 0) +
    (meubleFilter !== 'TOUS' ? 1 : 0) +
    (animauxAcceptes ? 1 : 0) +
    selectedProximites.length;

  const parsedBudgetMaxDraft = budgetMaxDraft.trim() ? Number(budgetMaxDraft) : undefined;
  const effectiveDraftMax =
    parsedBudgetMaxDraft !== undefined && Number.isFinite(parsedBudgetMaxDraft) && parsedBudgetMaxDraft > 0
      ? parsedBudgetMaxDraft
      : undefined;

  return (
    <div className="h-[calc(100dvh-65px)] sm:h-[calc(100dvh-81px)] flex flex-col bg-sand-100 overflow-hidden">

      {/* MOBILE VIEW SWITCHER (< md) */}
      <div className="md:hidden bg-white border-b border-argile-200 px-3 py-2 flex items-center justify-between gap-2 shrink-0">
        <div className="inline-flex rounded-xl bg-sand-100 p-0.5 border border-argile-200">
          <button
            type="button"
            onClick={() => setMobileView('list')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-colors ${
              mobileView === 'list' ? 'bg-sahel-800 text-white shadow-sm' : 'text-charcoal-700'
            }`}
          >
            <List className="w-3.5 h-3.5" />
            <span>Liste ({data?.totalElements ?? 0})</span>
          </button>
          <button
            type="button"
            onClick={() => setMobileView('map')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-colors ${
              mobileView === 'map' ? 'bg-sahel-800 text-white shadow-sm' : 'text-charcoal-700'
            }`}
          >
            <MapIcon className="w-3.5 h-3.5" />
            <span>Carte</span>
          </button>
        </div>

        <Button
          variant="outline"
          size="sm"
          leftIcon={<SlidersHorizontal className="w-3.5 h-3.5 text-terracotta-500" />}
          onClick={() => setIsFilterDrawerOpen(true)}
          aria-label={activeAdvancedCount > 0 ? `Filtres (${activeAdvancedCount} actifs)` : 'Filtres'}
          className="shrink-0 text-xs"
        >
          <span>Filtres</span>
          {activeAdvancedCount > 0 && (
            <span
              data-testid="mobile-drawer-filter-count"
              className="ml-1 inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-terracotta-500 text-white text-[10px] font-bold"
            >
              {activeAdvancedCount}
            </span>
          )}
        </Button>
      </div>

      {/* MOBILE MAP ACTIVE FILTERS STRIP (< md, only when Map view is active) */}
      {mobileView === 'map' && activeChips.length > 0 && (
        <div
          data-testid="mobile-map-active-filters"
          className="md:hidden bg-sand-50/95 border-b border-argile-200 px-3 py-1.5 flex items-center gap-1.5 shrink-0"
        >
          <span className="text-[11px] font-bold text-sahel-900 whitespace-nowrap shrink-0">
            {activeChips.length} filtre{activeChips.length > 1 ? 's' : ''} :
          </span>
          <div className="flex-1 min-w-0 flex items-center gap-1.5 overflow-x-auto no-scrollbar">
            {activeChips.map((chip) => (
              <span
                key={chip.id}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-terracotta-100 text-terracotta-900 border border-terracotta-200 whitespace-nowrap shrink-0"
              >
                <span>{chip.label}</span>
                <button
                  type="button"
                  onClick={chip.onRemove}
                  data-testid={`mobile-map-remove-chip-${chip.id}`}
                  aria-label={`Supprimer le filtre ${chip.label}`}
                  className="p-0.5 hover:bg-terracotta-200 rounded-full transition-colors text-terracotta-700"
                >
                  <X className="w-3 h-3" />
                </button>
              </span>
            ))}
          </div>
          <button
            type="button"
            onClick={handleResetFilters}
            data-testid="mobile-map-clear-filters"
            className="text-[11px] font-semibold text-charcoal-700 hover:text-terracotta-700 underline whitespace-nowrap shrink-0 pl-1"
          >
            Effacer
          </button>
        </div>
      )}

      <div className="flex-1 min-h-0 flex flex-col md:flex-row overflow-hidden">

      {/* LEFT COLUMN: LISTINGS & FILTER BAR */}
      <div
        className={`w-full md:w-7/12 lg:w-1/2 flex-col flex-1 min-h-0 md:h-full border-r border-argile-200 ${
          mobileView === 'map' ? 'hidden md:flex' : 'flex'
        }`}
      >
        
        {/* Top Control & Quick Filters Bar */}
        <div className="p-3 sm:p-4 bg-white border-b border-argile-200 space-y-2.5 shrink-0 min-w-0 max-w-full">
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
              className="hidden md:inline-flex shrink-0"
            >
              <span>Filtres Avancés</span>
              {activeAdvancedCount > 0 && (
                <span
                  data-testid="desktop-drawer-filter-count"
                  className="ml-1.5 inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-terracotta-500 text-white text-[10px] font-bold"
                >
                  {activeAdvancedCount}
                </span>
              )}
            </Button>
          </div>

          {/* Keyword Search (q) & Sort Controls */}
          <form
            onSubmit={handleKeywordSubmit}
            className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 min-w-0"
            role="search"
            aria-label="Recherche par mot-clé et tri des résultats"
          >
            <div className="flex-1 min-w-0 flex items-center gap-1.5">
              <div className="relative flex-1 min-w-0">
                <Search className="w-3.5 h-3.5 text-charcoal-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="text"
                  enterKeyHint="search"
                  value={qInput}
                  onChange={(e) => handleKeywordChange(e.target.value)}
                  onBlur={() => {
                    if (qTimerRef.current !== null) {
                      window.clearTimeout(qTimerRef.current);
                      qTimerRef.current = null;
                      commitKeyword(qInput, false);
                    }
                  }}
                  placeholder="Mot-clé, quartier, F3, meublé..."
                  aria-label="Rechercher par mot-clé"
                  className="w-full pl-8 pr-7 py-1.5 rounded-xl border border-argile-300 bg-sand-50/70 text-xs text-charcoal-900 placeholder:text-charcoal-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-terracotta-500/30 focus:border-terracotta-500 transition-colors"
                />
                {qInput.length > 0 && (
                  <button
                    type="button"
                    onClick={handleClearKeyword}
                    aria-label="Effacer la recherche"
                    className="absolute right-2 top-1/2 -translate-y-1/2 p-0.5 rounded-full text-charcoal-400 hover:text-charcoal-700 hover:bg-sand-200 transition-colors"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
              <button
                type="submit"
                aria-label="Lancer la recherche"
                className="px-3 py-1.5 rounded-xl bg-terracotta-500 hover:bg-terracotta-600 text-white text-xs font-semibold transition-colors shrink-0 shadow-sm"
              >
                Chercher
              </button>
            </div>

            <div className="relative sm:w-48 shrink-0">
              <ArrowUpDown className="w-3.5 h-3.5 text-charcoal-500 absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              <select
                value={sort}
                onChange={(e) => handleSortChange(e.target.value)}
                aria-label="Trier les résultats"
                className="w-full pl-8 pr-6 py-1.5 rounded-xl border border-argile-300 bg-white text-xs font-semibold text-charcoal-800 focus:outline-none focus:ring-2 focus:ring-terracotta-500/30 focus:border-terracotta-500"
              >
                {SEARCH_SORT_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          </form>

          {/* Quick Mode & City Filters */}
          <div className="flex flex-wrap items-center gap-1.5 sm:gap-2 min-w-0 max-w-full text-xs font-semibold">
            {/* Mode selection buttons */}
            {(['TOUS', 'LOCATION', 'VENTE'] as const).map((m) => (
              <button
                key={m}
                onClick={() => handleModeChange(m)}
                className={`px-2.5 sm:px-3 py-1 sm:py-1.5 rounded-xl border transition-all whitespace-nowrap ${
                  modeTransaction === m
                    ? 'bg-terracotta-500 text-white border-terracotta-500 shadow-sm'
                    : 'bg-sand-100 text-charcoal-700 border-argile-300 hover:bg-sand-200'
                }`}
              >
                {m === 'TOUS' ? 'Tous les modes' : m === 'LOCATION' ? 'Location' : 'Vente'}
              </button>
            ))}

            <div className="hidden sm:block h-4 w-px bg-sand-300 shrink-0" />

            {/* Quick Cities */}
            {['TOUS', 'Dakar', 'Ziguinchor', 'Saly', 'Thiès'].map((c) => (
              <button
                key={c}
                onClick={() =>
                  commitParams((next) => {
                    next.delete('city');
                    if (c === 'TOUS') next.delete('ville');
                    else next.set('ville', c);
                  })
                }
                className={`px-2.5 sm:px-3 py-1 sm:py-1.5 rounded-xl border transition-all whitespace-nowrap ${
                  cityFilter === c
                    ? 'bg-sahel-800 text-white border-sahel-800'
                    : 'bg-sand-100 text-charcoal-700 border-argile-300 hover:bg-sand-200'
                }`}
              >
                {c === 'TOUS' ? 'Toutes les villes' : c}
              </button>
            ))}

            <button
              onClick={() =>
                commitParams((next) => {
                  next.delete('onlyVerified');
                  if (onlyVerified) next.delete('statutVerification');
                  else next.set('statutVerification', 'VERIFIEE');
                })
              }
              className={`px-2.5 sm:px-3 py-1 sm:py-1.5 rounded-xl border transition-all whitespace-nowrap flex items-center gap-1 ${
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
        <div className="flex-1 overflow-y-auto p-3 sm:p-4 pb-20 md:pb-4 space-y-4">
          
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
              {data && (
                <div
                  data-testid="results-transparency-summary"
                  className="flex flex-wrap items-center justify-between gap-2 text-xs text-charcoal-600 px-1"
                >
                  <span>
                    <strong className="font-bold text-charcoal-900">{properties.length}</strong>{' '}
                    annonce{properties.length > 1 ? 's' : ''} affichée{properties.length > 1 ? 's' : ''} sur cette page —{' '}
                    <strong className="font-bold text-charcoal-900">{data.totalElements}</strong> au total
                  </span>
                  {data.totalPages > 1 && (
                    <span className="font-mono text-[11px] font-semibold text-charcoal-500">
                      Page {data.number + 1} / {data.totalPages}
                    </span>
                  )}
                </div>
              )}

              <motion.div
                className="space-y-4"
                initial={shouldReduceMotion ? false : { opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.3 }}
              >
                {(() => {
                  const usedFallbacks = new Set<string>();
                  return properties.map((prop, idx) => {
                  const isSelected = selectedPropertyId === prop.id;
                  const fallbackUrl = selectFallbackPhoto(
                    mapPropertyCategory(prop.type, prop.titre),
                    prop.id,
                    usedFallbacks
                  );
                  const isFirst = idx === 0;
                  const hasSuperficie =
                    typeof prop.superficie === 'number' &&
                    Number.isFinite(prop.superficie) &&
                    prop.superficie > 0;
                  const hasPieces =
                    typeof prop.nombrePieces === 'number' &&
                    Number.isFinite(prop.nombrePieces) &&
                    prop.nombrePieces > 0;
                  const authorFullName = [prop.auteur?.prenom, prop.auteur?.nom]
                    .filter(Boolean)
                    .join(' ');
                  const isAgent = prop.auteurRole === 'AGENT' || prop.auteur?.role === 'AGENT';

                  return (
                    <motion.div
                      key={prop.id}
                      onClick={() => setSelectedPropertyId(prop.id)}
                      className={`flex flex-col sm:flex-row md:flex-col lg:flex-row gap-3.5 sm:gap-4 bg-white p-3.5 sm:p-4 rounded-2xl border transition-all cursor-pointer ${
                        isSelected
                          ? 'border-terracotta-500 ring-2 ring-terracotta-500/20 shadow-warm-hover'
                          : 'border-argile-200 hover:border-argile-300 hover:shadow-warm-card'
                      }`}
                      whileHover={shouldReduceMotion ? undefined : { y: -2 }}
                    >
                      {/* Photo Thumbnail */}
                      <div className="relative w-full sm:w-48 md:w-full lg:w-48 h-44 sm:h-36 md:h-44 lg:h-36 shrink-0 rounded-xl overflow-hidden bg-sand-200 aspect-[4/3]">
                        <PropertyVisual
                          mode="card"
                          rawUrl={prop.photos?.[0]?.url}
                          listingId={prop.id}
                          type={prop.type}
                          title={prop.titre}
                          quartier={prop.quartier}
                          fallbackUrl={fallbackUrl}
                          loading={isFirst ? 'eager' : 'lazy'}
                          fetchPriority={isFirst ? 'high' : undefined}
                          width={192}
                          height={144}
                          className="w-full h-full"
                        />
                        <div className="absolute top-2 left-2 flex flex-wrap items-center gap-1 max-w-[calc(100%-3.5rem)]">
                          <span className={`text-[10px] font-extrabold uppercase px-2 py-0.5 rounded shadow-sm ${
                            prop.modeTransaction === 'VENTE' ? 'bg-ocher-800 text-white' : 'bg-sahel-800 text-white'
                          }`}>
                            {prop.modeTransaction === 'VENTE' ? 'Vente' : 'Location'}
                          </span>
                          {prop.statutVerification === 'VERIFIEE' && (
                            <span className="bg-sahel-700 text-white text-[10px] font-bold px-1.5 py-0.5 rounded shadow-sm flex items-center gap-1">
                              <ShieldCheck className="w-3 h-3 shrink-0" /> Vérifié
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
                      <div className="flex-1 min-w-0 flex flex-col justify-between space-y-2">
                        <div>
                          <div className="flex flex-wrap items-center justify-between gap-1.5">
                            <span className="font-display font-extrabold text-base text-terracotta-600 whitespace-nowrap">
                              {prop.prix.toLocaleString('fr-FR')} FCFA
                              {prop.modeTransaction === 'LOCATION' && <span className="text-xs text-charcoal-500 font-normal"> /mois</span>}
                            </span>
                            <span className="text-xs text-charcoal-500 flex items-center gap-1 min-w-0 max-w-full sm:max-w-[190px] md:max-w-full lg:max-w-[190px]">
                              <MapPin className="w-3.5 h-3.5 shrink-0 text-terracotta-500" />
                              <span className="truncate">
                                {prop.ville}{prop.quartier ? ` (${prop.quartier})` : ''}
                              </span>
                            </span>
                          </div>

                          <h3 className="font-display font-bold text-sm text-charcoal-900 line-clamp-2 mt-1">
                            {prop.titre}
                          </h3>
                        </div>

                        {/* Vector Tags System */}
                        <div className="flex flex-wrap items-center gap-1.5 text-xs text-charcoal-600 bg-sand-100/90 p-2 rounded-lg">
                          {hasSuperficie && (
                            <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">
                              {prop.superficie} m²
                            </span>
                          )}
                          {hasPieces && (
                            <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">
                              {prop.nombrePieces} {prop.nombrePieces === 1 ? 'pièce' : 'pièces'}
                            </span>
                          )}
                          {prop.meuble && (
                            <span className="bg-terracotta-100 text-terracotta-800 text-[10px] font-bold px-2 py-0.5 rounded">
                              Meublé
                            </span>
                          )}
                          {prop.animauxAcceptes && (
                            <span className="bg-sahel-100 text-sahel-800 border border-sahel-200 text-[10px] font-bold px-2 py-0.5 rounded">
                              Animaux OK
                            </span>
                          )}
                          <span className="ml-auto font-mono text-[10px] uppercase bg-sahel-800 text-white px-2 py-0.5 rounded font-bold">
                            {prop.type}
                          </span>
                        </div>

                        <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
                          <div className="flex items-center gap-1.5 text-[11px] text-charcoal-600 flex-wrap min-w-0">
                            {authorFullName && (
                              <span className="font-semibold text-charcoal-800">{authorFullName}</span>
                            )}
                            {isAgent && (
                              <span className="bg-ocher-100 text-ocher-900 border border-ocher-300 text-[10px] font-bold px-1.5 py-0.5 rounded">
                                Publié par un agent immobilier
                              </span>
                            )}
                          </div>
                          <Link
                            to={`/annonce/${prop.id}`}
                            onClick={(e) => e.stopPropagation()}
                            className="shrink-0"
                          >
                            <Button variant="primary" size="sm" className="text-xs py-1.5 px-3">
                              Voir la fiche
                            </Button>
                          </Link>
                        </div>
                      </div>
                    </motion.div>
                  );
                  });
                })()}
              </motion.div>

              {/* SPRING PAGEABLE PAGINATION */}
              {data && data.totalPages > 1 && (
                <div className="flex flex-wrap items-center justify-between gap-2 pt-4 pb-16 md:pb-2 text-xs font-semibold text-charcoal-700 border-t border-argile-200">
                  <span>
                    Page {data.number + 1} sur {data.totalPages} ({properties.length} sur cette page / {data.totalElements} au total)
                  </span>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={data.first}
                      onClick={() =>
                        commitParams(
                          (next) => {
                            const prevPage = Math.max(0, page - 1);
                            if (prevPage > 0) next.set('page', String(prevPage));
                            else next.delete('page');
                          },
                          { resetPage: false }
                        )
                      }
                      leftIcon={<ChevronLeft className="w-4 h-4" />}
                    >
                      Précédent
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={data.last}
                      onClick={() =>
                        commitParams(
                          (next) => {
                            next.set('page', String(page + 1));
                          },
                          { resetPage: false }
                        )
                      }
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
      <div
        className={`w-full md:w-5/12 lg:w-1/2 flex-1 min-h-0 md:h-full relative isolate shrink-0 ${
          mobileView === 'list' ? 'hidden md:block' : 'block'
        }`}
      >
        {data && !isLoading && !isError && (
          <div
            data-testid="map-page-count-badge"
            className="pointer-events-none absolute top-2.5 right-2.5 z-[650] rounded-full bg-white/95 backdrop-blur-sm border border-argile-200 px-3 py-1 text-[11px] font-semibold text-charcoal-800 shadow-sm max-w-[calc(100%-4.5rem)] truncate"
          >
            {properties.length} sur cette page • {data.totalElements} au total
          </div>
        )}
        <React.Suspense
          fallback={
            <div className="w-full h-full flex flex-col items-center justify-center bg-sand-100 text-charcoal-400">
              <span className="text-2xl mb-1">🗺️</span>
              <span className="text-xs font-medium">Chargement de la carte...</span>
            </div>
          }
        >
          <LazyLeafletMap
            properties={properties}
            selectedPropertyId={selectedPropertyId}
            onSelectProperty={(id) => setSelectedPropertyId(id)}
          />
        </React.Suspense>
      </div>
      </div>

      {/* ADVANCED FILTERS DRAWER */}
      <Drawer
        isOpen={isFilterDrawerOpen}
        onClose={() => setIsFilterDrawerOpen(false)}
        title="Filtres de Recherche Approfondis"
        className="sm:w-[500px]"
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
                  onClick={() => handleModeChange(m)}
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
            <div className="grid grid-cols-2 gap-2">
              {(references?.typesBien || [
                { valeur: 'APPARTEMENT', libelle: 'Appartement' },
                { valeur: 'VILLA', libelle: 'Villa' },
                { valeur: 'STUDIO', libelle: 'Studio' },
                { valeur: 'CHAMBRE', libelle: 'Chambre' },
              ]).map((t) => {
                const isChecked = selectedTypes.includes(t.valeur);
                return (
                  <button
                    key={t.valeur}
                    type="button"
                    onClick={() => toggleType(t.valeur)}
                    className={`py-2 px-3 rounded-xl border text-xs font-medium flex items-center justify-between gap-2 transition-all ${
                      isChecked
                        ? 'bg-terracotta-50 border-terracotta-500 text-terracotta-800 font-semibold ring-1 ring-terracotta-400'
                        : 'bg-white border-sand-300 text-charcoal-700 hover:bg-sand-50'
                    }`}
                  >
                    <span className="text-left leading-snug">{t.libelle}</span>
                    {isChecked && <Check className="w-3.5 h-3.5 text-terracotta-600 shrink-0" />}
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
                  const val = e.target.value;
                  commitParams((next) => {
                    next.delete('city');
                    if (val === 'TOUS') next.delete('ville');
                    else next.set('ville', val);
                  });
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
                onChange={(val) =>
                  commitParams((next) => {
                    if (val.trim()) next.set('quartier', val.trim());
                    else next.delete('quartier');
                  })
                }
              />
            </div>
          </div>

          {/* Budget Min et Max (Curseur dépendant du mode - F9) */}
          <div className="space-y-3 pt-2 border-t border-sand-200">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-sahel-800">
                Budget (FCFA) {modeTransaction === 'VENTE' ? '— Vente' : modeTransaction === 'LOCATION' ? '— Location' : ''}
              </label>
              {(budgetMin !== undefined || budgetMax !== undefined) && (
                <button
                  type="button"
                  onClick={() => {
                    if (budgetMinTimerRef.current !== null) window.clearTimeout(budgetMinTimerRef.current);
                    if (budgetMaxTimerRef.current !== null) window.clearTimeout(budgetMaxTimerRef.current);
                    setBudgetMinDraft('');
                    setBudgetMaxDraft('');
                    commitParams((next) => {
                      next.delete('budgetMin');
                      next.delete('budgetMax');
                    });
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
                  min={0}
                  placeholder="Min"
                  value={budgetMinDraft}
                  onChange={(e) => {
                    const raw = e.target.value;
                    setBudgetMinDraft(raw);
                    if (budgetMinTimerRef.current !== null) window.clearTimeout(budgetMinTimerRef.current);
                    budgetMinTimerRef.current = window.setTimeout(() => {
                      budgetMinTimerRef.current = null;
                      commitNumericParam('budgetMin', raw, true);
                    }, 280);
                  }}
                  onBlur={() => {
                    if (budgetMinTimerRef.current !== null) {
                      window.clearTimeout(budgetMinTimerRef.current);
                      budgetMinTimerRef.current = null;
                      commitNumericParam('budgetMin', budgetMinDraft, false);
                    }
                  }}
                  className="w-full px-3 py-1.5 rounded-xl border border-sand-300 text-xs focus:ring-2 focus:ring-terracotta-500 outline-none"
                />
              </div>
              <div>
                <label className="block text-[11px] text-charcoal-500 mb-1">Budget Max (FCFA)</label>
                <input
                  type="number"
                  min={0}
                  placeholder="Max (sans plafond)"
                  value={budgetMaxDraft}
                  onChange={(e) => {
                    const raw = e.target.value;
                    setBudgetMaxDraft(raw);
                    if (budgetMaxTimerRef.current !== null) window.clearTimeout(budgetMaxTimerRef.current);
                    budgetMaxTimerRef.current = window.setTimeout(() => {
                      budgetMaxTimerRef.current = null;
                      commitNumericParam('budgetMax', raw, true);
                    }, 280);
                  }}
                  onBlur={() => {
                    if (budgetMaxTimerRef.current !== null) {
                      window.clearTimeout(budgetMaxTimerRef.current);
                      budgetMaxTimerRef.current = null;
                      commitNumericParam('budgetMax', budgetMaxDraft, false);
                    }
                  }}
                  className="w-full px-3 py-1.5 rounded-xl border border-sand-300 text-xs focus:ring-2 focus:ring-terracotta-500 outline-none"
                />
              </div>
            </div>

            <Slider
              label="Plafond maximal"
              min={sliderConfig.min}
              max={sliderConfig.max}
              step={sliderConfig.step}
              value={effectiveDraftMax !== undefined ? Math.min(effectiveDraftMax, sliderConfig.max) : sliderConfig.max}
              displayValue={
                effectiveDraftMax === undefined
                  ? 'Aucun plafond'
                  : effectiveDraftMax > sliderConfig.max
                    ? `${effectiveDraftMax.toLocaleString('fr-FR')} F`
                    : undefined
              }
              maxLabel="Sans plafond"
              onChange={(val) => {
                if (val >= sliderConfig.max) {
                  setBudgetMaxDraft('');
                } else {
                  setBudgetMaxDraft(String(val));
                }
              }}
              onCommit={(val) => {
                if (budgetMaxTimerRef.current !== null) {
                  window.clearTimeout(budgetMaxTimerRef.current);
                  budgetMaxTimerRef.current = null;
                }
                commitParams((next) => {
                  if (val >= sliderConfig.max || val <= 0) {
                    next.delete('budgetMax');
                  } else {
                    next.set('budgetMax', String(val));
                  }
                });
              }}
              formatValue={(val) => {
                if (val >= 1_000_000 && sliderConfig.max > RENT_BUDGET_THRESHOLD) {
                  return `${(val / 1_000_000).toLocaleString('fr-FR')}M F`;
                }
                return `${(val / 1000).toLocaleString('fr-FR')}k F`;
              }}
            />
          </div>

          {/* Superficie & Nombre de pièces (F10, F11) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-sand-200">
            <div>
              <label className="block text-xs font-bold text-sahel-800 mb-1.5">Superficie minimale (m²)</label>
              <input
                type="number"
                min={0}
                placeholder="Ex: 80"
                value={superficieDraft}
                onChange={(e) => {
                  const raw = e.target.value;
                  setSuperficieDraft(raw);
                  if (superficieTimerRef.current !== null) window.clearTimeout(superficieTimerRef.current);
                  superficieTimerRef.current = window.setTimeout(() => {
                    superficieTimerRef.current = null;
                    commitNumericParam('superficieMin', raw, true);
                  }, 280);
                }}
                onBlur={() => {
                  if (superficieTimerRef.current !== null) {
                    window.clearTimeout(superficieTimerRef.current);
                    superficieTimerRef.current = null;
                    commitNumericParam('superficieMin', superficieDraft, false);
                  }
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
                    onClick={() =>
                      commitParams((next) => {
                        if (nombrePieces === np) next.delete('nombrePieces');
                        else next.set('nombrePieces', String(np));
                      })
                    }
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
                  onClick={() =>
                    commitParams((next) => {
                      if (opt.val === 'MEUBLE') next.set('meuble', 'true');
                      else if (opt.val === 'NON_MEUBLE') next.set('meuble', 'false');
                      else next.delete('meuble');
                    })
                  }
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
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
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
                    className={`flex items-center gap-2 px-3 py-2 rounded-xl border text-xs text-left transition-all ${
                      isChecked
                        ? 'bg-sahel-50 border-sahel-600 text-sahel-900 font-semibold ring-1 ring-sahel-500'
                        : 'bg-white border-sand-300 text-charcoal-700 hover:bg-sand-50'
                    }`}
                  >
                    <Icon className={`w-3.5 h-3.5 shrink-0 ${isChecked ? 'text-sahel-700' : 'text-charcoal-500'}`} />
                    <span className="leading-snug flex-1 min-w-0">{p.libelle}</span>
                    {isChecked && <Check className="w-3.5 h-3.5 text-sahel-700 ml-auto shrink-0" />}
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
                  const checked = e.target.checked;
                  commitParams((next) => {
                    if (checked) next.set('animauxAcceptes', 'true');
                    else next.delete('animauxAcceptes');
                  });
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
                  const checked = e.target.checked;
                  commitParams((next) => {
                    next.delete('onlyVerified');
                    if (checked) next.set('statutVerification', 'VERIFIEE');
                    else next.delete('statutVerification');
                  });
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
            <Button
              variant="primary"
              size="md"
              className="flex-1"
              onClick={() => {
                if (budgetMinTimerRef.current !== null) {
                  window.clearTimeout(budgetMinTimerRef.current);
                  budgetMinTimerRef.current = null;
                  commitNumericParam('budgetMin', budgetMinDraft, false);
                }
                if (budgetMaxTimerRef.current !== null) {
                  window.clearTimeout(budgetMaxTimerRef.current);
                  budgetMaxTimerRef.current = null;
                  commitNumericParam('budgetMax', budgetMaxDraft, false);
                }
                if (superficieTimerRef.current !== null) {
                  window.clearTimeout(superficieTimerRef.current);
                  superficieTimerRef.current = null;
                  commitNumericParam('superficieMin', superficieDraft, false);
                }
                setIsFilterDrawerOpen(false);
              }}
            >
              Voir les résultats ({data?.totalElements ?? 0})
            </Button>
          </div>

        </div>
      </Drawer>

    </div>
  );
};

export default SearchResultsPage;
