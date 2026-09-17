import React, { useState, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { SlidersHorizontal, MapPin, ShieldCheck, Search, RefreshCw } from 'lucide-react';
import { MOCK_PROPERTIES } from '../mocks/properties';
import { LeafletMap } from '../components/map/LeafletMap';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Slider } from '../components/ui/Slider';
import { Select } from '../components/ui/Select';
import { Drawer } from '../components/ui/Drawer';
import { Skeleton } from '../components/ui/Skeleton';

export const SearchResultsPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [selectedPropertyId, setSelectedPropertyId] = useState<string | null>(null);
  const [isFilterDrawerOpen, setIsFilterDrawerOpen] = useState(false);
  const [isLoading] = useState(false);

  // Filter States
  const [cityFilter, setCityFilter] = useState<string>(searchParams.get('city') || 'TOUS');
  const [typeFilter, setTypeFilter] = useState<string>('TOUS');
  const [maxPrice, setMaxPrice] = useState<number>(800000);
  const [onlyVerified, setOnlyVerified] = useState<boolean>(false);
  const [onlyFurnished, setOnlyFurnished] = useState<boolean>(false);
  const [onlyGenerator, setOnlyGenerator] = useState<boolean>(false);

  const filteredProperties = useMemo(() => {
    return MOCK_PROPERTIES.filter((p) => {
      if (cityFilter !== 'TOUS' && p.city !== cityFilter) return false;
      if (typeFilter !== 'TOUS' && p.type !== typeFilter) return false;
      if (p.price > maxPrice) return false;
      if (onlyVerified && !p.isVerified) return false;
      if (onlyFurnished && !p.isFurnished) return false;
      if (onlyGenerator && !p.hasGenerator) return false;
      return true;
    });
  }, [cityFilter, typeFilter, maxPrice, onlyVerified, onlyFurnished, onlyGenerator]);

  const handleResetFilters = () => {
    setCityFilter('TOUS');
    setTypeFilter('TOUS');
    setMaxPrice(800000);
    setOnlyVerified(false);
    setOnlyFurnished(false);
    setOnlyGenerator(false);
  };

  return (
    <div className="h-[calc(100vh-80px)] flex flex-col md:flex-row overflow-hidden bg-sand-100">
      
      {/* LEFT COLUMN / TOP ON MOBILE: FILTER BAR & LISTINGS */}
      <div className="w-full md:w-7/12 lg:w-1/2 flex flex-col h-full border-r border-argile-200">
        
        {/* Top Control & Quick Filters Bar */}
        <div className="p-4 bg-white border-b border-argile-200 space-y-3 shrink-0">
          <div className="flex items-center justify-between gap-2">
            <div>
              <h1 className="font-display font-extrabold text-lg text-charcoal-900">
                Logements Disponibles ({filteredProperties.length})
              </h1>
              <p className="text-xs text-charcoal-500">
                Dakar, Ziguinchor, Saly &bull; Filtres certifiés
              </p>
            </div>
            <Button
              variant="outline"
              size="sm"
              leftIcon={<SlidersHorizontal className="w-4 h-4 text-terracotta-500" />}
              onClick={() => setIsFilterDrawerOpen(true)}
            >
              Filtres Avancés
            </Button>
          </div>

          {/* Quick Filter Chips */}
          <div className="flex items-center gap-2 overflow-x-auto no-scrollbar text-xs font-semibold">
            {['TOUS', 'Dakar', 'Ziguinchor', 'Saly'].map((c) => (
              <button
                key={c}
                onClick={() => setCityFilter(c)}
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
              <span>Certifiées uniquement</span>
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
          ) : filteredProperties.length === 0 ? (
            <div className="p-8 text-center space-y-4 bg-white rounded-2xl border border-argile-200 my-8">
              <div className="w-12 h-12 rounded-full bg-sand-200 text-charcoal-500 flex items-center justify-center mx-auto">
                <Search className="w-6 h-6" />
              </div>
              <h3 className="font-display font-bold text-base text-charcoal-800">Aucun logement trouvé</h3>
              <p className="text-xs text-charcoal-500 max-w-sm mx-auto">
                Aucune annonce ne correspond à vos critères actuels. Essayez d’élargir votre budget ou de réinitialiser vos filtres.
              </p>
              <Button variant="primary" size="sm" onClick={handleResetFilters} leftIcon={<RefreshCw className="w-4 h-4" />}>
                Réinitialiser les filtres
              </Button>
            </div>
          ) : (
            filteredProperties.map((prop) => {
              const isSelected = prop.id === selectedPropertyId;
              return (
                <div
                  key={prop.id}
                  onMouseEnter={() => setSelectedPropertyId(prop.id)}
                  className={`bg-white rounded-2xl border transition-all duration-200 overflow-hidden shadow-warm-card flex flex-col sm:flex-row ${
                    isSelected ? 'border-terracotta-500 ring-2 ring-terracotta-500/20 shadow-terracotta-glow' : 'border-argile-200 hover:border-terracotta-300'
                  }`}
                >
                  {/* Property Image Container */}
                  <div className="relative w-full sm:w-52 h-44 sm:h-auto shrink-0 bg-sand-200">
                    <img src={prop.images[0]} alt={prop.title} className="w-full h-full object-cover" />
                    <div className="absolute top-2 left-2">
                      <Badge variant="sahel" className="text-[10px] px-2 py-0.5">
                        <ShieldCheck className="w-3 h-3 mr-1 inline" /> Certifié
                      </Badge>
                    </div>
                  </div>

                  {/* Property Details */}
                  <div className="p-4 flex-1 flex flex-col justify-between space-y-3">
                    <div>
                      <div className="flex items-center justify-between text-xs text-sahel-800 font-semibold mb-1">
                        <span className="flex items-center gap-1">
                          <MapPin className="w-3.5 h-3.5 text-terracotta-500" />
                          {prop.neighborhood}, {prop.city}
                        </span>
                        <span className="text-terracotta-600 font-extrabold text-sm">
                          {prop.price.toLocaleString('fr-FR')} FCFA{prop.pricePeriod}
                        </span>
                      </div>
                      <h3 className="font-display font-bold text-sm text-charcoal-900 line-clamp-2">
                        {prop.title}
                      </h3>
                    </div>

                    <div className="flex items-center gap-3 text-xs text-charcoal-500 bg-sand-100 p-2 rounded-xl">
                      <span>📐 {prop.surface} m²</span>
                      <span>🚪 {prop.rooms} pièces</span>
                      <span>🛏️ {prop.bedrooms} ch.</span>
                      {prop.isFurnished && <span className="text-terracotta-600 font-semibold">🛋️ Meublé</span>}
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      <div className="flex items-center gap-1.5 text-[11px] text-charcoal-600">
                        <img src={prop.owner.avatar} alt={prop.owner.name} className="w-5 h-5 rounded-full object-cover" />
                        <span className="truncate max-w-[110px]">{prop.owner.name}</span>
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
            })
          )}

        </div>
      </div>

      {/* RIGHT COLUMN / BOTTOM ON MOBILE: SYNCHRONIZED LEAFLET MAP */}
      <div className="w-full md:w-5/12 lg:w-1/2 h-64 md:h-full relative shrink-0">
        <LeafletMap
          properties={filteredProperties}
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
            ]}
            value={cityFilter}
            onChange={setCityFilter}
          />

          {/* Type */}
          <Select
            label="Type de bien"
            options={[
              { value: 'TOUS', label: 'Tous les types' },
              { value: 'APPARTEMENT', label: 'Appartement' },
              { value: 'VILLA', label: 'Villa' },
              { value: 'STUDIO', label: 'Studio' },
              { value: 'DUPLEX', label: 'Duplex' },
            ]}
            value={typeFilter}
            onChange={setTypeFilter}
          />

          {/* Budget Slider */}
          <Slider
            label="Budget Maximal (FCFA / mois)"
            min={50000}
            max={1500000}
            step={25000}
            value={maxPrice}
            onChange={setMaxPrice}
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
              <span className="text-xs font-medium text-charcoal-800">Annonces certifiées avec titre foncier vérifié</span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={onlyFurnished}
                onChange={(e) => setOnlyFurnished(e.target.checked)}
                className="w-4 h-4 rounded text-terracotta-500 focus:ring-terracotta-500"
              />
              <span className="text-xs font-medium text-charcoal-800">Meublé</span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={onlyGenerator}
                onChange={(e) => setOnlyGenerator(e.target.checked)}
                className="w-4 h-4 rounded text-terracotta-500 focus:ring-terracotta-500"
              />
              <span className="text-xs font-medium text-charcoal-800">Groupe Électrogène autonome (Secours coupure)</span>
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
