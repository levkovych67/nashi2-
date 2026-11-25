import { apiFetch } from '../../api/client'
import type { components } from '../../api/types.gen'

type Fragment = components['schemas']['Fragment']

/** `204 No Content` (the musician has no audio) → `null`. */
export async function getFragment(musicianId: string, exclude?: string): Promise<Fragment | null> {
  const f = await apiFetch<Fragment | undefined>(`/musicians/${encodeURIComponent(musicianId)}/fragment`, {
    query: { exclude },
  })
  return f ?? null
}
