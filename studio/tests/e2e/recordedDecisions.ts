import { readFileSync } from 'node:fs'

// The decisions recorded in decisions/decisions.json, which the studio uses when the draft is empty.
// Read from disk: Playwright loads specs as native ES modules, where a JSON import needs an attribute.
export const recordedDecisions: Record<
  string,
  { variant: string; status: 'PROPOSED' | 'APPROVED' } | undefined
> = JSON.parse(
  readFileSync(new URL('../../decisions/decisions.json', import.meta.url), 'utf8'),
).decisions
