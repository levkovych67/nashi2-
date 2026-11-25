import type { ApiProblem } from '../../api/client'
import type { CopyKey } from '../i18n/copy'

/** `images[2]` -> `images`, `links[0]` -> `links`. */
export const baseField = (field: string) => field.replace(/\[\d+\]$/, '')

/** Server messages per field (first one wins); indexed fields keep their index: `images[2]`, `links[0]`. */
export function problemToFieldErrors(p: ApiProblem): Record<string, string> {
  const out: Record<string, string> = {}
  for (const e of p.errors) out[e.field] ??= e.message
  return out
}

const BY_CODE = new Set(['VALIDATION_FAILED', 'PAYLOAD_TOO_LARGE', 'UNSUPPORTED_MEDIA_TYPE', 'RATE_LIMITED', 'INTERNAL', 'UNAVAILABLE'])
const BY_STATUS: Record<number, string> = { 413: 'PAYLOAD_TOO_LARGE', 415: 'UNSUPPORTED_MEDIA_TYPE', 429: 'RATE_LIMITED', 503: 'UNAVAILABLE' }

/** Copy key of the banner for a failed submission. */
export function problemMessageKey(p: ApiProblem): CopyKey {
  if (p.status === 0) return 'submit.error.offline'
  const code = BY_CODE.has(p.code) ? p.code : BY_STATUS[p.status] ?? (p.status >= 500 ? 'INTERNAL' : undefined)
  return code ? (`submit.error.${code}` as CopyKey) : 'errors.generic'
}
