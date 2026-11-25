import { ApiProblem } from '../../api/client'

export type LoadErrorKey = 'errors.network' | 'errors.rateLimited' | 'errors.generic'

/** Copy key for data that failed to load: no connection, too many requests, or anything else. */
export function loadErrorKey(error: unknown): LoadErrorKey {
  if (error instanceof ApiProblem) {
    if (error.status === 0) return 'errors.network'
    if (error.status === 429) return 'errors.rateLimited'
  }
  return 'errors.generic'
}
