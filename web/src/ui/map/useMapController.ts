import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { MapRef } from 'react-map-gl/maplibre'
import { useFilters, useMapPins, useMapPlaces, useMapRegions, usePublicConfig } from '../../api/queries'
import type { components } from '../../api/types.gen'
import { useFragmentPlayer } from '../../core/audio/useFragmentPlayer'
import { useDesign } from '../../core/design/config'
import { activeFilterCount, applyFilters } from '../../core/filters/filterState'
import { useCopy } from '../../core/i18n/copy'
import type { AggregationMode, BBox, MapItem } from '../../core/map/aggregation'
import { clusterCamera, placeCamera, regionsCamera, ukraineCamera, type CameraMove } from '../../core/map/camera'
import { regionAt } from '../../core/map/geo'
import { useMapModel } from '../../core/map/useMapModel'
import { useToast } from '../layout/Toast'
import { regionFillOpacity } from './fill'
import type { MarkerHandlers } from './MapMarkers'
import type { Viewport } from './MapView'
import { capMarkers, clusterNames, isUnsplittableStack, itemKey, MAX_DOM_MARKERS, stackPins } from './markers'
import { createPointIndex } from './pointIndex'
import { readMuted, writeMuted } from './soundPref'
import { moveCamera, prefersReducedMotion } from './viewport'

type MapPin = components['schemas']['MapPin']
type RegionStat = components['schemas']['RegionStat']

export interface StackSheet { title: string; pins: MapPin[] }

export interface MapHandlers extends MarkerHandlers {
  onViewport(viewport: Viewport): void
  /** Fits the regions; a single region opens at least at its place level. */
  openRegions(regions: RegionStat[], animate: boolean): void
  closeSheets(): void
  backToUkraine(): void
  /** Writes the region filter (?region=) and moves the camera to the chosen regions, or back to all of Ukraine. */
  applyRegions(codes: string[]): void
  /** Position → region → camera. Does not write ?region. A refusal or a position outside Ukraine shows a toast. */
  nearMe(): void
  toggleSound(): void
}

const NO_INSETS = { top: 0, right: 0, bottom: 0, left: 0 }
const WORLD: BBox = [-180, -85, 180, 85]
const NO_MARKERS = { items: [] as MapItem[], capped: false }

/**
 * State and actions of the map screen: what to draw (level, markers, names, fill, sheets) and stable handlers for
 * camera moves, pins and sheets. The handlers never change identity, so memoized markers do not re-render; they
 * read the latest state from a ref.
 */
export function useMapController() {
  const t = useCopy()
  const mode = useDesign().variant('map.aggregation') as AggregationMode
  // indexes exist as soon as the server counts do; the pins query may still be loading
  const { indexes, filters, setFilters } = useMapModel()
  const { state: audio, previewId: tapPreview, gesture, pointerEnter, pointerLeave, stop } = useFragmentPlayer()
  const toast = useToast()
  const pins = useMapPins().data?.pins
  const places = useMapPlaces().data
  const regions = useMapRegions().data
  const filtersQuery = useFilters()
  const filtersReady = filtersQuery.isSuccess
  const filterDefs = filtersQuery.data
  const regionMaxZoom = usePublicConfig().data?.map.regionMaxZoom

  const mapRef = useRef<MapRef>(null)
  const [viewport, setViewport] = useState<Viewport | null>(null)
  const [stack, setStack] = useState<StackSheet | null>(null)
  const [mousePreview, setMousePreview] = useState<string | null>(null)
  const [muted, setMuted] = useState(readMuted)

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
  const placeIndex = useMemo(() => createPointIndex(places ?? []), [places])
  const pinIndex = useMemo(() => createPointIndex(visiblePins), [visiblePins])
  // clusters are built at the integer zoom, so their names are too
  const clusterZoom = Math.floor(viewport?.zoom ?? 0)
  const names = useMemo(
    () => clusterNames(items, placeIndex, pinIndex, placeName, clusterZoom),
    [items, placeIndex, pinIndex, placeName, clusterZoom],
  )

  // Choropleth shades every region by its count, also those outside the viewport, hence the whole world.
  const choropleth = mode === 'CHOROPLETH_PLACE_PIN' && level === 'REGION'
  const fillOpacity = useMemo(() => {
    const counts: Record<string, number> = {}
    if (choropleth && indexes) {
      for (const item of indexes.items(WORLD, 0)) if (item.kind === 'region') counts[item.code] = item.count
    }
    return regionFillOpacity(counts, filters.region ?? [], choropleth)
  }, [choropleth, indexes, filters.region])

  const previewId = mousePreview ?? tapPreview
  const playingId = audio.status === 'playing' ? audio.musicianId : null

  const latest = useRef({ indexes, viewport, regions, filters, setFilters, regionMaxZoom, names, visiblePins, t, tapPreview, gesture, pointerEnter, pointerLeave, stop, muted, blocked: false, notify: toast.show })
  useLayoutEffect(() => {
    latest.current = { indexes, viewport, regions, filters, setFilters, regionMaxZoom, names, visiblePins, t, tapPreview, gesture, pointerEnter, pointerLeave, stop, muted, blocked: audio.status === 'blocked', notify: toast.show }
  })

  const handlers = useMemo<MapHandlers>(() => {
    const now = () => latest.current
    const camera = (move: CameraMove, minZoom?: number) => {
      if (mapRef.current) moveCamera(mapRef.current, move, minZoom)
    }
    const openRegions = (selected: RegionStat[], animate: boolean) => {
      const move = regionsCamera(selected, NO_INSETS, !animate || prefersReducedMotion())
      camera(move, selected.length === 1 ? now().regionMaxZoom : undefined)
    }
    const closeSheets = () => {
      setStack(null)
      setMousePreview(null)
      const { tapPreview: open, gesture: send } = now()
      if (open) send({ type: 'closePreview', musicianId: open })
    }
    return {
      onViewport: setViewport,
      openRegions,
      closeSheets,
      onRegion(item) {
        const region = now().regions?.find((r) => r.code === item.code)
        if (region) openRegions([region], true)
      },
      onPlace(item) {
        camera(placeCamera(item.lon, item.lat, prefersReducedMotion()))
      },
      onCluster(item) {
        const { indexes: idx, viewport: view, names: named, visiblePins: visible, t: copy } = now()
        if (!idx || !view) return
        const expansion = idx.expansionZoom(item)
        if (isUnsplittableStack(item, expansion, view.zoom)) {
          const title = copy('cluster.ariaLabel', { name: named.get(itemKey(item)) ?? '', count: item.count })
          setStack({ title, pins: stackPins(visible, item.lon, item.lat, item.count) })
          return
        }
        camera(clusterCamera(item.lon, item.lat, expansion, prefersReducedMotion()))
      },
      onPin(pin, input) {
        setStack(null)
        if (input === 'mouse') {
          // a mouse already plays the fragment on hover; its click only opens the preview
          setMousePreview(pin.id)
          return
        }
        if (now().muted) {
          setMousePreview(pin.id)
          return
        }
        setMousePreview(null)
        now().gesture({ type: 'tap', musicianId: pin.id })
      },
      onHoverStart(id) {
        if (!now().muted) now().pointerEnter(id)
      },
      onHoverEnd: (id) => now().pointerLeave(id),
      backToUkraine() {
        closeSheets()
        const { filters: current, setFilters: write } = now()
        if (current.region?.length) write({ ...current, region: [] })
        camera(ukraineCamera(NO_INSETS, prefersReducedMotion()))
      },
      applyRegions(codes) {
        const { filters: current, setFilters: write, regions: all } = now()
        write({ ...current, region: codes })
        const selected = all?.filter((r) => codes.includes(r.code)) ?? []
        if (selected.length > 0) openRegions(selected, true)
        else camera(ukraineCamera(NO_INSETS, prefersReducedMotion()))
      },
      nearMe() {
        const refuse = () => now().notify(now().t('map.nearMeDenied'))
        if (!navigator.geolocation) {
          refuse()
          return
        }
        navigator.geolocation.getCurrentPosition(
          ({ coords }) => {
            const code = regionAt(coords.longitude, coords.latitude)
            const region = code ? now().regions?.find((r) => r.code === code) : undefined
            if (region) openRegions([region], true)
            else refuse()
          },
          refuse,
          { timeout: 10_000, maximumAge: 60_000 },
        )
      },
      toggleSound() {
        const { muted: wasMuted, blocked, stop: stopAudio } = now()
        if (blocked && !wasMuted) {
          // the press itself is the gesture that lets the browser play; clear the blocked state
          stopAudio()
          return
        }
        setMuted(!wasMuted)
        writeMuted(!wasMuted)
        if (!wasMuted) stopAudio()
      },
    }
  }, [])

  // ?region= on load opens the region (D-10), once the map and the filter definitions are there.
  const deepLinkDone = useRef(false)
  useEffect(() => {
    if (deepLinkDone.current || !viewport || !regions || !filtersReady || regionMaxZoom === undefined) return
    deepLinkDone.current = true
    const selected = regions.filter((r) => filters.region?.includes(r.code))
    if (selected.length > 0) handlers.openRegions(selected, false)
  }, [viewport, regions, filtersReady, regionMaxZoom, filters.region, handlers])

  return {
    mapRef,
    level,
    items,
    names,
    placeName,
    numeralRegions: mode === 'CHOROPLETH_PLACE_PIN',
    fillOpacity,
    muted,
    blocked: audio.status === 'blocked',
    toast: toast.message,
    /** Musicians matching the filters; null until the pins have loaded. */
    resultsCount: pins ? visiblePins.length : null,
    activeFilterCount: activeFilterCount(filters),
    selectedRegions: filters.region ?? [],
    regionOptions: filterDefs?.find((d) => d.key === 'region')?.options,
    showBack: indexes !== null && level !== null && (level !== indexes.level(0) || Boolean(filters.region?.length)),
    previewPin: previewId ? pinById.get(previewId) : undefined,
    previewId,
    playingId,
    stack,
    handlers,
  }
}
