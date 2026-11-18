import type * as GeoJSON from 'geojson'
import { useEffect, useState } from 'react'

export interface Region {
  code: string
  name: string
  center: [number, number]
  bbox: [number, number, number, number]
  count: number
}
export interface Place {
  placeId: string
  name: string
  region: string
  count: number
  lat: number
  lon: number
}
export interface Pin {
  id: string
  name: string
  slug: string
  hasAudio: boolean
  kind: string
  region: string
  placeId: string
  lat: number
  lon: number
  thumbUrl?: string
}
export interface AtlasData {
  regions: Region[]
  places: Place[]
  pins: Pin[]
  profiles: Set<string>
  shapes: GeoJSON.FeatureCollection<GeoJSON.Polygon | GeoJSON.MultiPolygon>
}

async function json<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (!response.ok) throw new Error(path)
  return response.json()
}

let overviewRequest: Promise<AtlasData> | undefined
let pinRequest: Promise<AtlasData> | undefined

function overview() {
  return (overviewRequest ??= Promise.all([
    json<Region[]>('/fixtures/map-regions.json'),
    json<Place[]>('/fixtures/map-places.json'),
    json<AtlasData['shapes']>('/geo/regions.geojson'),
  ])
    .then(([regions, places, shapes]) => ({
      regions,
      places,
      shapes,
      pins: [],
      profiles: new Set<string>(),
    }))
    .catch((error) => {
      overviewRequest = undefined
      throw error
    }))
}

function load(withPins: boolean) {
  if (!withPins) return overview()
  return (pinRequest ??= Promise.all([
    overview(),
    json<{ pins: Pin[] }>('/fixtures/map-pins.json'),
    json<string[]>('/fixtures/profile-slugs.json'),
  ])
    .then(([data, { pins }, slugs]) => ({
      ...data,
      pins,
      profiles: new Set(slugs),
    }))
    .catch((error) => {
      pinRequest = undefined
      throw error
    }))
}
export function useAtlasData(withPins = false) {
  const [result, setResult] = useState<{ data: AtlasData; withPins: boolean }>()
  const [error, setError] = useState(false)
  const [attempt, retry] = useState(0)
  useEffect(() => {
    let active = true
    load(withPins).then(
      (data) => {
        if (active) {
          setError(false)
          setResult({ data, withPins })
        }
      },
      () => {
        if (active) setError(true)
      },
    )
    return () => {
      active = false
    }
  }, [attempt, withPins])
  return {
    data: result?.withPins === withPins ? result.data : undefined,
    error,
    retry: () => {
      setError(false)
      retry((n) => n + 1)
    },
  }
}
export const num = (n: number) => new Intl.NumberFormat('uk-UA').format(n)
export const originNotice =
  'Позначка — місце походження музиканта, а не те, де він зараз'
