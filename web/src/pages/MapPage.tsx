import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { MapRef } from 'react-map-gl/maplibre'
import { useFilters, useMapPins, useMapPlaces, useMapRegions, usePublicConfig } from '../api/queries'
import type { components } from '../api/types.gen'
import { useFragmentPlayer } from '../core/audio/useFragmentPlayer'
import { useDesign } from '../core/design/config'
import { applyFilters } from '../core/filters/filterState'
import { useCopy } from '../core/i18n/copy'
import type { AggregationMode, BBox, MapItem } from '../core/map/aggregation'
import { clusterCamera, placeCamera, regionsCamera, ukraineCamera, type CameraMove } from '../core/map/camera'
import { useMapModel } from '../core/map/useMapModel'
import { AppShell } from '../ui/layout/AppShell'
import { BottomSheet } from '../ui/layout/BottomSheet'
import { BackToUkraine } from '../ui/map/BackToUkraine'
import { ClusterBubble, type ClusterItem } from '../ui/map/ClusterBubble'
import { regionFillOpacity } from '../ui/map/fill'
import { MapMarker } from '../ui/map/MapMarker'
import { MapOverlays } from '../ui/map/MapOverlays'
import { MapView, type Viewport } from '../ui/map/MapView'
import {
  capMarkers,
  isUnsplittableStack,
  itemKey,
  largestPlaceNear,
  MAX_DOM_MARKERS,
  nearestPin,
  stackPins,
} from '../ui/map/markers'
import { MusicianPreviewPlaceholder } from '../ui/map/MusicianPreviewPlaceholder'
import { OriginNotice } from '../ui/map/OriginNotice'
import { PinMarker } from '../ui/map/PinMarker'
import { PinStackList } from '../ui/map/PinStackList'
import { PlaceBubble } from '../ui/map/PlaceBubble'
import { RegionBubble } from '../ui/map/RegionBubble'
import { moveCamera, prefersReducedMotion } from '../ui/map/viewport'

type MapPin = components['schemas']['MapPin']
type RegionStat = components['schemas']['RegionStat']

const NO_INSETS = { top: 0, right: 0, bottom: 0, left: 0 }
const WORLD: BBox = [-180, -85, 180, 85]
const NO_MARKERS = { items: [] as MapItem[], capped: false }
/** A mouse plays fragments on hover, so its click only opens the preview; touch and pen tap through the audio core. */
const hoverDevice = () => window.matchMedia?.('(hover: hover) and (pointer: fine)').matches ?? false

export default function MapPage() {
  const t = useCopy()
  const mode = useDesign().variant('map.aggregation') as AggregationMode
  // indexes exist as soon as the server counts do; the pins query may still be loading
  const { indexes, filters, setFilters } = useMapModel()
  const { gesture, previewId: tapPreview, pointerEnter, pointerLeave, state: audio } = useFragmentPlayer()
  const pins = useMapPins().data?.pins
  const places = useMapPlaces().data
  const regions = useMapRegions().data
  const filtersReady = useFilters().isSuccess
  const regionMaxZoom = usePublicConfig().data?.map.regionMaxZoom

  const mapRef = useRef<MapRef>(null)
  const [viewport, setViewport] = useState<Viewport | null>(null)
  const [stack, setStack] = useState<{ title: string; pins: MapPin[] } | null>(null)
  const [mousePreview, setMousePreview] = useState<string | null>(null)

  const level = indexes && viewport ? indexes.level(viewport.zoom) : null
  const { items, capped } = useMemo(
    () => (indexes && viewport ? capMarkers(indexes.items(viewport.bbox, viewport.zoom)) : NO_MARKERS),
    [indexes, viewport],
  )
  useEffect(() => {
    if (capped && import.meta.env.DEV) console.warn(`[map] over ${MAX_DOM_MARKERS} markers in view: showing clusters only`)
  }, [capped])

  const placeName = useMemo(() => new Map(places?.map((p) => [p.placeId, p.name])), [places])
  const pinById = useMemo(() => new Map(pins?.map((p) => [p.id, p])), [pins])
  const visiblePins = useMemo(() => (pins ? applyFilters(pins, filters) : []), [pins, filters])

  const camera = useCallback((move: CameraMove, minZoom?: number) => {
    if (mapRef.current) moveCamera(mapRef.current, move, minZoom)
  }, [])

  /** Fits the regions; a single region opens at least at its place level. */
  const openRegions = useCallback(
    (selected: RegionStat[], animate: boolean) => {
      const move = regionsCamera(selected, NO_INSETS, !animate || prefersReducedMotion())
      camera(move, selected.length === 1 ? regionMaxZoom : undefined)
    },
    [camera, regionMaxZoom],
  )

  // ?region= on load opens the region (D-10), once the map and the filter definitions are there.
  const deepLinkDone = useRef(false)
  useEffect(() => {
    if (deepLinkDone.current || !viewport || !regions || !filtersReady || regionMaxZoom === undefined) return
    deepLinkDone.current = true
    const selected = regions.filter((r) => filters.region?.includes(r.code))
    if (selected.length > 0) openRegions(selected, false)
  }, [viewport, regions, filtersReady, regionMaxZoom, filters.region, openRegions])

  // Choropleth shades every region by its count, also those outside the viewport, hence the whole world.
  const choropleth = mode === 'CHOROPLETH_PLACE_PIN' && level === 'REGION'
  const fillOpacity = useMemo(() => {
    const counts: Record<string, number> = {}
    if (choropleth && indexes) {
      for (const item of indexes.items(WORLD, 0)) if (item.kind === 'region') counts[item.code] = item.count
    }
    return regionFillOpacity(counts, filters.region ?? [], choropleth)
  }, [choropleth, indexes, filters.region])

  const standalonePlaces = useMemo(
    () => new Set(items.flatMap((i) => (i.kind === 'place' ? [i.placeId] : []))),
    [items],
  )
  const clusterName = (item: ClusterItem): string => {
    if (item.kind === 'placeCluster') {
      const zoom = Math.floor(viewport?.zoom ?? 0)
      return largestPlaceNear(places ?? [], item.lon, item.lat, zoom, standalonePlaces)?.name ?? ''
    }
    const near = nearestPin(visiblePins, item.lon, item.lat)
    return (near && placeName.get(near.placeId)) ?? ''
  }

  const previewId = tapPreview ?? mousePreview
  const previewPin = previewId ? pinById.get(previewId) : undefined

  const closeSheets = useCallback(() => {
    setStack(null)
    setMousePreview(null)
    if (tapPreview) gesture({ type: 'closePreview', musicianId: tapPreview })
  }, [gesture, tapPreview])

  const onRegion = (item: Extract<MapItem, { kind: 'region' }>) => {
    const region = regions?.find((r) => r.code === item.code)
    if (region) openRegions([region], true)
  }
  const onPlace = (item: Extract<MapItem, { kind: 'place' }>) => camera(placeCamera(item.lon, item.lat, prefersReducedMotion()))
  const onCluster = (item: ClusterItem) => {
    if (!indexes || !viewport) return
    const expansion = indexes.expansionZoom(item)
    if (isUnsplittableStack(item, expansion, viewport.zoom)) {
      const title = t('cluster.ariaLabel', { name: clusterName(item), count: item.count })
      setStack({ title, pins: stackPins(visiblePins, item.lon, item.lat, item.count) })
      return
    }
    camera(clusterCamera(item.lon, item.lat, expansion, prefersReducedMotion()))
  }
  const onPin = (pin: MapPin) => {
    setStack(null)
    if (hoverDevice()) setMousePreview(pin.id)
    else gesture({ type: 'tap', musicianId: pin.id })
  }
  const backToUkraine = () => {
    closeSheets()
    if (filters.region?.length) setFilters({ ...filters, region: [] })
    camera(ukraineCamera(NO_INSETS, prefersReducedMotion()))
  }
  const showBack = indexes !== null && level !== null && (level !== indexes.level(0) || Boolean(filters.region?.length))

  const marker = (item: MapItem) => {
    switch (item.kind) {
      case 'region':
        return <RegionBubble item={item} choropleth={mode === 'CHOROPLETH_PLACE_PIN'} onSelect={onRegion} />
      case 'place':
        return <PlaceBubble item={item} onSelect={onPlace} />
      case 'placeCluster':
      case 'pinCluster':
        return <ClusterBubble item={item} name={clusterName(item)} showName={item.kind === 'placeCluster'} onSelect={onCluster} />
      case 'pin':
        return (
          <PinMarker
            pin={item.pin}
            placeName={placeName.get(item.pin.placeId) ?? ''}
            playing={audio.status === 'playing' && audio.musicianId === item.pin.id}
            selected={previewId === item.pin.id}
            onActivate={onPin}
            onPointerEnter={pointerEnter}
            onPointerLeave={pointerLeave}
          />
        )
    }
  }

  return (
    <AppShell>
      <div className="n-map-screen">
        <div className="n-map-stage">
          <MapView ref={mapRef} level={level} onViewport={setViewport} onBackgroundClick={closeSheets}>
            <MapOverlays fillOpacity={fillOpacity} />
            {items.map((item) => {
              const { lon, lat } = item.kind === 'pin' ? item.pin : item
              const raised = item.kind === 'pin' && item.pin.id === previewId
              return (
                <MapMarker key={itemKey(item)} lon={lon} lat={lat} raised={raised}>
                  {marker(item)}
                </MapMarker>
              )
            })}
          </MapView>
          {showBack && <BackToUkraine onClick={backToUkraine} />}
          {previewPin ? (
            <BottomSheet testId="musician-preview" label={previewPin.name} onClose={closeSheets}>
              <MusicianPreviewPlaceholder name={previewPin.name} />
            </BottomSheet>
          ) : (
            stack && (
              <BottomSheet label={stack.title} onClose={closeSheets}>
                <PinStackList title={stack.title} pins={stack.pins} onSelect={onPin} />
              </BottomSheet>
            )
          )}
        </div>
        <OriginNotice />
      </div>
    </AppShell>
  )
}
