import React, { useEffect, useRef, useState } from 'react';
import { MapContainer, TileLayer, Marker, useMapEvents, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Button } from '../ui/Button';
import { MapPin, Navigation, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

// Custom default Leaflet icon fix
const defaultMarkerIcon = L.icon({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

interface LocationPickerMapProps {
  latitude: number;
  longitude: number;
  onChange: (lat: number, lng: number) => void;
  className?: string;
}

// Senegal bounding box
export const SENEGAL_BOUNDS = {
  minLat: 12.0,
  maxLat: 17.0,
  minLng: -17.8,
  maxLng: -11.0,
};

export const isInsideSenegal = (lat: number, lng: number): boolean => {
  return (
    lat >= SENEGAL_BOUNDS.minLat &&
    lat <= SENEGAL_BOUNDS.maxLat &&
    lng >= SENEGAL_BOUNDS.minLng &&
    lng <= SENEGAL_BOUNDS.maxLng
  );
};

// Map sub-component to handle map clicks & resize invalidation
const MapEventsHandler: React.FC<{
  position: [number, number];
  onPositionChange: (lat: number, lng: number) => void;
}> = ({ position, onPositionChange }) => {
  const map = useMap();

  useEffect(() => {
    // Invalidate map size to prevent gray tiles in modals
    const timer = setTimeout(() => {
      map.invalidateSize();
    }, 250);
    return () => clearTimeout(timer);
  }, [map]);

  useEffect(() => {
    map.panTo(position);
  }, [map, position]);

  useMapEvents({
    click(e) {
      onPositionChange(Number(e.latlng.lat.toFixed(6)), Number(e.latlng.lng.toFixed(6)));
    },
  });

  return null;
};

export const LocationPickerMap: React.FC<LocationPickerMapProps> = ({
  latitude,
  longitude,
  onChange,
  className = 'h-64',
}) => {
  const [isLocating, setIsLocating] = useState(false);
  const markerRef = useRef<L.Marker | null>(null);

  const currentPos: [number, number] = [
    latitude || 14.7167,
    longitude || -17.4677,
  ];

  const inSenegal = isInsideSenegal(currentPos[0], currentPos[1]);

  const handleUseCurrentLocation = () => {
    if (!navigator.geolocation) {
      toast.error('La géolocalisation n’est pas supportée par votre navigateur');
      return;
    }

    setIsLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setIsLocating(false);
        const lat = Number(pos.coords.latitude.toFixed(6));
        const lng = Number(pos.coords.longitude.toFixed(6));

        if (!isInsideSenegal(lat, lng)) {
          toast.warning(
            `Votre position actuelle (${lat}, ${lng}) est hors du Sénégal. Positionnez le marqueur au Sénégal.`
          );
        } else {
          toast.success('Position GPS récupérée avec succès');
        }
        onChange(lat, lng);
      },
      (err) => {
        setIsLocating(false);
        toast.error(`Impossible de récupérer votre position : ${err.message}`);
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  const handleMarkerDragEnd = () => {
    const marker = markerRef.current;
    if (marker) {
      const latLng = marker.getLatLng();
      const lat = Number(latLng.lat.toFixed(6));
      const lng = Number(latLng.lng.toFixed(6));
      onChange(lat, lng);
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <label className="text-xs font-bold text-sahel-800 flex items-center gap-1.5">
          <MapPin className="w-3.5 h-3.5 text-terracotta-500" />
          Localisation sur la carte (cliquable & marqueur déplaçable)
        </label>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={handleUseCurrentLocation}
          isLoading={isLocating}
          leftIcon={<Navigation className="w-3 h-3 text-terracotta-500" />}
          className="text-xs py-1 px-2.5"
        >
          Utiliser ma position
        </Button>
      </div>

      <div className={`w-full rounded-xl overflow-hidden border border-sand-300 relative z-0 ${className}`}>
        <MapContainer
          center={currentPos}
          zoom={13}
          scrollWheelZoom={false}
          className="w-full h-full z-0"
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <MapEventsHandler position={currentPos} onPositionChange={onChange} />
          <Marker
            position={currentPos}
            draggable={true}
            ref={markerRef}
            icon={defaultMarkerIcon}
            eventHandlers={{
              dragend: handleMarkerDragEnd,
            }}
          />
        </MapContainer>
      </div>

      {/* Lat/Lon read-only inputs */}
      <div className="grid grid-cols-2 gap-3 pt-1">
        <div>
          <label className="block text-[11px] font-semibold text-charcoal-600 mb-0.5">
            Latitude (lecture seule)
          </label>
          <input
            type="text"
            readOnly
            value={latitude !== undefined ? latitude : ''}
            className="w-full px-3 py-1.5 bg-sand-100 border border-sand-300 rounded-lg text-xs font-mono text-charcoal-700 cursor-not-allowed select-all"
          />
        </div>
        <div>
          <label className="block text-[11px] font-semibold text-charcoal-600 mb-0.5">
            Longitude (lecture seule)
          </label>
          <input
            type="text"
            readOnly
            value={longitude !== undefined ? longitude : ''}
            className="w-full px-3 py-1.5 bg-sand-100 border border-sand-300 rounded-lg text-xs font-mono text-charcoal-700 cursor-not-allowed select-all"
          />
        </div>
      </div>

      {!inSenegal && (
        <div className="p-2.5 rounded-lg bg-red-50 border border-red-200 text-red-700 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0 text-red-500" />
          <span>
            Attention : Les coordonnées sélectionnées sont en dehors du territoire sénégalais (lat: 12.0 à 17.0, lon: -17.8 à -11.0).
          </span>
        </div>
      )}
    </div>
  );
};
