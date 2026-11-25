import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { components } from './types.gen'

type S = components['schemas']

const MIN = 60_000
const STALE = 5 * MIN
const STALE_MAP = 10 * MIN

export interface MusiciansParams {
  region?: string[]
  q?: string
  sort?: 'NAME' | 'LIKES' | 'RECENT'
  page?: number
  size?: number
}

export const useMapPins = () =>
  useQuery({ queryKey: ['map', 'pins'], queryFn: () => apiFetch<S['MapPinsResponse']>('/map/pins'), staleTime: STALE_MAP })

export const useMapRegions = () =>
  useQuery({ queryKey: ['map', 'regions'], queryFn: () => apiFetch<S['RegionStat'][]>('/map/regions'), staleTime: STALE_MAP })

export const useMapPlaces = () =>
  useQuery({ queryKey: ['map', 'places'], queryFn: () => apiFetch<S['PlaceStat'][]>('/map/places'), staleTime: STALE_MAP })

export const useFilters = () =>
  useQuery({ queryKey: ['filters'], queryFn: () => apiFetch<S['FilterDefinition'][]>('/filters'), staleTime: STALE })

export const usePublicConfig = () =>
  useQuery({ queryKey: ['config'], queryFn: () => apiFetch<S['PublicConfig']>('/config/public'), staleTime: STALE })

export const useMusicians = (params: MusiciansParams) =>
  useQuery({
    queryKey: ['musicians', 'list', params],
    queryFn: () => apiFetch<S['MusicianPage']>('/musicians', { query: { ...params } }),
    staleTime: STALE,
  })

export const useMusician = (slug: string) =>
  useQuery({
    queryKey: ['musicians', 'detail', slug],
    queryFn: () => apiFetch<S['MusicianDetails']>(`/musicians/${encodeURIComponent(slug)}`),
    staleTime: STALE,
  })

export const usePlaceSearch = (q: string, region?: string) =>
  useQuery({
    queryKey: ['places', 'search', q, region],
    queryFn: () => apiFetch<S['PlaceSuggestion'][]>('/places/search', { query: { q, region } }),
    enabled: q.trim().length >= 2,
    staleTime: STALE,
  })
