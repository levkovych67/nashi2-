import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useMemo, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { ApiProblem } from '../../api/client'
import { useFilters, useMapPins, useMapPlaces, useMapRegions, usePublicConfig } from '../../api/queries'
import { useDesign } from '../design/config'
import { parseFilterParams, writeFilterParams, type FilterState } from '../filters/filterState'
import { buildIndexes, type AggregationMode, type MapIndexes } from './aggregation'

export function useMapModel(): {
  indexes: MapIndexes | null
  filters: FilterState
  setFilters(s: FilterState): void
  loading: boolean
  error: ApiProblem | null
  /** Loads the failed requests again (they are `loading` again until they answer). */
  retry(): void
} {
  const [searchParams, setSearchParams] = useSearchParams()
  const mode = useDesign().variant('map.aggregation') as AggregationMode
  const pinsQuery = useMapPins()
  const regionsQuery = useMapRegions()
  const placesQuery = useMapPlaces()
  const filtersQuery = useFilters()
  const configQuery = usePublicConfig()

  const defs = filtersQuery.data
  const parsed = useMemo(() => (defs ? parseFilterParams(searchParams, defs) : {}), [searchParams, defs])
  // Unrelated query params change `searchParams` but not the filters; keep the previous object so indexes are not rebuilt.
  const filtersRef = useRef(parsed)
  if (JSON.stringify(filtersRef.current) !== JSON.stringify(parsed)) filtersRef.current = parsed
  const filters = filtersRef.current

  const setFilters = useCallback(
    (next: FilterState) => {
      // Keys missing from `next` must be cleared, otherwise a deselected filter would stay in the URL.
      const cleared = Object.fromEntries((defs ?? []).map((d) => [d.key, [] as string[]]))
      setSearchParams((sp) => writeFilterParams(sp, { ...cleared, ...next }), { replace: true })
    },
    [defs, setSearchParams],
  )

  const pins = pinsQuery.data?.pins
  const regions = regionsQuery.data
  const places = placesQuery.data
  const map = configQuery.data?.map
  const regionMaxZoom = map?.regionMaxZoom
  const placeMaxZoom = map?.placeMaxZoom

  const indexes = useMemo(() => {
    if (!regions || !places || regionMaxZoom === undefined || placeMaxZoom === undefined) return null
    return buildIndexes({
      pins: pins ?? [],
      regions,
      places,
      filters,
      thresholds: { regionMaxZoom, placeMaxZoom },
      mode,
    })
  }, [pins, regions, places, filters, regionMaxZoom, placeMaxZoom, mode])

  const queryClient = useQueryClient()
  const retry = useCallback(
    () => void queryClient.refetchQueries({ type: 'active', predicate: (q) => q.state.status === 'error' }),
    [queryClient],
  )

  const queries = [pinsQuery, regionsQuery, placesQuery, filtersQuery, configQuery]
  const error = (queries.find((q) => q.error)?.error as ApiProblem | undefined) ?? null
  return { indexes, filters, setFilters, loading: queries.some((q) => q.isPending), error, retry }
}
