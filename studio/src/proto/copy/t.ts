import { IntlMessageFormat } from 'intl-messageformat'
import uk from './copy.uk.json'
const messages: Record<string, string> = uk
const cache = new Map<string, IntlMessageFormat>()
export function t(
  key: string,
  vars: Record<string, string | number> = {},
  overrides: Record<string, string> = {},
) {
  const raw = overrides[key] ?? messages[key]
  if (!raw) return '⟦' + key + '⟧'
  if (!raw.includes('{')) return raw
  let compiled = cache.get(raw)
  if (!compiled) {
    compiled = new IntlMessageFormat(raw, 'uk')
    cache.set(raw, compiled)
  }
  return String(compiled.format(vars))
}
