import { useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { AtlasData, Pin } from './data'
import layers from './lvivLayers.json'

// The fallback is a projection of the four attributed z11 tiles in public/lviv.
export function StudyCityMap({
  data,
  pins,
  theme,
  renderPin,
  close,
  inset = 0,
}: {
  data: AtlasData
  pins: Pin[]
  theme: string
  renderPin: (p: Pin) => ReactNode
  close: () => void
  inset?: number
}) {
  const host = useRef<HTMLDivElement>(null)
  const [roots, setRoots] = useState<HTMLElement[] | null>(null)
  const closeRef = useRef(close)
  useEffect(() => {
    closeRef.current = close
  }, [close])
  useEffect(() => {
    let live = true
    let dispose: (() => void) | undefined
    const offline =
      import.meta.env.VITE_STUDIO_OFFLINE_MAP === '1' ||
      new URLSearchParams(window.location.search).get('offline') === '1'
    if (
      offline ||
      !host.current ||
      !window.WebGL2RenderingContext ||
      !pins.length
    )
      return
    Promise.all([
      import('./cityMap'),
      import('maplibre-gl'),
      import('maplibre-gl/dist/maplibre-gl.css'),
    ])
      .then(([{ createCityMap }, { default: lib }]) => {
        const div = host.current
        if (!live || !div) return
        const map = createCityMap(div, data)
        const targets = pins.map(() => document.createElement('div'))
        const markers = pins.map((p, i) =>
          new lib.Marker({ element: targets[i] })
            .setLngLat([p.lon, p.lat])
            .addTo(map),
        )
        const bounds = new lib.LngLatBounds()
        pins.forEach((p) => bounds.extend([p.lon, p.lat]))
        let fitted: { width: number; height: number } | undefined
        const fit = () => {
          map.resize()
          const { clientWidth: width, clientHeight: height } = div
          if (width < 135 || height < 110) return
          const previous = fitted
          fitted = { width, height }
          if (previous?.width === width) {
            // Only the height changed: a hint, the now-playing line or a card
            // opened or closed under the map. Keep the pins where they are, so a
            // hovered pin stays under the pointer, unless one would be cut off.
            map.panBy([0, (height - previous.height) / 2], { duration: 0 })
            const visible = pins.every((p) => {
              const { y } = map.project([p.lon, p.lat])
              return y >= 0 && y <= height
            })
            if (visible) return
          }
          const right = width > 720 ? inset + 36 : 36
          map.fitBounds(bounds, {
            padding: { top: 42, bottom: 52, left: 56, right },
            maxZoom: 13,
            duration: 0,
          })
        }
        map.addControl(
          new lib.NavigationControl({ showCompass: false }),
          'top-left',
        )
        map.on('click', () => closeRef.current())
        const resize = new ResizeObserver(fit)
        resize.observe(div)
        fit()
        const revealMap = () => {
          if (live && map.isSourceLoaded('city')) setRoots(targets)
        }
        map.on('idle', revealMap)
        map.on('error', () => {
          if (live) setRoots(null)
        })
        dispose = () => {
          resize.disconnect()
          markers.forEach((m) => m.remove())
          map.remove()
        }
      })
      .catch(() => {
        if (live) setRoots(null)
      })
    return () => {
      live = false
      dispose?.()
      setRoots(null)
    }
  }, [data, pins, theme, inset])
  return (
    <div className="artist-map">
      <div ref={host} className="artist-map-host" />
      {roots ? (
        pins.map((pin, i) => createPortal(renderPin(pin), roots[i], pin.id))
      ) : (
        <div className="artist-map-fallback" onClick={close}>
          <svg
            aria-hidden="true"
            viewBox="0 0 720 720"
            preserveAspectRatio="none"
          >
            <path className="city-green" d={layers.land} />
            <path className="city-water" d={layers.water} />
            <path className="city-road" d={layers.road} />
          </svg>
          {pins.map((pin) => (
            <div
              key={pin.id}
              className="artist-map-point"
              style={{
                left: ((pin.lon - 23.98) / 0.11) * 100 + '%',
                top: ((49.88 - pin.lat) / 0.08) * 100 + '%',
              }}
              onClick={(e) => e.stopPropagation()}
            >
              {renderPin(pin)}
            </div>
          ))}
        </div>
      )}
      <span className="artist-map-name">Львів</span>
      <span className="geo-credit">
        <a href="https://openfreemap.org/">OpenFreeMap</a> /{' '}
        <a href="https://openmaptiles.org/">OpenMapTiles</a> /{' '}
        <a href="https://www.openstreetmap.org/copyright">© OpenStreetMap</a>
      </span>
    </div>
  )
}
