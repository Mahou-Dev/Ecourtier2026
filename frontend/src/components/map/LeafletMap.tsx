import React, { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import MarkerClusterGroup from 'react-leaflet-cluster';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Property } from '../../mocks/properties';

export interface LeafletMapProps {
  properties: Property[];
  selectedPropertyId?: string | null;
  onSelectProperty?: (id: string) => void;
  center?: [number, number];
  zoom?: number;
  className?: string;
}

// Reactive controller component inside MapContainer to auto-pan and fit bounds
const MapController: React.FC<{
  properties: Property[];
  selectedPropertyId?: string | null;
  defaultCenter: [number, number];
  defaultZoom: number;
}> = ({ properties, selectedPropertyId, defaultCenter, defaultZoom }) => {
  const map = useMap();

  useEffect(() => {
    if (selectedPropertyId) {
      const selected = properties.find((p) => p.id === selectedPropertyId);
      if (selected) {
        map.setView([selected.lat, selected.lng], 15, { animate: true });
      }
    } else if (properties.length > 1) {
      const bounds = L.latLngBounds(properties.map((p) => [p.lat, p.lng]));
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 15 });
    } else if (properties.length === 1) {
      map.setView([properties[0].lat, properties[0].lng], 14, { animate: true });
    } else {
      map.setView(defaultCenter, defaultZoom);
    }
  }, [map, properties, selectedPropertyId, defaultCenter, defaultZoom]);

  return null;
};

// Custom E-Courtier Senegal Design System Price Badge Icon
const createCustomIcon = (property: Property, isSelected: boolean) => {
  const formattedPrice = (property.price / 1000).toLocaleString('fr-FR') + 'k FCFA';
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
  className = 'w-full h-full min-h-[400px]',
}) => {
  return (
    <div className={className}>
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

        <MarkerClusterGroup
          chunkedLoading
          maxClusterRadius={50}
        >
          {properties.map((property) => {
            const isSelected = property.id === selectedPropertyId;
            return (
              <Marker
                key={property.id}
                position={[property.lat, property.lng]}
                icon={createCustomIcon(property, isSelected)}
                eventHandlers={{
                  click: () => {
                    if (onSelectProperty) onSelectProperty(property.id);
                  },
                }}
              >
                <Popup>
                  <div className="p-1 max-w-[200px] font-body text-xs">
                    <img
                      src={property.images[0]}
                      alt={property.title}
                      className="w-full h-24 object-cover rounded-lg mb-2"
                    />
                    <div className="font-bold text-charcoal-900 line-clamp-1">
                      {property.title}
                    </div>
                    <div className="text-terracotta-600 font-extrabold text-sm mt-1">
                      {property.price.toLocaleString('fr-FR')} FCFA
                      {property.pricePeriod || ''}
                    </div>
                    <div className="text-charcoal-500 text-[11px] mt-0.5">
                      {property.neighborhood}, {property.city}
                    </div>
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
