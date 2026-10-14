import { useState, useEffect, useCallback, useRef } from 'react';
import { LatLng, calculateQiblaBearing, FALLBACK_CITIES } from './QiblaMath';

export interface CompassSensorState {
  heading: number; // 0-360 degrees (device heading relative to North)
  qiblaBearing: number; // Qibla bearing relative to North
  relativeQiblaAngle: number; // Offset angle to rotate compass dial so Qibla points top (-heading + qiblaBearing)
  location: LatLng;
  locationName: string;
  isSimulated: boolean;
  accuracy: number | null; // compass accuracy or GPS accuracy in meters
  permissionStatus: 'granted' | 'denied' | 'prompt' | 'unsupported';
  error: string | null;
  isLoading: boolean;
}

export function useCompassSensor(defaultCityKey: string = 'karachi') {
  const [state, setState] = useState<CompassSensorState>({
    heading: 0,
    qiblaBearing: 0,
    relativeQiblaAngle: 0,
    location: FALLBACK_CITIES[defaultCityKey].coords,
    locationName: FALLBACK_CITIES[defaultCityKey].name,
    isSimulated: false,
    accuracy: null,
    permissionStatus: 'prompt',
    error: null,
    isLoading: true,
  });

  const headingRef = useRef(0);
  const animationFrameRef = useRef<number | null>(null);

  // Update Qibla bearing whenever location changes
  const updateLocation = useCallback((coords: LatLng, name: string, simulated: boolean = false) => {
    const bearing = calculateQiblaBearing(coords.lat, coords.lng);
    setState((prev) => {
      const relative = (bearing - prev.heading + 360) % 360;
      return {
        ...prev,
        location: coords,
        locationName: name,
        qiblaBearing: bearing,
        relativeQiblaAngle: relative,
        isSimulated: simulated,
        isLoading: false,
        error: null,
      };
    });
  }, []);

  // Initialize Geolocation
  useEffect(() => {
    if (!navigator.geolocation) {
      // Fallback immediately
      const city = FALLBACK_CITIES[defaultCityKey] || FALLBACK_CITIES['karachi'];
      updateLocation(city.coords, `${city.name} (GPS Unsupported)`, true);
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const coords = { lat: position.coords.latitude, lng: position.coords.longitude };
        updateLocation(coords, `Current Location`, false);
      },
      (err) => {
        console.warn('Geolocation error or denied:', err.message);
        const city = FALLBACK_CITIES[defaultCityKey] || FALLBACK_CITIES['karachi'];
        updateLocation(coords => city.coords, `${city.name} (Fallback Mode)`, true);
        setState((prev) => ({
          ...prev,
          error: `GPS unavailable (${err.message}). Using ${city.name} fallback.`,
          permissionStatus: 'denied',
        }));
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 }
    );
  }, [defaultCityKey, updateLocation]);

  // Handle DeviceOrientation & iOS permission request
  const requestCompassPermission = useCallback(async () => {
    if (typeof window === 'undefined') return;

    // Check if DeviceOrientationEvent requires permission (iOS 13+)
    if (
      typeof (DeviceOrientationEvent as any).requestPermission === 'function'
    ) {
      try {
        const response = await (DeviceOrientationEvent as any).requestPermission();
        if (response === 'granted') {
          setState((prev) => ({ ...prev, permissionStatus: 'granted', error: null }));
          setupOrientationListener();
        } else {
          setState((prev) => ({
            ...prev,
            permissionStatus: 'denied',
            error: 'Compass permission denied. Using simulation mode.',
            isSimulated: true,
          }));
        }
      } catch (err: any) {
        setState((prev) => ({
          ...prev,
          permissionStatus: 'denied',
          error: `Compass permission error: ${err.message}`,
          isSimulated: true,
        }));
      }
    } else {
      // Android / Desktop / Browsers without explicit prompt
      setState((prev) => ({ ...prev, permissionStatus: 'granted' }));
      setupOrientationListener();
    }
  }, []);

  const setupOrientationListener = useCallback(() => {
    const handleOrientation = (event: DeviceOrientationEvent) => {
      let compassHeading: number | null = null;

      // iOS alpha or absolute compass heading
      if ('webkitCompassHeading' in event) {
        compassHeading = (event as any).webkitCompassHeading;
      } else if (event.alpha !== null) {
        // Standard Android/W3C alpha (counter-clockwise from north usually, converted)
        compassHeading = event.absolute ? event.alpha : (360 - event.alpha) % 360;
      }

      if (compassHeading !== null && !isNaN(compassHeading)) {
        // Smooth heading update
        const currentHeading = headingRef.current;
        // Handle 360 wrap around for smooth lerp
        let diff = compassHeading - currentHeading;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;
        const smoothed = (currentHeading + diff * 0.2 + 360) % 360;
        headingRef.current = smoothed;

        setState((prev) => ({
          ...prev,
          heading: smoothed,
          relativeQiblaAngle: (prev.qiblaBearing - smoothed + 360) % 360,
          isSimulated: false,
        }));
      }
    };

    window.addEventListener('deviceorientation', handleOrientation, true);
    return () => {
      window.removeEventListener('deviceorientation', handleOrientation, true);
    };
  }, []);

  useEffect(() => {
    const cleanup = setupOrientationListener();
    return cleanup;
  }, [setupOrientationListener]);

  // Manual simulation heading adjustment (for desktop/testing)
  const setSimulatedHeading = useCallback((simulatedHeading: number) => {
    const normalized = (simulatedHeading + 360) % 360;
    headingRef.current = normalized;
    setState((prev) => ({
      ...prev,
      heading: normalized,
      relativeQiblaAngle: (prev.qiblaBearing - normalized + 360) % 360,
      isSimulated: true,
    }));
  }, []);

  // Switch fallback city manually
  const selectFallbackCity = useCallback((cityKey: string) => {
    const city = FALLBACK_CITIES[cityKey];
    if (city) {
      updateLocation(city.coords, city.name, true);
    }
  }, [updateLocation]);

  return {
    state,
    requestCompassPermission,
    setSimulatedHeading,
    selectFallbackCity,
  };
}
