import 'maplibre-gl/dist/maplibre-gl.css'
import { useLayoutEffect, useMemo, useRef, useState, type ReactNode, type Ref, type RefObject } from 'react'
import Map, { type MapInstance, type MapLayerMouseEvent, type MapRef } from 'react-map-gl/maplibre'
import type { BBox, MapLevel } from '../../core/map/aggregation'
import { ukraineCamera, type CameraMove } from '../../core/map/camera'
import { baseMapStyle } from './baseStyle'
import { mapLimits, MAX_ZOOM, prefersReducedMotion, zoomBy, type Size } from './viewport'
import { ZoomButtons } from './ZoomButtons'
import './map.css'

export interface Viewport { zoom: number; bbox: BBox }

const NO_INSETS = { top: 0, right: 0, bottom: 0, left: 0 }

function overviewCamera(): Extract<CameraMove, { type: 'fitBounds' }> {
  const move = ukraineCamera(NO_INSETS, true)
  if (move.type !== 'fitBounds') throw new Error('ukraineCamera must fit bounds')
  return move
}

function useElementSize(ref: RefObject<HTMLElement | null>): Size | null {
  const [size, setSize] = useState<Size | null>(null)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const measure = () => {
      const width = Math.round(el.clientWidth)
      const height = Math.round(el.clientHeight)
      if (!width || !height) return
      setSize((prev) => (prev?.width === width && prev.height === height ? prev : { width, height }))
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(el)
    return () => observer.disconnect()
  }, [ref])
  return size
}

/**
 * MapLibre map with an uncontrolled camera that opens on the whole of Ukraine, with +/− zoom buttons. Reports the
 * viewport after load and after every move; `level` and the zoom are exposed as `data-level` / `data-zoom` for tests
 * and the skin.
 */
export function MapView({ ref, level, onViewport, onBackgroundClick, children }: {
  ref?: Ref<MapRef>
  level: MapLevel | null
  onViewport(viewport: Viewport): void
  onBackgroundClick(): void
  children?: ReactNode
}) {
  const box = useRef<HTMLDivElement>(null)
  // The size must be known before the map mounts: the minimum zoom depends on it.
  const size = useElementSize(box)
  const mapStyle = useMemo(() => baseMapStyle(import.meta.env as Record<string, string | undefined>), [])
  const overview = useMemo(overviewCamera, [])
  const limits = useMemo(() => (size ? mapLimits(size, overview.padding) : null), [size, overview])
  const [map, setMap] = useState<MapInstance | null>(null)
  const [zoom, setZoom] = useState<number | null>(null)

  const report = ({ target }: { target: MapInstance }) => {
    const b = target.getBounds()
    setZoom(target.getZoom())
    onViewport({ zoom: target.getZoom(), bbox: [b.getWest(), b.getSouth(), b.getEast(), b.getNorth()] })
  }
  const onLoad = ({ target }: { target: MapInstance }) => {
    target.touchZoomRotate.disableRotation()
    target.keyboard.disableRotation()
    setMap(target)
    report({ target })
  }
  const onClick = (e: MapLayerMouseEvent) => {
    // a tap on a marker reaches the map too; only a tap on the map itself closes sheets
    if ((e.originalEvent.target as Element | null)?.closest('.maplibregl-marker')) return
    onBackgroundClick()
  }

  return (
    <div ref={box} className="n-map" data-testid="map" data-level={level ?? undefined} data-zoom={zoom?.toFixed(2)}>
      {limits && (
        <Map
          ref={ref}
          mapStyle={mapStyle}
          initialViewState={{ bounds: overview.bounds, fitBoundsOptions: { padding: overview.padding } }}
          minZoom={limits.minZoom}
          maxZoom={MAX_ZOOM}
          maxBounds={limits.maxBounds}
          dragRotate={false}
          pitchWithRotate={false}
          touchPitch={false}
          maxPitch={0}
          renderWorldCopies={false}
          attributionControl={{ compact: false }}
          onLoad={onLoad}
          onMoveEnd={report}
          onClick={onClick}
        >
          {children}
        </Map>
      )}
      {limits && map && zoom !== null && (
        <ZoomButtons
          zoom={zoom}
          minZoom={limits.reachableMinZoom}
          maxZoom={MAX_ZOOM}
          onZoomIn={() => zoomBy(map, 1, prefersReducedMotion())}
          onZoomOut={() => zoomBy(map, -1, prefersReducedMotion())}
        />
      )}
    </div>
  )
}
