import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type RefObject,
  type PointerEvent,
} from 'react'
import type { ExpressionSpecification, Map as GLMap } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import type { AtlasData } from './data'
import type { FlatTokens } from '../tokens/tokens'
import {
  density,
  project,
  regionAt,
  unproject,
  type Camera,
  type Dimensions,
} from './mapGeometry'
import { basemapPalette, studyMapStyle } from './mapStyle'

interface Props {
  data: AtlasData
  basemap: string
  tokens: FlatTokens
  view: Camera
  size: Dimensions
  mapRef: RefObject<GLMap | null>
  onMove: (view: Camera) => void
  onRegion: (code: string) => void
  selected: string[]
  choropleth: boolean
  minZoom: number
}

export function StudyCanvas(props: Props) {
  const { data, basemap, tokens, view, size, mapRef, selected, choropleth } =
    props
  const host = useRef<HTMLDivElement>(null)
  const latest = useRef(props)
  useEffect(() => {
    latest.current = props
  }, [props])
  const [ready, setReady] = useState(false)
  const palette = basemapPalette(basemap, tokens)
  const colors = useMemo(
    () =>
      Object.fromEntries(
        data.regions.map((r) => [
          r.code,
          selected.includes(r.code)
            ? 0.75
            : choropleth
              ? density(r.count, data.regions)
              : 0,
        ]),
      ),
    [data.regions, selected, choropleth],
  )
  const opacity = useRef(colors)
  useEffect(() => {
    opacity.current = colors
    const map = mapRef.current
    if (map?.getLayer('region-color'))
      map.setPaintProperty(
        'region-color',
        'fill-opacity',
        colorExpression(colors),
      )
  }, [colors, mapRef])

  useEffect(() => {
    const container = host.current
    if (!container || typeof window.WebGL2RenderingContext === 'undefined')
      return
    let disposed = false
    let map: GLMap | undefined
    let resize: ResizeObserver | undefined
    const offline =
      import.meta.env.VITE_STUDIO_OFFLINE_MAP === '1' ||
      new URLSearchParams(window.location.search).get('offline') === '1'
    const style = studyMapStyle(
      basemap,
      offline ? null : import.meta.env.VITE_MAP_TILES_URL,
      undefined,
      tokens,
    )
    void import('maplibre-gl')
      .then(({ Map: LibreMap }) => {
        if (disposed) return
        const { view: initial, minZoom } = latest.current
        map = new LibreMap({
          container,
          style,
          center: initial.center,
          zoom: initial.zoom,
          minZoom,
          maxZoom: 17,
          attributionControl: false,
          maplibreLogo: false,
          dragRotate: false,
          pitchWithRotate: false,
          maxPitch: 0,
          renderWorldCopies: false,
          canvasContextAttributes: { antialias: false },
        })
        mapRef.current = map
        map.touchZoomRotate.disableRotation()
        let retriedOffline = offline
        map.on('error', (event) => {
          const failedSource = 'sourceId' in event ? event.sourceId : ''
          if (disposed || retriedOffline || failedSource !== 'openmaptiles')
            return
          retriedOffline = true
          map?.setStyle(studyMapStyle(basemap, null, undefined, tokens))
        })
        map.on('style.load', () => {
          if (disposed || !map) return
          if (map.getLayer('region-color'))
            map.setPaintProperty(
              'region-color',
              'fill-opacity',
              colorExpression(opacity.current),
            )
          setReady(true)
        })
        map.on('move', () => {
          if (!disposed && map)
            latest.current.onMove({
              center: map.getCenter().toArray(),
              zoom: map.getZoom(),
            })
        })
        map.on('click', ({ lngLat }) => {
          const code = regionAt(
            lngLat.lng,
            lngLat.lat,
            latest.current.data.shapes,
          )
          if (code) latest.current.onRegion(code)
        })
        resize = new ResizeObserver(() => map?.resize())
        resize.observe(container)
      })
      .catch(() => {
        if (!disposed) setReady(false)
      })
    return () => {
      disposed = true
      resize?.disconnect()
      map?.remove()
      mapRef.current = null
      setReady(false)
    }
  }, [basemap, mapRef, tokens])
  useEffect(() => {
    mapRef.current?.setMinZoom(props.minZoom)
  }, [mapRef, props.minZoom])

  // A simple Mercator overlay also keeps the map usable without a WebGL context.
  const paths = useMemo(
    () =>
      data.shapes.features.map((f) => {
        const polygons =
          f.geometry.type === 'Polygon'
            ? [f.geometry.coordinates]
            : f.geometry.coordinates
        const d = polygons
          .flatMap((poly) =>
            poly.map(
              (ring) =>
                ring
                  .map(([lon, lat], i) => {
                    const [x, y] = project(
                      [lon, lat],
                      { center: [0, 0], zoom: 0 },
                      { width: 0, height: 0 },
                    )
                    return (i ? 'L' : 'M') + x.toFixed(4) + ',' + y.toFixed(4)
                  })
                  .join('') + 'Z',
            ),
          )
          .join('')
        return { code: String(f.properties?.code), d }
      }),
    [data.shapes],
  )
  const drag = useRef<{
    x: number
    y: number
    moved: boolean
    view: Camera
  } | null>(null)
  const cursor = (e: PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    return [
      ((e.clientX - rect.left) * size.width) / rect.width,
      ((e.clientY - rect.top) * size.height) / rect.height,
    ]
  }
  const [ox, oy] = project([0, 0], view, size)
  return (
    <>
      {!ready && (
        <svg
          className="ms-offline"
          viewBox={'0 0 ' + size.width + ' ' + size.height}
          aria-label="Карта України"
          role="img"
          style={{ background: palette.base }}
          onPointerDown={(e) => {
            const [x, y] = cursor(e)
            drag.current = { x, y, view, moved: false }
            e.currentTarget.setPointerCapture?.(e.pointerId)
          }}
          onPointerMove={(e) => {
            const pan = drag.current
            if (!pan) return
            const [x, y] = cursor(e)
            if (Math.abs(x - pan.x) + Math.abs(y - pan.y) < 4 && !pan.moved)
              return
            pan.moved = true
            props.onMove({
              zoom: pan.view.zoom,
              center: unproject(
                size.width / 2 + pan.x - x,
                size.height / 2 + pan.y - y,
                pan.view,
                size,
              ),
            })
          }}
          onPointerUp={(e) => {
            const pan = drag.current
            drag.current = null
            if (!pan || pan.moved) return
            const [x, y] = cursor(e)
            const [lon, lat] = unproject(x, y, view, size)
            const code = regionAt(lon, lat, data.shapes)
            if (code) props.onRegion(code)
          }}
          onPointerCancel={() => {
            drag.current = null
          }}
        >
          <g
            transform={
              'translate(' + ox + ' ' + oy + ') scale(' + 2 ** view.zoom + ')'
            }
          >
            {paths.map(({ code, d }) => (
              <g key={code}>
                <path
                  d={d}
                  fill={palette.land}
                  fillRule="evenodd"
                  stroke={palette.border}
                  vectorEffect="non-scaling-stroke"
                />
                {colors[code] > 0 && (
                  <path
                    d={d}
                    fill={String(
                      tokens['color.map.region-fill-active']?.value ||
                        palette.outline,
                    )}
                    fillRule="evenodd"
                    fillOpacity={colors[code]}
                  />
                )}
              </g>
            ))}
          </g>
        </svg>
      )}
      <div
        className="ms-gl"
        ref={host}
        style={{ visibility: ready ? 'visible' : 'hidden' }}
      />
    </>
  )
}

function colorExpression(
  colors: Record<string, number>,
): ExpressionSpecification {
  return [
    'to-number',
    ['get', ['to-string', ['get', 'code']], ['literal', colors]],
    0,
  ]
}
