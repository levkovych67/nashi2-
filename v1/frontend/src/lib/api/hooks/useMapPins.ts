import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../client';
import type { components } from '../generated/types';

type MapPinDTO = components['schemas']['MapPinDTO'];

/** Every pin, loaded once; the map filters them in the browser (src/features/map/utils/pinFilters.ts). */
export function useMapPins() {
  return useQuery({
    queryKey: ['map', 'pins'],
    queryFn: () => apiClient.get<MapPinDTO[]>('/api/v1/map/pins'),
    staleTime: 10 * 60 * 1000,
  });
}
