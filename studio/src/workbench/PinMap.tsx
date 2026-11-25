import { createCityMap } from './cityMap'
import { useEffect, useRef, useState } from 'react'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import type { AtlasData, Pin } from './data'
import { MusicianProfile, PhotoCredit } from './MusicianProfile'
import { loadProfile, type Credit } from './profile'
import { SampleButton } from './SampleButton'
import { OriginNote } from './AtlasPlate'

function initials(name: string) {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((s) => s[0])
    .join('')
}
function artistSample(data: AtlasData) {
  const local = data.pins.filter(
    (p) => p.placeId === 'Q36036' && data.profiles.has(p.slug),
  )
  const preferred = ['Океан Ельзи', 'Піккардійська терція', 'Один в каное']
  return [
    ...new Set(
      [
        ...preferred
          .map((n) => local.find((p) => p.name === n))
          .filter((p): p is Pin => !!p),
        local.find((p) => !p.thumbUrl && !p.hasAudio),
        ...local,
      ].filter((p): p is Pin => !!p),
    ),
  ].slice(0, 6)
}
function marker(pin: Pin, variant: string) {
  const el = document.createElement('button')
  el.type = 'button'
  el.className = 'pin-hit ' + variant
  el.setAttribute('aria-label', pin.name)
  const mark = document.createElement('span')
  mark.className = 'pin-glyph'
  mark.textContent =
    variant === 'AVATAR_RING'
      ? initials(pin.name)
      : variant === 'DOT_LABEL'
        ? pin.name
        : '♪'
  if (variant === 'AVATAR_RING' && pin.thumbUrl) {
    void loadProfile(pin.slug)
      .then((profile) => {
        const photo = profile.images[0]
        if (!el.isConnected || !photo?.credit) return
        const img = document.createElement('img')
        img.alt = ''
        img.src = photo.url
        img.addEventListener('error', () => img.remove(), { once: true })
        mark.appendChild(img)
      })
      .catch(() => {
        /* Initials remain available when a credit cannot be loaded. */
      })
  }
  el.appendChild(mark)
  if (pin.hasAudio) {
    const dot = document.createElement('i')
    dot.textContent = '♪'
    dot.title = 'Є фрагмент'
    el.appendChild(dot)
  }
  return el
}
export function PinMap({
  data,
  variant,
  theme,
}: {
  data: AtlasData
  variant: string
  theme: string
}) {
  const [pins] = useState(() => artistSample(data))
  const [pin, setPin] = useState<Pin>(pins[0])
  const [profileOpen, setProfileOpen] = useState(false)
  const [credits, setCredits] = useState<{ name: string; credit: Credit }[]>([])
  useEffect(() => {
    let live = true
    if (variant !== 'AVATAR_RING') return
    Promise.all(
      pins
        .filter((p) => p.thumbUrl)
        .map(async (p) => {
          const profile = await loadProfile(p.slug).catch(() => null)
          return { name: p.name, credit: profile?.images[0]?.credit }
        }),
    ).then(
      (values) => {
        if (live)
          setCredits(
            values.filter(
              (v): v is { name: string; credit: Credit } => !!v.credit,
            ),
          )
      },
      () => {},
    )
    return () => {
      live = false
    }
  }, [pins, variant])
  const host = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const [failed, setFailed] = useState(false)
  const markers = useRef(new Map<string, HTMLElement>())
  useEffect(() => {
    const div = host.current
    if (!div || !pins.length) return
    let map: maplibregl.Map
    try {
      map = createCityMap(div, data)
    } catch {
      setFailed(true)
      return
    }
    setFailed(false)
    mapRef.current = map
    map.addControl(
      new maplibregl.NavigationControl({ showCompass: false }),
      'top-right',
    )
    const bounds = new maplibregl.LngLatBounds()
    const buttons = new Map<string, HTMLElement>()
    const elements = pins.map((p) => {
      const el = marker(p, variant)
      buttons.set(p.id, el)
      el.onclick = () => {
        setPin(p)
      }
      el.setAttribute('aria-pressed', String(p === pins[0]))
      bounds.extend([p.lon, p.lat])
      return new maplibregl.Marker({ element: el })
        .setLngLat([p.lon, p.lat])
        .addTo(map)
    })
    markers.current = buttons
    map.fitBounds(bounds, { padding: 50, maxZoom: 12, duration: 0 })
    const resize = new ResizeObserver(() => map.resize())
    resize.observe(div)
    return () => {
      resize.disconnect()
      for (const m of elements) m.remove()
      map.remove()
      mapRef.current = null
    }
  }, [data, pins, variant, theme])
  useEffect(() => {
    markers.current.forEach((el, id) =>
      el.setAttribute('aria-pressed', String(id === pin?.id)),
    )
  }, [pin, variant, theme])
  if (!pin) return <p>У наборі немає позначок Львова.</p>
  return (
    <>
      {profileOpen && (
        <MusicianProfile
          slug={pin.slug}
          back={() => {
            setProfileOpen(false)
            requestAnimationFrame(() => mapRef.current?.resize())
          }}
        />
      )}
      <section className="pin-proto" hidden={profileOpen}>
        <div className="proto-head">
          <b>Наші</b>
          <span>Музична карта</span>
        </div>
        <div className="city-stage">
          <div className="map-host" ref={host} />
          {failed && (
            <div className="map-fallback">
              <p>Позначки Львова · карта недоступна</p>
              <div className="pin-fallback-list">
                {pins.map((p) => (
                  <button
                    key={p.id}
                    aria-pressed={p === pin}
                    className={'pin-hit ' + variant}
                    onClick={() => setPin(p)}
                    aria-label={p.name}
                  >
                    <span className="pin-glyph">
                      {variant === 'AVATAR_RING'
                        ? initials(p.name)
                        : variant === 'DROP_ICON'
                          ? '♪'
                          : p.name}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}
          <span className="city-label">Львів</span>
        </div>
        <div className="geo-credit">
          <a href="https://openfreemap.org/">OpenFreeMap</a> /{' '}
          <a href="https://openmaptiles.org/">© OpenMapTiles</a> /{' '}
          <a href="https://www.openstreetmap.org/copyright">© OpenStreetMap</a>
        </div>
        <div className="pin-card">
          <small>
            {pin.kind === 'BAND'
              ? 'Гурт · місце заснування'
              : 'Музикант · місце народження'}
          </small>
          <h2>{pin.name}</h2>
          <p>Львів, Львівська область</p>
          {pin.hasAudio ? (
            !profileOpen && <SampleButton key={pin.id} id={pin.id} />
          ) : (
            <p>Фрагмента немає</p>
          )}
          <button
            onClick={() => {
              setProfileOpen(true)
            }}
          >
            Профіль музиканта
          </button>
          <label>
            Музикант{' '}
            <select
              value={pin.id}
              onChange={(e) => {
                const p = pins.find((item) => item.id === e.target.value)!
                setPin(p)
                mapRef.current?.easeTo({ center: [p.lon, p.lat], duration: 0 })
              }}
            >
              {pins.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          {variant === 'AVATAR_RING' && credits.length > 0 && (
            <details className="source-note">
              <summary>Автори фото</summary>
              {credits.map((c) => (
                <p key={c.name}>
                  {c.name} · <PhotoCredit credit={c.credit} />
                </p>
              ))}
            </details>
          )}
        </div>
        <OriginNote />
      </section>
    </>
  )
}
