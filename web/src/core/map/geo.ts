import booleanPointInPolygon from '@turf/boolean-point-in-polygon'
import type { Feature, FeatureCollection, MultiPolygon, Polygon } from 'geojson'
import type { components } from '../../api/types.gen'
import outlineRaw from '@contracts/geo/ukraine-outline.geojson?raw'
import regionsRaw from '@contracts/geo/regions.geojson?raw'

type Region = components['schemas']['Region']

export const regionsGeoJson = JSON.parse(regionsRaw) as FeatureCollection
export const ukraineOutline = JSON.parse(outlineRaw) as FeatureCollection

/** Kyiv and Sevastopol are enclaves of their oblasts, so the city is tested before the surrounding region. */
const ENCLAVES: Region[] = ['KYIV_CITY', 'SEVASTOPOL_CITY']
const ordered = [...regionsGeoJson.features].sort(
  (a, b) => Number(ENCLAVES.includes(b.properties?.code)) - Number(ENCLAVES.includes(a.properties?.code)),
) as Feature<Polygon | MultiPolygon, { code: Region }>[]

export function regionAt(lon: number, lat: number): Region | null {
  const point: [number, number] = [lon, lat]
  return ordered.find((f) => booleanPointInPolygon(point, f))?.properties.code ?? null
}
