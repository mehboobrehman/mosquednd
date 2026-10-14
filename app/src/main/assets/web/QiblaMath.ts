export interface LatLng {
  lat: number;
  lng: number;
}

export const KAABA_COORDS: LatLng = {
  lat: 21.4225,
  lng: 39.8262,
};

/**
 * Converts degrees to radians.
 */
export function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/**
 * Converts radians to degrees.
 */
export function toDeg(rad: number): number {
  return (rad * 180) / Math.PI;
}

/**
 * Calculates the Great Circle Bearing from user location to Kaaba (Mecca).
 * Formula:
 * Delta lambda = lambda_Kaaba - lambda_user
 * X = cos(phi_Kaaba) * sin(Delta lambda)
 * Y = cos(phi_user) * sin(phi_Kaaba) - sin(phi_user) * cos(phi_Kaaba) * cos(Delta lambda)
 * Bearing = atan2(X, Y) normalized to [0, 360)
 */
export function calculateQiblaBearing(userLat: number, userLng: number): number {
  const phiUser = toRad(userLat);
  const phiKaaba = toRad(KAABA_COORDS.lat);
  const deltaLambda = toRad(KAABA_COORDS.lng - userLng);

  const x = Math.cos(phiKaaba) * Math.sin(deltaLambda);
  const y =
    Math.cos(phiUser) * Math.sin(phiKaaba) -
    Math.sin(phiUser) * Math.cos(phiKaaba) * Math.cos(deltaLambda);

  let bearingRad = Math.atan2(x, y);
  let bearingDeg = toDeg(bearingRad);

  // Normalize to 0 - 360 degrees
  return (bearingDeg + 360) % 360;
}

/**
 * Calculates Great Circle distance in kilometers between two coordinates using Haversine formula.
 */
export function calculateDistanceKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371; // Earth radius in km
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export interface CityPreset {
  name: string;
  country: string;
  coords: LatLng;
}

export const FALLBACK_CITIES: Record<string, CityPreset> = {
  london: { name: "London", country: "United Kingdom", coords: { lat: 51.5074, lng: -0.1278 } },
  karachi: { name: "Karachi", country: "Pakistan", coords: { lat: 24.8607, lng: 67.0011 } },
  newyork: { name: "New York", country: "United States", coords: { lat: 40.7128, lng: -74.0060 } },
  makkah: { name: "Makkah", country: "Saudi Arabia", coords: { lat: 21.4225, lng: 39.8262 } },
  istanbul: { name: "Istanbul", country: "Turkey", coords: { lat: 41.0082, lng: 28.9784 } },
  dubai: { name: "Dubai", country: "UAE", coords: { lat: 25.2048, lng: 55.2708 } },
};
