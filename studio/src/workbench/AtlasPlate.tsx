import { useId, useMemo, useState } from 'react'
import { type AtlasData, type Region, num, originNotice } from './data'

const x = (lon: number) => (lon - 21.5) * 30
const y = (lat: number) => (52.8 - lat) * 46
function shape(feature: AtlasData['shapes']['features'][number]) {
  const polygons =
    feature.geometry.type === 'Polygon'
      ? [feature.geometry.coordinates]
      : feature.geometry.coordinates
  return polygons
    .flatMap((poly) =>
      poly.map(
        (ring) =>
          ring
            .map(
              ([lon, lat], i) =>
                (i ? 'L' : 'M') + x(lon).toFixed(1) + ',' + y(lat).toFixed(1),
            )
            .join('') + 'Z',
      ),
    )
    .join('')
}

export function AtlasPlate({
  data,
  labels = false,
  numerals = false,
  onRegion,
  regionCode,
}: {
  data: AtlasData
  labels?: boolean
  numerals?: boolean
  regionCode?: string
  onRegion?: (region: Region) => void
}) {
  const [local, setPicked] = useState<string>()
  const picked = regionCode ?? local
  const uid = useId().replace(/:/g, '')
  const paths = useMemo(
    () =>
      data.shapes.features.map((f) => ({
        code: String(f.properties?.code),
        d: shape(f),
      })),
    [data],
  )
  const active = data.regions.find((r) => r.code === picked)
  const choose = (r: Region) => {
    setPicked(r.code)
    onRegion?.(r)
  }
  return (
    <div className="plate">
      <svg
        viewBox="-18 -20 636 432"
        preserveAspectRatio="xMidYMid meet"
        role="img"
        aria-label="Музична карта України з Кримом, Києвом та Севастополем"
      >
        <defs>
          <pattern
            id={'grid' + uid}
            width="30"
            height="46"
            patternUnits="userSpaceOnUse"
          >
            <path
              d="M30 0H0V46"
              fill="none"
              stroke="currentColor"
              strokeOpacity=".14"
              strokeWidth=".8"
            />
          </pattern>
        </defs>
        <rect
          x="-18"
          y="-20"
          width="636"
          height="432"
          fill={'url(#grid' + uid + ')'}
        />
        {paths.map(({ code, d }) => (
          <path
            key={code}
            d={d}
            fillRule="evenodd"
            className={'territory' + (code === picked ? ' active' : '')}
            onClick={() => {
              const r = data.regions.find((r) => r.code === code)
              if (r) choose(r)
            }}
          />
        ))}
        {!numerals &&
          data.places.map((p) => (
            <circle
              key={p.placeId}
              className="settlement"
              cx={x(p.lon)}
              cy={y(p.lat)}
              r={Math.min(5.5, 0.85 + Math.sqrt(p.count) * 0.32)}
            />
          ))}
        {data.regions
          .filter(
            (r) =>
              r.count > 0 &&
              (numerals ||
                [
                  'LVIV',
                  'KYIV_CITY',
                  'ODESA',
                  'CRIMEA',
                  'KHARKIV',
                  'DONETSK',
                ].includes(r.code)),
          )
          .map((r) => {
            const place = data.places
              .filter((p) => p.region === r.code)
              .sort((a, b) => b.count - a.count)[0]
            const [cx, cy] =
              numerals || !place ? r.center : [place.lon, place.lat]
            const name = numerals
              ? r.name
                  .replace(' область', '')
                  .replace('Автономна Республіка ', '')
              : place?.name
            return (
              <g
                key={r.code}
                transform={'translate(' + x(cx) + ' ' + y(cy) + ')'}
                className="atlas-marker"
                onClick={() => choose(r)}
              >
                <title>
                  {r.name} · {num(r.count)}
                </title>
                <circle r={numerals ? 12 : 5.2} className="ink-dot" />
                {numerals && (
                  <text className="count" y="3">
                    {num(r.count)}
                  </text>
                )}
                {(!numerals || labels) && (
                  <text
                    className="geo-name"
                    x={numerals ? 0 : 9}
                    y={numerals ? 23 : 3}
                    textAnchor={numerals ? 'middle' : 'start'}
                  >
                    {name}
                  </text>
                )}
              </g>
            )
          })}
      </svg>
      {!onRegion && (
        <label className="geo-picker">
          Регіон{' '}
          <select
            aria-label="Регіон на оглядовій карті"
            value={picked ?? ''}
            onChange={(e) => setPicked(e.target.value)}
          >
            <option value="">Уся Україна</option>
            {data.regions.map((r) => (
              <option key={r.code} value={r.code}>
                {r.name}
              </option>
            ))}
          </select>
        </label>
      )}
      {active && (
        <output className="plate-result">
          {active.name} · {num(active.count)}
        </output>
      )}
      <span className="geo-credit">
        <a href="https://www.geoboundaries.org/">geoBoundaries</a> /{' '}
        <a href="https://www.openstreetmap.org/copyright">© OpenStreetMap</a>
      </span>
    </div>
  )
}
export function OriginNote() {
  return (
    <p className="origin-note" data-testid="origin-notice">
      <span aria-hidden="true">⌖</span> {originNotice}
    </p>
  )
}
