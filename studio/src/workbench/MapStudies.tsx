import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type RefObject,
} from 'react'
import type { Map as GLMap } from 'maplibre-gl'
import {
  type AtlasData,
  type Region,
  type Place,
  type Pin,
  num,
  originNotice,
} from './data'
import { MusicianProfile } from './MusicianProfile'
import { SampleButton } from './SampleButton'
import {
  boundsOf,
  fit,
  levelAt,
  makeIndex,
  project,
  regionAt,
  viewportBounds,
  type Camera,
  type Coord,
} from './mapGeometry'
import {
  emptyFilters,
  extraMatch,
  FilterControls,
  FilterPanel,
  hasExtra,
  persistFilters,
  readFilters,
  toggleRegion,
  type Filters,
} from './MapFilters'
import { StudyCanvas } from './StudyCanvas'
import { flattenTokens } from '../tokens/tokens'
import './MapStudies.css'

const identityFiles = import.meta.glob<object>('../identities/*/tokens.json', {
  eager: true,
  import: 'default',
})
type Step = 'overview' | 'region' | 'place' | 'pin'
type StudyProps = {
  id: string
  variant: string
  data: AtlasData
  theme: string
  onMusician?: (pin: Pin) => void
  choices?: Record<string, string>
  includePinCardLabel?: boolean
  onExplore?: (event: Exclude<Step, 'overview'>, pin?: Pin) => void
  advanceRef?: RefObject<((target?: Step) => void) | null>
}

const defaultSize = { width: 390, height: 365 }
const noRegions: string[] = []
const initialStudy = 'PIN_CARD_LABEL'
const simulations: Record<string, Coord> = {
  lviv: [24.03, 49.84],
  kyiv: [30.5234, 50.4501],
  warsaw: [21.01, 52.23],
}
const duration = () =>
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 0 : 650
const nameOrder = (a: { name: string }, b: { name: string }) =>
  a.name.localeCompare(b.name, 'uk')
const mostFirst = (a: { count: number }, b: { count: number }) =>
  b.count - a.count
const shortRegion = (name: string) =>
  name.replace(' область', '').replace('Автономна Республіка ', '')

export function MapStudies({
  id,
  variant,
  data,
  theme,
  onMusician,
  choices,
  includePinCardLabel = true,
  onExplore,
  advanceRef,
}: StudyProps) {
  const choice = (key: string) => (id === key ? variant : choices?.[key])
  const tokens = useMemo(
    () =>
      flattenTokens(
        identityFiles['../identities/' + theme + '/tokens.json'] ||
          identityFiles['../identities/atlas/tokens.json'],
      ),
    [theme],
  )
  const filterVariant = choice('filters.region')
  const filterStudy = Boolean(filterVariant)
  const mapFilter = filterVariant === 'MAP_TAP_REGION'
  const nearChoice =
    choice('map.initial-view') === 'UKRAINE_WITH_NEAR_ME_PROMPT'
  const pinVariant = choice('map.pin') || 'AVATAR_RING'
  const labelClusters = choice('map.cluster') === 'BUBBLE_COUNT_LABEL'
  const standalone = window.location.pathname.startsWith('/v/')
  const [filters, setFilters] = useState<Filters>(() =>
    standalone ? readFilters(location.search, data.regions) : emptyFilters,
  )
  const startingRegion =
    filters.regions.length === 1
      ? data.regions.find((r) => r.code === filters.regions[0])
      : undefined
  const example =
    variant === initialStudy
      ? data.pins.find((p) => p.slug === 'okean-elzy')
      : undefined
  const initialPlace =
    example && data.places.find((p) => p.placeId === example.placeId)
  const [focusRegion, setFocusRegion] = useState(
    startingRegion?.code ?? example?.region ?? '',
  )
  const [focusPlace, setFocusPlace] = useState(initialPlace)
  const [pin, setPin] = useState<Pin | undefined>(example)
  const [profile, setProfile] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [sheet, setSheet] = useState(false)
  const [hint, setHint] = useState(true)
  const [nearState, setNearState] = useState('')
  const [locating, setLocating] = useState(false)
  const [simulation, setSimulation] = useState('browser')
  const requestId = useRef(0)
  const wrap = useRef<HTMLDivElement>(null)
  const map = useRef<GLMap | null>(null)
  const [size, setSize] = useState(defaultSize)
  const [view, setView] = useState<Camera>(() =>
    example
      ? { center: [example.lon, example.lat], zoom: 13 }
      : fit(
          boundsOf(startingRegion ? [startingRegion] : data.regions),
          defaultSize,
        ),
  )
  const firstFit = useRef(true)
  const mode = choice('map.aggregation') || 'REGION_PLACE_PIN'
  const level = levelAt(view.zoom, mode)
  const base =
    choice('map.basemap') ||
    (theme === 'nichna-karta'
      ? 'night'
      : theme === 'atlas'
        ? 'atlas-paper'
        : 'light-minimal')
  const heat = mode === 'CHOROPLETH_PLACE_PIN' && level === 'region'
  const legend = choice('map.origin-notice') === 'LEGEND_BAR'
  const allBounds = useMemo(() => boundsOf(data.regions), [data.regions])
  const minZoom = Math.min(5, fit(allBounds, size).zoom)
  const byPlace = useMemo(
    () => new Map(data.places.map((p) => [p.placeId, p])),
    [data.places],
  )
  const byPin = useMemo(
    () => new Map(data.pins.map((p) => [p.id, p])),
    [data.pins],
  )
  const currentRegion = data.regions.find((r) => r.code === focusRegion)
  const pool = useMemo(() => {
    const extra = hasExtra(filters)
    const pins = extra
      ? data.pins.filter((p) => extraMatch(p, filters))
      : data.pins
    const selected = new Set(filters.regions)
    const allowed = (r: { region: string }) =>
      !selected.size || selected.has(r.region)
    const regionCounts = new Map<string, number>(),
      placeCounts = new Map<string, number>()
    if (extra)
      for (const p of pins) {
        regionCounts.set(p.region, 1 + (regionCounts.get(p.region) || 0))
        placeCounts.set(p.placeId, 1 + (placeCounts.get(p.placeId) || 0))
      }
    const regions = data.regions.map((r) =>
      extra ? { ...r, count: regionCounts.get(r.code) || 0 } : r,
    )
    const visibleRegions = regions.filter(
      (r) => !selected.size || selected.has(r.code),
    )
    const places = data.places
      .filter(allowed)
      .map((p) =>
        extra ? { ...p, count: placeCounts.get(p.placeId) || 0 } : p,
      )
      .filter((p) => p.count)
    return {
      pins: pins.filter(allowed),
      places,
      visibleRegions,
      total: visibleRegions.reduce((sum, r) => sum + r.count, 0),
    }
  }, [data, filters])
  const placeIndex = useMemo(
    () => makeIndex(pool.places, 'place'),
    [pool.places],
  )
  const pinIndex = useMemo(() => makeIndex(pool.pins, 'pin'), [pool.pins])
  const viewBounds = viewportBounds(view, size)
  const within = ([lon, lat]: number[]) =>
    lon >= viewBounds[0] &&
    lon <= viewBounds[2] &&
    lat >= viewBounds[1] &&
    lat <= viewBounds[3]
  const index = level === 'pin' ? pinIndex : placeIndex
  const items =
    level === 'region'
      ? []
      : index.getClusters(viewBounds, Math.floor(view.zoom))
  const regions =
    level !== 'region'
      ? []
      : pool.visibleRegions.filter((r) => r.count && within(r.center))
  const places =
    level === 'place'
      ? [
          ...pool.places.filter((p) =>
            focusRegion ? p.region === focusRegion : within([p.lon, p.lat]),
          ),
        ].sort(mostFirst)
      : []
  const pins =
    level === 'pin'
      ? [
          ...pool.pins.filter((p) =>
            focusPlace
              ? p.placeId === focusPlace.placeId
              : within([p.lon, p.lat]),
          ),
        ].sort(
          (a, b) =>
            Number(data.profiles.has(b.slug)) -
              Number(data.profiles.has(a.slug)) || nameOrder(a, b),
        )
      : []

  function go(target: Camera, animate = true) {
    firstFit.current = false
    const next = {
      center: target.center,
      zoom: Math.max(minZoom, Math.min(17, target.zoom)),
    }
    if (map.current)
      map.current.easeTo({
        center: next.center,
        zoom: next.zoom,
        duration: animate ? duration() : 0,
      })
    else setView(next)
  }
  useLayoutEffect(() => {
    const node = wrap.current
    if (!node) return
    const measure = () => {
      if (!node.clientWidth || !node.clientHeight) return
      const next = { width: node.clientWidth, height: node.clientHeight }
      setSize(next)
      if (!firstFit.current || example) return
      const initial = data.regions.filter((r) =>
        filters.regions.includes(r.code),
      )
      const target = fit(initial.length ? boundsOf(initial) : allBounds, next)
      if (map.current)
        map.current.jumpTo({ center: target.center, zoom: target.zoom })
      setView(target)
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(node)
    return () => observer.disconnect()
  }, [data.regions, allBounds, filters.regions, example, profile])
  useEffect(
    () => () => {
      requestId.current++
    },
    [],
  )

  function regionView(region: Region) {
    onExplore?.('region')
    setFocusRegion(region.code)
    setFocusPlace(undefined)
    setPin(undefined)
    const target = fit(region.bbox, size)
    go({ center: target.center, zoom: Math.max(7.1, target.zoom) })
  }
  function placeView(place: Place) {
    onExplore?.('place')
    setFocusRegion(place.region)
    setFocusPlace(place)
    setPin(undefined)
    go({ center: [place.lon, place.lat], zoom: 12.5 })
  }
  function choosePin(p: Pin) {
    setPin(p)
    setFocusPlace(byPlace.get(p.placeId))
    setFocusRegion(p.region)
    go({ center: [p.lon, p.lat], zoom: Math.max(13, view.zoom) })
    onExplore?.('pin', p)
    onMusician?.(p)
  }
  function national() {
    go(fit(allBounds, size))
    if (filters.regions.length || hasExtra(filters)) {
      setFilters(emptyFilters)
      if (standalone) persistFilters(emptyFilters)
    }
    setPin(undefined)
    setFocusRegion('')
    setFocusPlace(undefined)
  }
  function apply(next: Filters) {
    setFilters(next)
    setSheet(false)
    setPin(undefined)
    setFocusPlace(undefined)
    setFocusRegion(next.regions.length === 1 ? next.regions[0] : '')
    if (standalone) persistFilters(next)
    const selected = data.regions.filter((r) => next.regions.includes(r.code))
    go(fit(selected.length ? boundsOf(selected) : allBounds, size))
  }
  function territory(code: string) {
    if (mapFilter) {
      apply({ ...filters, regions: toggleRegion(filters.regions, code) })
      return
    }
    const r = pool.visibleRegions.find((s) => s.code === code)
    if (r && level === 'region') regionView(r)
  }
  function nearMe() {
    firstFit.current = false
    setHint(false)
    setLocating(true)
    setNearState('Визначаємо область…')
    const req = ++requestId.current
    function finish(
      pos: Coord | null,
      message = 'Геолокація недоступна. Оберіть область на карті.',
    ) {
      if (requestId.current !== req) return
      setLocating(false)
      if (!pos) {
        setNearState(message)
        return
      }
      const code = regionAt(pos[0], pos[1], data.shapes)
      const region = data.regions.find((r) => r.code === code)
      if (region) {
        regionView(region)
        setNearState(region.name)
      } else {
        national()
        setNearState('Ви за межами України. Оберіть область на карті.')
      }
    }
    if (simulation !== 'browser') {
      finish(simulations[simulation] || null)
      return
    }
    if (!navigator.geolocation) {
      finish(null)
      return
    }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => finish([coords.longitude, coords.latitude]),
      (e) =>
        finish(
          null,
          e.code === 1
            ? 'Доступ до геолокації закрито. Оберіть область на карті.'
            : undefined,
        ),
      { timeout: 10000, maximumAge: 60000 },
    )
  }
  useLayoutEffect(() => {
    if (!advanceRef) return
    advanceRef.current = (target) => {
      const step =
        target ||
        (level === 'region' ? 'region' : level === 'place' ? 'place' : 'pin')
      if (step === 'overview') {
        national()
        return
      }
      const region = data.regions.find((r) => r.code === 'LVIV')
      const place = data.places.find((p) => p.placeId === 'Q36036')
      const artist =
        data.pins.find((p) => p.slug === 'okean-elzy') ||
        data.pins.find((p) => p.hasAudio && data.profiles.has(p.slug))
      if (step === 'region' && region) regionView(region)
      if (step === 'place' && place) placeView(place)
      if (step === 'pin' && artist) choosePin(artist)
    }
    return () => {
      advanceRef.current = null
    }
  })
  useEffect(() => {
    if (
      !standalone ||
      !(import.meta.env.DEV || import.meta.env.VITE_STUDIO_OFFLINE_MAP === '1')
    )
      return
    const handle = {
      getZoom: () => map.current?.getZoom() ?? view.zoom,
      setZoom: (zoom: number) => go({ center: view.center, zoom }, false),
      flyTo: (lon: number, lat: number, zoom: number) =>
        go({ center: [lon, lat], zoom }, false),
    }
    const win = window as Window & { __studioMap?: typeof handle }
    win.__studioMap = handle
    return () => {
      if (win.__studioMap === handle) delete win.__studioMap
    }
  })
  const pinPlace = pin && byPlace.get(pin.placeId)
  const pinRegion = pin && data.regions.find((r) => r.code === pin.region)
  const originLabel =
    pin?.kind === 'BAND' ? 'Місце заснування' : 'Місце народження'
  const chip = (
    <div className="ms-chip-notice" data-testid="origin-notice">
      <button aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
        ⌖ Місце походження
      </button>
      {expanded && <p>{originNotice}.</p>}
    </div>
  )
  if (profile && pin)
    return data.profiles.has(pin.slug) ? (
      <MusicianProfile
        data={data}
        slug={pin.slug}
        back={() => setProfile(false)}
      />
    ) : (
      <section className="musician-study">
        <button className="study-back" onClick={() => setProfile(false)}>
          Повернутися на карту
        </button>
        <h2>{pin.name}</h2>
        <h3>{originLabel}</h3>
        <p>
          {pinPlace?.name} · {pinRegion?.name}
        </p>
        <p data-testid="origin-notice">{originNotice}.</p>
        {pin.hasAudio && <SampleButton id={pin.id} />}
      </section>
    )
  return (
    <section
      className="map-study"
      data-level={level}
      data-basemap={base}
      data-mode={mode}
    >
      <header className="proto-head">
        <b>Наші</b>
        <span>Музична карта України</span>
      </header>
      {filterStudy && (
        <FilterControls
          value={filters}
          regions={data.regions}
          variant={filterVariant!}
          onApply={apply}
          open={() => setSheet(true)}
        />
      )}
      <div className="ms-map-wrap">
        <div className="ms-stage" ref={wrap}>
          <StudyCanvas
            data={data}
            basemap={base}
            tokens={tokens}
            view={view}
            size={size}
            mapRef={map}
            onMove={setView}
            onRegion={territory}
            selected={filters.regions.length ? filters.regions : noRegions}
            choropleth={heat}
            minZoom={minZoom}
          />
          <div className="ms-marks">
            {regions.map((r) => {
              const [x, y] = project(r.center, view, size)
              return (
                <button
                  className={'ms-mark ms-region' + (heat ? ' ms-numeral' : '')}
                  key={r.code}
                  data-region={r.code}
                  data-testid="region-bubble"
                  aria-label={r.name + ', ' + num(r.count)}
                  title={r.name + ' · ' + num(r.count)}
                  aria-pressed={
                    mapFilter ? filters.regions.includes(r.code) : undefined
                  }
                  style={{ left: x, top: y }}
                  onClick={() =>
                    mapFilter ? territory(r.code) : regionView(r)
                  }
                >
                  <b>{num(r.count)}</b>
                  {(labelClusters ||
                    (id === 'map.basemap' &&
                      [
                        'LVIV',
                        'CRIMEA',
                        'KHARKIV',
                        'ODESA',
                        'KYIV_CITY',
                      ].includes(r.code))) && (
                    <small>{shortRegion(r.name)}</small>
                  )}
                </button>
              )
            })}
            {items.slice(0, 300).map((item) => {
              const [lon, lat] = item.geometry.coordinates
              const [x, y] = project([lon, lat], view, size)
              const p = item.properties
              if ('cluster' in p)
                return (
                  <button
                    className="ms-mark ms-group"
                    key={'c' + p.cluster_id}
                    data-testid={level + '-cluster'}
                    title={p.name + ' та поруч'}
                    aria-label={p.name + ' та поруч, ' + num(p.count)}
                    style={{ left: x, top: y }}
                    onClick={() => {
                      go({
                        center: [lon, lat],
                        zoom: Math.min(
                          17,
                          Math.max(
                            view.zoom + 1,
                            index.getClusterExpansionZoom(p.cluster_id),
                          ),
                        ),
                      })
                    }}
                  >
                    <b>{num(p.count)}</b>
                    {(level === 'place' || labelClusters) && (
                      <small>{p.name}</small>
                    )}
                  </button>
                )
              const single = level === 'pin'
              const artist = single && byPin.get(p.id)
              return (
                <button
                  className={
                    'ms-mark ' + (single ? 'ms-pin ' + pinVariant : 'ms-place')
                  }
                  key={p.id}
                  data-region={p.region}
                  data-testid={single ? 'pin' : 'place-bubble'}
                  style={{ left: x, top: y }}
                  title={p.name}
                  aria-pressed={single ? pin?.id === p.id : undefined}
                  aria-label={
                    p.name +
                    (single
                      ? artist && artist.hasAudio
                        ? ', є фрагмент'
                        : ''
                      : ', ' + num(p.count))
                  }
                  onClick={() => {
                    if (single) choosePin(byPin.get(p.id)!)
                    else placeView(byPlace.get(p.id)!)
                  }}
                >
                  <b>
                    {single && pinVariant === 'DROP_ICON'
                      ? '♪'
                      : single && pinVariant === 'DOT_LABEL'
                        ? '•'
                        : single
                          ? p.name
                              .split(/\s+/)
                              .slice(0, 2)
                              .map((n) => n[0])
                              .join('')
                          : num(p.count)}
                  </b>
                  {single && pinVariant === 'DOT_LABEL' && (
                    <small>{p.name}</small>
                  )}
                  {artist && artist.hasAudio && <i aria-hidden="true">♪</i>}
                  {!single && p.count >= 5 && <small>{p.name}</small>}
                </button>
              )
            })}
          </div>
          {!legend && chip}
          <div className="ms-zoom" aria-label="Масштаб карти">
            <button
              aria-label="Збільшити масштаб"
              disabled={view.zoom >= 17}
              onClick={() => go({ ...view, zoom: view.zoom + 1 })}
            >
              +
            </button>
            <button
              aria-label="Зменшити масштаб"
              disabled={view.zoom <= minZoom + 0.01}
              onClick={() => go({ ...view, zoom: view.zoom - 1 })}
            >
              −
            </button>
          </div>
          {nearChoice && (
            <div className="ms-near">
              {hint && <small>Знайдіть музикантів зі своєї області</small>}
              <button disabled={locating} onClick={nearMe}>
                Біля мене
              </button>
            </div>
          )}
        </div>
        {legend && (
          <p className="ms-legend" data-testid="origin-notice">
            {originNotice}.
          </p>
        )}
        {sheet && (
          <FilterPanel
            value={filters}
            regions={data.regions}
            count={(f) =>
              data.pins.filter(
                (p) =>
                  (!f.regions.length || f.regions.includes(p.region)) &&
                  extraMatch(p, f),
              ).length
            }
            onApply={apply}
            onClose={() => setSheet(false)}
          />
        )}
      </div>
      <div className="ms-credit">
        <a href="https://www.openstreetmap.org/copyright">© OpenStreetMap</a> ·{' '}
        <a href="https://www.geoboundaries.org/">geoBoundaries</a> ·{' '}
        <a href="https://openfreemap.org/">OpenFreeMap</a> ·{' '}
        <a href="https://openmaptiles.org/">OpenMapTiles</a>
      </div>
      <div className="ms-info">
        <div className="ms-heading">
          <div>
            <small>
              {filterStudy
                ? 'Знайдено музикантів'
                : level === 'region'
                  ? 'Області України'
                  : level === 'place'
                    ? 'Населені пункти'
                    : 'Позначки музикантів'}
            </small>
            <h2>
              {filterStudy
                ? num(pool.total)
                : focusPlace?.name || currentRegion?.name || 'Уся Україна'}
            </h2>
          </div>
          <button
            onClick={() => (filterStudy ? apply(emptyFilters) : national())}
          >
            {filterStudy ? 'Скинути' : 'До всієї України'}
          </button>
        </div>
        {filterStudy && filters.regions.length > 0 && (
          <div>
            <p>{pool.visibleRegions.map((r) => r.name).join(' · ')}</p>
            {mapFilter && (
              <button onClick={() => go(fit(allBounds, size))}>
                Огляд усіх областей
              </button>
            )}
          </div>
        )}
        {heat && (
          <div className="ms-heat-key">
            <i />
            Менше музикантів <span>Більше</span>
          </div>
        )}
        {nearState && <p role="status">{nearState}</p>}
        {filterStudy && pool.total === 0 && (
          <p role="status">За вибраними критеріями музикантів не знайдено.</p>
        )}
        {level === 'region' && !mapFilter && (
          <label>
            Область або місто
            <select
              value=""
              onChange={(e) => {
                const r = pool.visibleRegions.find(
                  (s) => s.code === e.target.value,
                )
                if (r) regionView(r)
              }}
            >
              <option value="">Обрати регіон</option>
              {pool.visibleRegions.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.name} · {num(r.count)}
                </option>
              ))}
            </select>
          </label>
        )}
        {level === 'place' && (
          <label>
            Населений пункт
            <select
              value=""
              onChange={(e) => {
                const p = byPlace.get(e.target.value)
                if (p) placeView(p)
              }}
            >
              <option value="">Обрати населений пункт</option>
              {places.map((p) => (
                <option key={p.placeId} value={p.placeId}>
                  {p.name} · {num(p.count)}
                </option>
              ))}
            </select>
          </label>
        )}
        {level === 'pin' && (
          <label>
            Музикант
            <select
              value={pin?.id || ''}
              onChange={(e) => {
                const p = byPin.get(e.target.value)
                if (p) choosePin(p)
              }}
            >
              <option value="">Обрати музиканта</option>
              {pins.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
        )}
        {items.length > 300 && (
          <p>Наблизьте карту, щоб побачити решту позначок.</p>
        )}
        {pin && !onMusician && (
          <section className="ms-pin-card">
            <div className="ms-sheet-head">
              <small>{pin.kind === 'BAND' ? 'Гурт' : 'Музикант'}</small>
              <button
                aria-label="Закрити картку"
                onClick={() => setPin(undefined)}
              >
                ×
              </button>
            </div>
            <h3>{pin.name}</h3>
            <p>
              {includePinCardLabel && (
                <>
                  <strong>{originLabel}</strong> ·{' '}
                </>
              )}
              {pinPlace?.name}, {pinRegion?.name}
            </p>
            {pin.hasAudio ? (
              <SampleButton key={pin.id} id={pin.id} />
            ) : (
              <small>Фрагмента немає</small>
            )}
            <button onClick={() => setProfile(true)}>Профіль музиканта</button>
          </section>
        )}
        {nearChoice && (
          <details className="ms-simulation">
            <summary>Перевірити геолокацію</summary>
            <label>
              Джерело координат
              <select
                value={simulation}
                onChange={(e) => {
                  setSimulation(e.target.value)
                  setNearState('')
                  setLocating(false)
                  requestId.current++
                }}
              >
                <option value="browser">Геолокація браузера</option>
                <option value="lviv">Львів</option>
                <option value="kyiv">Київ</option>
                <option value="warsaw">Варшава</option>
                <option value="deny">Відмова</option>
              </select>
            </label>
          </details>
        )}
      </div>
    </section>
  )
}
