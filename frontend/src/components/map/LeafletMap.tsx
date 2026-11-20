import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import MarkerClusterGroup from 'react-leaflet-cluster';
import { ArrowRight } from 'lucide-react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { AnnonceDTO } from '../../types';
import { PropertyVisual } from '../property/PropertyVisual';

export interface LeafletMapProps {
  properties: AnnonceDTO[];
  selectedPropertyId?: string | null;
  onSelectProperty?: (id: string) => void;
  center?: [number, number];
  zoom?: number;
  className?: string;
  showPopupAction?: boolean;
}

// Reactive controller component inside MapContainer to auto-pan and fit bounds
const MapController: React.FC<{
  properties: AnnonceDTO[];
  selectedPropertyId?: string | null;
  defaultCenter: [number, number];
  defaultZoom: number;
}> = ({ properties, selectedPropertyId, defaultCenter, defaultZoom }) => {
  const map = useMap();
  const [layoutTick, setLayoutTick] = useState(0);

  useEffect(() => {
    let isMounted = true;
    const container = map.getContainer();
    const isMapReady = () => isMounted && container.isConnected && Boolean(map.getPane('mapPane'));
    let wasHidden = container.clientWidth === 0 || container.clientHeight === 0;
    const rafId = requestAnimationFrame(() => {
      if (isMapReady()) {
        map.invalidateSize({ pan: false });
      }
    });

    if (typeof ResizeObserver === 'undefined') {
      return () => {
        isMounted = false;
        cancelAnimationFrame(rafId);
      };
    }

    const observer = new ResizeObserver(() => {
      if (!isMapReady()) return;
      map.invalidateSize({ pan: false });
      const isVisible = container.clientWidth > 0 && container.clientHeight > 0;
      if (wasHidden && isVisible) {
        wasHidden = false;
        setLayoutTick((tick) => tick + 1);
      } else if (!isVisible) {
        wasHidden = true;
      }
    });
    observer.observe(container);

    return () => {
      isMounted = false;
      cancelAnimationFrame(rafId);
      observer.disconnect();
    };
  }, [map]);

  useEffect(() => {
    const container = map.getContainer();
    if (!container?.isConnected || !map.getPane('mapPane')) {
      return;
    }
    map.invalidateSize({ pan: false });
    const size = map.getSize();
    // Leaflet flyTo/fitBounds throw Invalid LatLng (NaN, NaN) on 0x0 hidden containers
    if (size.x <= 0 || size.y <= 0) {
      return;
    }

    const validProperties = properties.filter(
      (p) =>
        typeof p.latitude === 'number' &&
        typeof p.longitude === 'number' &&
        Number.isFinite(p.latitude) &&
        Number.isFinite(p.longitude)
    );

    if (selectedPropertyId) {
      const selected = validProperties.find((p) => p.id === selectedPropertyId);
      if (selected) {
        map.flyTo([selected.latitude, selected.longitude], 15, {
          duration: 0.6,
          easeLinearity: 0.25,
        });
      }
    } else if (validProperties.length > 1) {
      const validPoints = validProperties.map((p) => [p.latitude, p.longitude] as [number, number]);
      const bounds = L.latLngBounds(validPoints);
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 15, animate: false });
    } else if (validProperties.length === 1) {
      map.setView([validProperties[0].latitude, validProperties[0].longitude], 14, { animate: false });
    } else {
      map.setView(defaultCenter, defaultZoom, { animate: false });
    }

    return () => {
      try {
        map.stop();
      } catch {
        // Ignore teardown race if Leaflet has already removed internal panes
      }
    };
  }, [map, properties, selectedPropertyId, defaultCenter, defaultZoom, layoutTick]);

  return null;
};

// Custom E-Courtier Senegal Price Badge Icon
const createCustomIcon = (property: AnnonceDTO, isSelected: boolean) => {
  const formattedPrice = (property.prix / 1000).toLocaleString('fr-FR') + 'k F';
  return L.divIcon({
    className: 'custom-map-pin',
    html: `
      <div class="px-2.5 py-1 rounded-full text-xs font-extrabold shadow-md transition-all duration-200 flex items-center gap-1 border whitespace-nowrap ${
        isSelected
          ? 'bg-terracotta-500 text-white border-white scale-110 ring-4 ring-terracotta-300/50'
          : 'bg-sahel-800 text-white border-sahel-600 hover:bg-terracotta-500 hover:scale-105'
      }">
        <span>${formattedPrice}</span>
      </div>
    `,
    iconSize: [80, 30],
    iconAnchor: [40, 15],
  });
};

export const LeafletMap: React.FC<LeafletMapProps> = ({
  properties,
  selectedPropertyId,
  onSelectProperty,
  center = [14.6937, -17.4441], // Default Dakar
  zoom = 12,
  className = 'w-full h-full min-h-[300px]',
  showPopupAction = true,
}) => {
  const location = useLocation();
  return (
    <div className={`w-full max-w-full overflow-hidden relative ${className}`}>
      <MapContainer
        center={center}
        zoom={zoom}
        zoomControl={true}
        scrollWheelZoom={true}
        className="w-full h-full rounded-2xl overflow-hidden z-0"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          maxZoom={19}
        />

        <MapController
          properties={properties}
          selectedPropertyId={selectedPropertyId}
          defaultCenter={center}
          defaultZoom={zoom}
        />

        <MarkerClusterGroup maxClusterRadius={50}>
          {properties
            .filter(
              (p) =>
                typeof p.latitude === 'number' &&
                typeof p.longitude === 'number' &&
                Number.isFinite(p.latitude) &&
                Number.isFinite(p.longitude)
            )
            .map((property) => {
              const isSelected = property.id === selectedPropertyId;
              return (
                <Marker
                  key={property.id}
                  position={[property.latitude, property.longitude]}
                  icon={createCustomIcon(property, isSelected)}
                  title={property.titre}
                  alt={`${property.titre} — ${property.prix.toLocaleString('fr-FR')} FCFA`}
                  keyboard={true}
                  eventHandlers={{
                    click: () => {
                      if (onSelectProperty) onSelectProperty(property.id);
                    },
                  }}
                >
                  <Popup>
                    <div
                      className="p-1 w-[205px] max-w-[calc(100vw-5rem)] font-body text-xs"
                      onClick={(e) => e.stopPropagation()}
                      onMouseDown={(e) => e.stopPropagation()}
                    >
                      <PropertyVisual
                        mode="thumbnail"
                        rawUrl={property.photos?.[0]?.url}
                        listingId={property.id}
                        type={property.type}
                        title={property.titre}
                        quartier={property.quartier}
                        className="w-full h-24 rounded-lg mb-2"
                        showIllustrativeBadge={true}
                        illustrativeBadgeCompact={true}
                      />
                      <div className="font-bold text-charcoal-900 line-clamp-1">
                        {property.titre}
                      </div>
                      <div className="text-terracotta-600 font-extrabold text-sm mt-1">
                        {property.prix.toLocaleString('fr-FR')} FCFA
                        {property.modeTransaction === 'LOCATION' && (
                          <span className="text-[11px] font-normal text-charcoal-500"> /mois</span>
                        )}
                      </div>
                      <div className="text-charcoal-500 text-[11px] mt-0.5 line-clamp-1">
                        {property.quartier ? `${property.quartier}, ` : ''}{property.ville}
                      </div>
                      {showPopupAction && location.pathname !== `/annonce/${property.id}` && (
                        <Link
                          to={`/annonce/${property.id}`}
                          onClick={(e) => e.stopPropagation()}
                          onMouseDown={(e) => e.stopPropagation()}
                          className="mt-2.5 inline-flex w-full items-center justify-center gap-1.5 rounded-xl bg-terracotta-500 px-3 py-1.5 text-xs font-bold !text-white no-underline shadow-sm hover:bg-terracotta-600 focus:outline-none focus:ring-2 focus:ring-terracotta-500/40 transition-colors"
                        >
                          <span>Voir la fiche</span>
                          <ArrowRight className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                        </Link>
                      )}
                    </div>
                  </Popup>
                </Marker>
              );
            })}
        </MarkerClusterGroup>
      </MapContainer>
    </div>
  );
};
