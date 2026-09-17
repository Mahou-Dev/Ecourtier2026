import React, { useEffect, useRef } from 'react';
import L from 'leaflet';
import { Property } from '../../mocks/properties';

export interface LeafletMapProps {
  properties: Property[];
  selectedPropertyId?: string | null;
  onSelectProperty?: (id: string) => void;
  center?: [number, number];
  zoom?: number;
  className?: string;
}

export const LeafletMap: React.FC<LeafletMapProps> = ({
  properties,
  selectedPropertyId,
  onSelectProperty,
  center = [14.6937, -17.4441], // Default Dakar
  zoom = 12,
  className = 'w-full h-full min-h-[400px]',
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const markersRef = useRef<{ [id: string]: L.Marker }>({});

  useEffect(() => {
    if (!mapContainerRef.current) return;

    // Initialize map if not already done
    if (!mapInstanceRef.current) {
      const map = L.map(mapContainerRef.current, {
        center: center,
        zoom: zoom,
        zoomControl: false,
      });

      L.control.zoom({ position: 'bottomright' }).addTo(map);

      // Add clean OpenStreetMap tiles
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 19,
      }).addTo(map);

      mapInstanceRef.current = map;
    }

    const map = mapInstanceRef.current;

    // Clear existing markers
    Object.values(markersRef.current).forEach((marker) => marker.remove());
    markersRef.current = {};

    // Add new markers for properties
    if (properties.length > 0) {
      const bounds = L.latLngBounds([]);

      properties.forEach((property) => {
        const isSelected = property.id === selectedPropertyId;
        const formattedPrice = (property.price / 1000).toLocaleString('fr-FR') + 'k FCFA';

        // Custom HTML Marker Icon styled with E-Courtier Senegal Design System
        const customIcon = L.divIcon({
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

        const marker = L.marker([property.lat, property.lng], { icon: customIcon }).addTo(map);

        // Bind Popup
        marker.bindPopup(`
          <div class="p-2 max-w-[200px] font-body text-xs">
            <img src="${property.images[0]}" alt="${property.title}" class="w-full h-24 object-cover rounded-lg mb-2" />
            <div class="font-bold text-charcoal-900 line-clamp-1">${property.title}</div>
            <div class="text-terracotta-600 font-extrabold text-sm mt-1">${property.price.toLocaleString('fr-FR')} FCFA${property.pricePeriod || ''}</div>
            <div class="text-charcoal-500 text-[11px] mt-0.5">${property.neighborhood}, ${property.city}</div>
          </div>
        `);

        marker.on('click', () => {
          if (onSelectProperty) onSelectProperty(property.id);
        });

        markersRef.current[property.id] = marker;
        bounds.extend([property.lat, property.lng]);
      });

      // Adjust bounds if multiple properties
      if (properties.length > 1 && !selectedPropertyId) {
        map.fitBounds(bounds, { padding: [40, 40] });
      }
    }

    return () => {
      // Map cleanup on unmount
    };
  }, [properties, selectedPropertyId]);

  // Pan to selected property when selectedPropertyId changes
  useEffect(() => {
    if (selectedPropertyId && mapInstanceRef.current && markersRef.current[selectedPropertyId]) {
      const selectedProperty = properties.find((p) => p.id === selectedPropertyId);
      if (selectedProperty) {
        mapInstanceRef.current.setView([selectedProperty.lat, selectedProperty.lng], 14, {
          animate: true,
        });
        markersRef.current[selectedPropertyId].openPopup();
      }
    }
  }, [selectedPropertyId, properties]);

  return <div ref={mapContainerRef} className={className} />;
};
