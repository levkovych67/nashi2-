import type { components } from '../../api/types.gen'
import { createCopy, type CopyFn } from '../i18n/copy'

type Origin = components['schemas']['Origin']
type LifeDates = components['schemas']['LifeDates']
type Link = components['schemas']['Link']
type LinkGroup = components['schemas']['LinkGroup']

/** "Місце заснування: Львів"; for an approximate (CITY) pin the precision note follows. */
export function originLabel(o: Origin, t: CopyFn): string {
  const label = t(`musician.originLabel.${o.basis}`, { place: o.placeName })
  return o.precision === 'CITY' ? `${label}. ${t('musician.precisionNote.CITY')}` : label
}

// Partial ISO dates (YYYY, YYYY-MM, YYYY-MM-DD) → the year, null for anything else (ported from V1 lifeDates).
const yearOf = (iso: string | null | undefined): string | null => iso?.match(/^(\d{4})(?:-\d{2}(?:-\d{2})?)?$/)?.[1] ?? null

const defaultCopy = createCopy({})

/** Life years of a person ("1872–1916", "нар. 1872") or activity years of a band ("з 1994"); null when unknown. */
export function lifeDatesLabel(d: LifeDates, t: CopyFn = defaultCopy): string | null {
  const [birth, death, from, to] = [yearOf(d.birth), yearOf(d.death), yearOf(d.activeFrom), yearOf(d.activeTo)]
  if (birth && death) return `${birth}–${death}`
  if (birth) return t('musician.dates.born', { year: birth })
  if (death) return t('musician.dates.died', { year: death })
  if (from && to) return `${from}–${to}`
  if (from) return t('musician.dates.since', { year: from })
  if (to) return t('musician.dates.until', { year: to })
  return null
}

/** True only for an absolute http(s) address: the only links from the API that may go into an href or src. */
export function isSafeHttpUrl(url: string | null | undefined): url is string {
  if (!url) return false
  try {
    const u = new URL(url)
    return (u.protocol === 'http:' || u.protocol === 'https:') && url === url.trim()
  } catch {
    return false
  }
}

const GROUPS: readonly LinkGroup[] = ['MUSIC', 'VIDEO', 'REFERENCE', 'SOCIAL']

export function groupLinks(links: Link[]): Record<LinkGroup, Link[]> {
  const out: Record<LinkGroup, Link[]> = { MUSIC: [], VIDEO: [], REFERENCE: [], SOCIAL: [] }
  for (const l of links) if (GROUPS.includes(l.group)) out[l.group].push(l)
  return out
}

/** Privacy-friendly embed address; nothing loads from it until the user asks for the video. */
export const youtubeEmbedUrl = (youtubeId: string): string => `https://www.youtube-nocookie.com/embed/${encodeURIComponent(youtubeId)}`
