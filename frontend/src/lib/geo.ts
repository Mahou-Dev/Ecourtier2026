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
