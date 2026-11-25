import booleanPointInPolygon from '@turf/boolean-point-in-polygon'
import type { Feature, FeatureCollection, MultiPolygon, Polygon } from 'geojson'
import { useEffect, useState } from 'react'
import type { components } from '../../api/types.gen'

type Region = components['schemas']['Region']
type RegionFeature = Feature<Polygon | MultiPolygon, { code: Region }>

export interface Geo {
  regions: FeatureCollection
  outline: FeatureCollection
}

/** Kyiv and Sevastopol are enclaves of their oblasts, so the city is tested before the surrounding region. */
const ENCLAVES: Region[] = ['KYIV_CITY', 'SEVASTOPOL_CITY']

async function fetchJson(url: string): Promise<FeatureCollection> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`${url}: ${res.status}`)
  return (await res.json()) as FeatureCollection
}

let loading: Promise<Geo> | null = null

/** The region and outline geometry, fetched once from /geo (kept out of the JS bundle). A failed load can be retried. */
export function loadGeo(): Promise<Geo> {
  loading ??= Promise.all([fetchJson('/geo/regions.geojson'), fetchJson('/geo/ukraine-outline.geojson')])
    .then(([regions, outline]) => ({ regions, outline }))
    .catch((e) => {
      loading = null
      throw e
    })
  return loading
}

/** The geometry once it has loaded, `null` until then. */
export function useGeo(): Geo | null {
  const [geo, setGeo] = useState<Geo | null>(null)
  useEffect(() => {
    let live = true
    loadGeo().then((g) => live && setGeo(g), () => {})
    return () => {
      live = false
    }
  }, [])
  return geo
}

export async function regionAt(lon: number, lat: number): Promise<Region | null> {
  const { regions } = await loadGeo()
  const ordered = [...regions.features].sort(
    (a, b) => Number(ENCLAVES.includes(b.properties?.code)) - Number(ENCLAVES.includes(a.properties?.code)),
  ) as RegionFeature[]
  const point: [number, number] = [lon, lat]
  return ordered.find((f) => booleanPointInPolygon(point, f))?.properties.code ?? null
}
