import { useCallback, useMemo, useRef, useState, useEffect } from 'react';
import Map, { Marker, NavigationControl, type MapRef } from 'react-map-gl/maplibre';
import 'maplibre-gl/dist/maplibre-gl.css';
import { config } from '@/lib/config';
import type { components } from '@/lib/api/generated/types';
import { groupPinsByLocation } from '@/lib/utils/groupPinsByLocation';
import { StackMarker } from './StackMarker';
import { ClusterMarker } from './ClusterMarker';
import { createPinIndex, getExpansionZoom, getMapItems } from '../utils/clusterPins';
import { regionsBounds } from '../utils/ukraineRegions';
import { applyAeroMapPalette, applyUkrainianLabels, onStyleReady } from '../utils/aeroMapStyle';

type MapPinDTO = components['schemas']['MapPinDTO'];

/** A point to fly to; a new `key` flies again, even to the same point. */
export interface MapFocus {
  longitude: number;
  latitude: number;
  key: string;
}

/** Pixels along each edge of the map hidden by floating UI (the filter panel or bar). */
export interface MapInsets {
  top?: number;
  right?: number;
  bottom?: number;
  left?: number;
}

interface MapContainerProps {
  pins: MapPinDTO[];
  onPinClick?: (pin: MapPinDTO) => void;
  /** Region filter: the map fits the chosen regions and returns to all of Ukraine when they are cleared. */
  regions?: string[];
  focus?: MapFocus | null;
  insets?: MapInsets;
}

// Ukraine center coordinates
const INITIAL_VIEW_STATE = {
  latitude: 48.3794,
  longitude: 31.1656,
  zoom: 6,
};

// Ukraine geographic boundaries (approximate)
// Southwest: [22.1371, 44.3614], Northeast: [40.2275, 52.3791]
const UKRAINE_BOUNDS = {
  minLng: 22.1371,
  minLat: 44.3614,
  maxLng: 40.2275,
  maxLat: 52.3791,
};

export function MapContainer({ pins, onPinClick, regions, focus, insets }: MapContainerProps) {
  const mapRef = useRef<MapRef | null>(null);
  // The map instance arrives after the first commit, so it is held in state and the effects below wait for it.
  const [mapInstance, setMapInstance] = useState<ReturnType<MapRef['getMap']> | null>(null);
  const setMapRef = useCallback((ref: MapRef | null) => {
    mapRef.current = ref;
    setMapInstance(ref ? ref.getMap() : null);
  }, []);
  const [isMobile, setIsMobile] = useState(false);
  const [hoveredGroupKey, setHoveredGroupKey] = useState<string | null>(null);
  const [isMapReady, setIsMapReady] = useState(false);

  // The visible area, read when the map stops moving; only bubbles and pins inside it are rendered.
  const [visibleArea, setVisibleArea] = useState<{ bbox: [number, number, number, number]; zoom: number }>({
    bbox: [UKRAINE_BOUNDS.minLng, UKRAINE_BOUNDS.minLat, UKRAINE_BOUNDS.maxLng, UKRAINE_BOUNDS.maxLat],
    zoom: INITIAL_VIEW_STATE.zoom,
  });
  const readVisibleArea = useCallback(() => {
    const map = mapRef.current?.getMap();
    if (!map) return;
    const bounds = map.getBounds();
    setVisibleArea({
      bbox: [bounds.getWest(), bounds.getSouth(), bounds.getEast(), bounds.getNorth()],
      zoom: map.getZoom(),
    });
  }, []);

  const handleMapLoad = () => {
    setIsMapReady(true);
    readVisibleArea();
  };

  // Paint the style in the Aero palette and switch its labels to Ukrainian as soon as the style is parsed,
  // before the tiles arrive, so the grey English base map never shows.
  useEffect(() => {
    if (!mapInstance) return;
    return onStyleReady(mapInstance, () => {
      applyAeroMapPalette(mapInstance);
      applyUkrainianLabels(mapInstance);
    });
  }, [mapInstance]);

  // The covered edges, kept in a ref so a new panel width does not move the camera by itself.
  const insetsRef = useRef<MapInsets | undefined>(insets);
  useEffect(() => {
    insetsRef.current = insets;
  }, [insets]);

  // Fit the chosen regions; when the last one is cleared, go back to all of Ukraine. Nothing moves on the first load.
  const regionsKey = (regions ?? []).join(',');
  const fittedRegionsKey = useRef('');
  useEffect(() => {
    if (!isMapReady) return;
    const map = mapRef.current?.getMap();
    if (!map || regionsKey === fittedRegionsKey.current) return;
    const previous = fittedRegionsKey.current;
    fittedRegionsKey.current = regionsKey;

    const edge = insetsRef.current ?? {};
    const bounds = regionsKey ? regionsBounds(regionsKey.split(',')) : null;
    if (bounds) {
      map.fitBounds(bounds, {
        padding: {
          top: 40 + (edge.top ?? 0),
          right: 40 + (edge.right ?? 0),
          bottom: 40 + (edge.bottom ?? 0),
          left: 40 + (edge.left ?? 0),
        },
        duration: 1000,
      });
    } else if (!regionsKey && previous) {
      map.flyTo({
        center: [INITIAL_VIEW_STATE.longitude, INITIAL_VIEW_STATE.latitude],
        zoom: INITIAL_VIEW_STATE.zoom,
        duration: 1000,
      });
    }
  }, [regionsKey, isMapReady]);

  // Fly to a focused pin once per key. A focus always opens the pin card (PinPreviewDrawer): below 768 px it covers
  // about the lower half of the map, from 768 px a 420 px column on the right. The pin lands in the middle of what the
  // card and the filter panel leave open.
  const flownFocusKey = useRef<string | null>(null);
  useEffect(() => {
    if (!isMapReady || !focus || flownFocusKey.current === focus.key) return;
    const map = mapRef.current?.getMap();
    if (!map) return;
    flownFocusKey.current = focus.key;
    const edge = insetsRef.current ?? {};
    const narrow = window.innerWidth < 768;
    const left = edge.left ?? 0;
    const right = narrow ? 0 : Math.max(edge.right ?? 0, 444);
    const top = edge.top ?? 0;
    const bottom = narrow ? Math.max(edge.bottom ?? 0, map.getContainer().clientHeight / 2) : edge.bottom ?? 0;
    map.flyTo({
      center: [focus.longitude, focus.latitude],
      zoom: 12,
      duration: 800,
      offset: [(left - right) / 2, (top - bottom) / 2],
    });
  }, [focus, isMapReady]);

  // Detect mobile screen size
  useEffect(() => {
    const checkMobile = () => setIsMobile(window.innerWidth < 768);
    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  // Pins at the very same point form one stack; nearby stacks merge into bubbles on the zoomed-out map.
  const pinIndex = useMemo(() => createPinIndex(groupPinsByLocation(pins)), [pins]);
  const mapItems = useMemo(
    () => getMapItems(pinIndex, visibleArea.bbox, visibleArea.zoom),
    [pinIndex, visibleArea],
  );

  const zoomIntoCluster = (clusterId: number, latitude: number, longitude: number) => {
    mapRef.current?.getMap().easeTo({
      center: [longitude, latitude],
      zoom: Math.min(getExpansionZoom(pinIndex, clusterId), 18),
      duration: 600,
    });
  };

  // One light style; aeroMapStyle.ts recolours it and switches its labels to Ukrainian.
  const mapStyle = `https://api.maptiler.com/maps/dataviz-light/style.json?key=${config.mapTilerApiKey}`;

  return (
    <div className="nashi-map h-full w-full">
      <Map
        initialViewState={INITIAL_VIEW_STATE}
        onLoad={handleMapLoad}
        onMoveEnd={readVisibleArea}
        mapStyle={mapStyle}
        style={{ width: '100%', height: '100%' }}
        ref={setMapRef}
        maxBounds={[
          [UKRAINE_BOUNDS.minLng, UKRAINE_BOUNDS.minLat], // Southwest
          [UKRAINE_BOUNDS.maxLng, UKRAINE_BOUNDS.maxLat], // Northeast
        ]}
        minZoom={5.5}
        maxZoom={18}
      >
        <NavigationControl position="top-right" />

        {mapItems.map((item) => {
          if (item.kind === 'cluster') {
            return (
              <Marker key={`cluster-${item.id}`} latitude={item.latitude} longitude={item.longitude} anchor="center">
                <ClusterMarker
                  count={item.count}
                  onClick={() => zoomIntoCluster(item.id, item.latitude, item.longitude)}
                />
              </Marker>
            );
          }
          const group = item.group;
          const groupKey = `group-${group.latitude}-${group.longitude}`;
          const isHovered = hoveredGroupKey === groupKey;
          
          return (
            <Marker
              key={groupKey}
              latitude={group.latitude}
              longitude={group.longitude}
              anchor="bottom"
              style={{
                zIndex: isHovered ? 1000 : 1,
              }}
            >
              <StackMarker
                pins={group.pins}
                onPinClick={(pin) => onPinClick?.(pin)}
                isMobile={isMobile}
                onHoverChange={(hovered) => 
                  setHoveredGroupKey(hovered ? groupKey : null)
                }
              />
            </Marker>
          );
        })}
      </Map>
    </div>
  );
}
