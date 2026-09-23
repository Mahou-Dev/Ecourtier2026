import React, { useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { SlidersHorizontal, MapPin, ShieldCheck, Search, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { apiFetch, getPhotoUrl } from '../services/api';
import { AnnonceDTO, PageResponse } from '../types';
import { LeafletMap } from '../components/map/LeafletMap';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Slider } from '../components/ui/Slider';
import { Select } from '../components/ui/Select';
import { Drawer } from '../components/ui/Drawer';
import { Skeleton } from '../components/ui/Skeleton';
import { PatternClaustra } from '../components/ui/PatternClaustra';

export const SearchResultsPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedPropertyId, setSelectedPropertyId] = useState<string | null>(null);
  const [isFilterDrawerOpen, setIsFilterDrawerOpen] = useState(false);

  // Filter States
  const [cityFilter, setCityFilter] = useState<string>(searchParams.get('city') || searchParams.get('ville') || 'TOUS');
  const [typeFilter, setTypeFilter] = useState<string>('TOUS');
  const [modeTransactionFilter, setModeTransactionFilter] = useState<string>('TOUS');
  const [proximiteFilter, setProximiteFilter] = useState<string>('TOUS');
  const [maxPrice, setMaxPrice] = useState<number>(800000);
  const [onlyVerified, setOnlyVerified] = useState<boolean>(false);
  const [onlyFurnished, setOnlyFurnished] = useState<boolean>(false);
  const [page, setPage] = useState<number>(0);

  // TanStack Query fetching GET /api/v1/annonces with Spring Pageable
  const { data, isLoading, isError, refetch } = useQuery<PageResponse<AnnonceDTO>>({
    queryKey: [
      'annonces',
      cityFilter,
      typeFilter,
      modeTransactionFilter,
      proximiteFilter,
      maxPrice,
      onlyVerified,
      onlyFurnished,
      page,
    ],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', '9');
      params.append('tri', 'dateCreation');
      params.append('direction', 'DESC');

      if (cityFilter !== 'TOUS') params.append('ville', cityFilter);
      if (typeFilter !== 'TOUS') params.append('type', typeFilter);
      if (modeTransactionFilter !== 'TOUS') params.append('modeTransaction', modeTransactionFilter);
      if (proximiteFilter !== 'TOUS') params.append('proximite', proximiteFilter);
      if (maxPrice > 0) params.append('budgetMax', maxPrice.toString());
      if (onlyFurnished) params.append('meuble', 'true');

      return apiFetch<PageResponse<AnnonceDTO>>(`/api/v1/annonces?${params.toString()}`);
    },
  });

  const rawProperties = data?.content || [];
  // Client filter for onlyVerified if specified (or backend statutVerification check)
  const properties = onlyVerified
    ? rawProperties.filter((p) => p.statutVerification === 'VERIFIEE')
    : rawProperties;

  const handleResetFilters = () => {
    setCityFilter('TOUS');
    setTypeFilter('TOUS');
    setModeTransactionFilter('TOUS');
    setProximiteFilter('TOUS');
    setMaxPrice(800000);
    setOnlyVerified(false);
    setOnlyFurnished(false);
    setPage(0);
    setSearchParams({});
  };

  return (
    <div className="h-[calc(100vh-80px)] flex flex-col md:flex-row overflow-hidden bg-sand-100">
      
      {/* LEFT COLUMN / TOP ON MOBILE: FILTER BAR & LISTINGS */}
      <div className="w-full md:w-7/12 lg:w-1/2 flex flex-col h-full border-r border-argile-200">
        
        {/* Top Control & Quick Filters Bar */}
        <div className="p-4 bg-white border-b border-argile-200 space-y-3 shrink-0 min-w-0 max-w-full overflow-hidden">
          <div className="flex items-center justify-between gap-2 min-w-0 max-w-full">
            <div className="min-w-0">
              <h1 className="font-display font-extrabold text-base sm:text-lg text-charcoal-900 truncate">
                Logements Disponibles ({data?.totalElements ?? 0})
              </h1>
              <p className="text-[11px] sm:text-xs text-charcoal-500 truncate">
                Dakar, Ziguinchor, Saly &bull; Annonces contrôlées par E-Courtier
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

          {/* Quick Filter Chips */}
          <div className="flex items-center gap-2 overflow-x-auto min-w-0 max-w-full no-scrollbar text-xs font-semibold">
            {['TOUS', 'Dakar', 'Ziguinchor', 'Saly', 'Thiès'].map((c) => (
              <button
                key={c}
                onClick={() => {
                  setCityFilter(c);
                  setPage(0);
                }}
                className={`px-3 py-1.5 rounded-xl border transition-all whitespace-nowrap ${
                  cityFilter === c ? 'bg-sahel-800 text-white border-sahel-800' : 'bg-sand-100 text-charcoal-700 border-argile-300 hover:bg-sand-200'
                }`}
              >
                {c === 'TOUS' ? 'Toutes les villes' : c}
              </button>
            ))}

            <button
              onClick={() => setOnlyVerified(!onlyVerified)}
              className={`px-3 py-1.5 rounded-xl border transition-all whitespace-nowrap flex items-center gap-1 ${
                onlyVerified ? 'bg-terracotta-500 text-white border-terracotta-500' : 'bg-sand-100 text-charcoal-700 border-argile-300'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Vérifiées uniquement</span>
            </button>
          </div>
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
                    <Skeleton variant="text" className="w-full" />
                  </div>
                </div>
              ))}
            </div>
          ) : isError ? (
            <div className="p-8 text-center space-y-4 bg-white rounded-2xl border border-argile-200 my-8">
              <h3 className="font-display font-bold text-base text-charcoal-800">Erreur de chargement</h3>
              <p className="text-xs text-charcoal-500">Impossible de récupérer les annonces pour le moment.</p>
              <Button variant="primary" size="sm" onClick={() => refetch()} leftIcon={<RefreshCw className="w-4 h-4" />}>
                Réessayer
              </Button>
            </div>
          ) : properties.length === 0 ? (
            <div className="relative overflow-hidden p-8 sm:p-12 text-center space-y-4 bg-white rounded-2xl border border-argile-300 my-8 shadow-warm-card">
              <PatternClaustra variant="terracotta" opacity={0.06} scale={1} />
              <div className="relative z-10 w-14 h-14 rounded-full bg-terracotta-100 text-terracotta-600 flex items-center justify-center mx-auto shadow-xs">
                <Search className="w-7 h-7" />
              </div>
              <div className="relative z-10 space-y-1.5 max-w-md mx-auto">
                <h3 className="font-display font-extrabold text-lg text-charcoal-900">
                  Aucun bien ne correspond à ces critères
                </h3>
                <p className="text-xs text-charcoal-600 leading-relaxed">
                  Aucun logement vérifié n'est actuellement répertorié dans cette zone avec vos filtres exacts. Élargissez votre périmètre ou réinitialisez la recherche.
                </p>
              </div>
              <div className="relative z-10 pt-2">
                <Button variant="primary" size="md" onClick={handleResetFilters} leftIcon={<RefreshCw className="w-4 h-4" />}>
                  Réinitialiser les filtres
                </Button>
              </div>
            </div>
          ) : (
            <>
              {properties.map((prop) => {
                const isSelected = prop.id === selectedPropertyId;
                const firstPhoto = prop.photos && prop.photos.length > 0 ? getPhotoUrl(prop.photos[0].url) : getPhotoUrl();
                return (
                  <div
                    key={prop.id}
                    onMouseEnter={() => setSelectedPropertyId(prop.id)}
                    className={`bg-white rounded-xl border transition-all duration-200 overflow-hidden shadow-warm-card flex flex-col sm:flex-row ${
                      isSelected ? 'border-terracotta-500 ring-2 ring-terracotta-500/20 shadow-terracotta-card' : 'border-argile-200 hover:border-terracotta-400'
                    }`}
                  >
                    {/* Property Image Container */}
                    <div className="relative w-full sm:w-52 h-44 sm:h-auto shrink-0 bg-sand-200">
                      <img src={firstPhoto} alt={prop.titre} className="w-full h-full object-cover" />
                      {prop.statutVerification === 'VERIFIEE' && (
                        <div className="absolute top-2 left-2">
                          <Badge variant="sahel" className="text-[10px] px-2 py-0.5 font-bold">
                            <ShieldCheck className="w-3 h-3 mr-1 inline" /> Vérifié
                          </Badge>
                        </div>
                      )}
                    </div>

                    {/* Property Details */}
                    <div className="p-4 flex-1 flex flex-col justify-between space-y-3">
                      <div>
                        <div className="flex items-center justify-between text-xs text-sahel-800 font-semibold mb-1">
                          <span className="flex items-center gap-1">
                            <MapPin className="w-3.5 h-3.5 text-terracotta-500" />
                            {prop.quartier}, {prop.ville}
                          </span>
                          <span className="text-terracotta-600 font-extrabold text-sm">
                            {prop.prix.toLocaleString('fr-FR')} FCFA {prop.modeTransaction === 'LOCATION' ? '/ mois' : ''}
                          </span>
                        </div>
                        <h3 className="font-display font-bold text-sm text-charcoal-900 line-clamp-2">
                          {prop.titre}
                        </h3>
                      </div>

                      {/* Vector Tags System */}
                      <div className="flex items-center gap-2 text-xs text-charcoal-600 bg-sand-100/90 p-2 rounded-lg">
                        <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">{prop.superficie} m²</span>
                        <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-argile-200">{prop.nombrePieces} pièces</span>
                        {prop.meuble && <span className="bg-terracotta-100 text-terracotta-800 text-[10px] font-bold px-2 py-0.5 rounded">Meublé</span>}
                        <span className="ml-auto font-mono text-[10px] uppercase bg-sahel-800 text-white px-2 py-0.5 rounded font-bold">{prop.type}</span>
                      </div>

                      <div className="flex items-center justify-between pt-1">
                        <div className="flex items-center gap-1.5 text-[11px] text-charcoal-600">
                          <span className="font-semibold text-charcoal-800">{prop.auteur?.prenom} {prop.auteur?.nom}</span>
                        </div>
                        <Link to={`/annonce/${prop.id}`}>
                          <Button variant="primary" size="sm" className="text-xs py-1.5 px-3">
                            Voir la fiche
                          </Button>
                        </Link>
                      </div>
                    </div>
                  </div>
                );
              })}

              {/* REAL SPRING PAGEABLE PAGINATION */}
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

      {/* RIGHT COLUMN / BOTTOM ON MOBILE: SYNCHRONIZED LEAFLET MAP */}
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
          
          {/* City */}
          <Select
            label="Ville"
            options={[
              { value: 'TOUS', label: 'Toutes les villes' },
              { value: 'Dakar', label: 'Dakar' },
              { value: 'Ziguinchor', label: 'Ziguinchor' },
              { value: 'Saly', label: 'Saly' },
              { value: 'Thiès', label: 'Thiès' },
            ]}
            value={cityFilter}
            onChange={(val) => {
              setCityFilter(val);
              setPage(0);
            }}
          />

          {/* Mode Transaction */}
          <Select
            label="Mode de Transaction"
            options={[
              { value: 'TOUS', label: 'Tous les modes' },
              { value: 'LOCATION', label: 'Location' },
              { value: 'VENTE', label: 'Vente' },
            ]}
            value={modeTransactionFilter}
            onChange={(val) => {
              setModeTransactionFilter(val);
              setPage(0);
            }}
          />

          {/* Type */}
          <Select
            label="Type de bien"
            options={[
              { value: 'TOUS', label: 'Tous les types' },
              { value: 'APPARTEMENT', label: 'Appartement' },
              { value: 'VILLA', label: 'Villa' },
              { value: 'STUDIO', label: 'Studio' },
            ]}
            value={typeFilter}
            onChange={(val) => {
              setTypeFilter(val);
              setPage(0);
            }}
          />

          {/* Proximité */}
          <Select
            label="Proximité (Écoles, Hôpitaux, Transports...)"
            options={[
              { value: 'TOUS', label: 'Toutes proximités' },
              { value: 'ECOLE', label: 'Écoles / Universités' },
              { value: 'HOPITAL', label: 'Hôpitaux / Pharmacies' },
              { value: 'MARCHE', label: 'Marchés / Commerces' },
              { value: 'TRANSPORT', label: 'Transports en commun (BRT/TER)' },
              { value: 'COMMERCE', label: 'Supermarchés' },
              { value: 'PLAGE', label: 'Mer / Plage' },
              { value: 'MOSQUEE', label: 'Lieu de culte / Mosquée' },
            ]}
            value={proximiteFilter}
            onChange={(val) => {
              setProximiteFilter(val);
              setPage(0);
            }}
          />

          {/* Budget Slider */}
          <Slider
            label="Budget Maximal (FCFA)"
            min={50000}
            max={2000000}
            step={25000}
            value={maxPrice}
            onChange={(val) => {
              setMaxPrice(val);
              setPage(0);
            }}
            formatValue={(val) => `${val.toLocaleString('fr-FR')} F`}
          />

          {/* Checkboxes */}
          <div className="space-y-3 pt-2">
            <h4 className="text-xs font-semibold text-charcoal-800 uppercase tracking-wider">Garanties & Équipements</h4>
            
            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={onlyVerified}
                onChange={(e) => setOnlyVerified(e.target.checked)}
                className="w-4 h-4 rounded text-terracotta-500 focus:ring-terracotta-500"
              />
              <span className="text-xs font-medium text-charcoal-800">Uniquement avec titre & propriété vérifiés</span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={onlyFurnished}
                onChange={(e) => {
                  setOnlyFurnished(e.target.checked);
                  setPage(0);
                }}
                className="w-4 h-4 rounded text-terracotta-500 focus:ring-terracotta-500"
              />
              <span className="text-xs font-medium text-charcoal-800">Logements meublés uniquement</span>
            </label>
          </div>

          <div className="pt-4 flex items-center gap-3">
            <Button variant="outline" size="md" className="flex-1" onClick={handleResetFilters}>
              Réinitialiser
            </Button>
            <Button variant="primary" size="md" className="flex-1" onClick={() => setIsFilterDrawerOpen(false)}>
              Appliquer les filtres
            </Button>
          </div>

        </div>
      </Drawer>

    </div>
  );
};
