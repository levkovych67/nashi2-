import { memo, useMemo } from 'react'
import { Layer, Source } from 'react-map-gl/maplibre'
import { useGeo } from '../../core/map/geo'
import { fillOpacityExpression } from './fill'

const token = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()

/** Our GeoJSON on top of the basemap: region fill, region lines and the outline of Ukraine (with Crimea). */
export const MapOverlays = memo(function MapOverlays({ fillOpacity }: { fillOpacity: Record<string, number> }) {
  const geo = useGeo()
  const colors = useMemo(
    () => ({
      fill: token('--n-color-map-region-fill-active'),
      line: token('--n-color-map-region-line'),
      border: token('--n-color-map-ua-border'),
    }),
    [],
  )
  const fillPaint = useMemo(
    () => ({ 'fill-color': colors.fill, 'fill-opacity': fillOpacityExpression(fillOpacity) }),
    [colors, fillOpacity],
  )
  const linePaint = useMemo(() => ({ 'line-color': colors.line, 'line-width': 1 }), [colors])
  const borderPaint = useMemo(() => ({ 'line-color': colors.border, 'line-width': 1.8 }), [colors])
  if (!geo) return null
  return (
    <>
      <Source id="nashi-regions" type="geojson" data={geo.regions}>
        <Layer id="nashi-region-fill" type="fill" paint={fillPaint} />
        <Layer id="nashi-region-line" type="line" paint={linePaint} />
      </Source>
      <Source id="nashi-outline" type="geojson" data={geo.outline}>
        <Layer id="nashi-ua-border" type="line" paint={borderPaint} />
      </Source>
    </>
  )
})
