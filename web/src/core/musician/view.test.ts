import { describe, expect, it } from 'vitest'
import type { components } from '../../api/types.gen'
import { createCopy } from '../i18n/copy'
import { groupLinks, isSafeHttpUrl, lifeDatesLabel, originLabel, youtubeEmbedUrl } from './view'

type Origin = components['schemas']['Origin']
type Link = components['schemas']['Link']

const t = createCopy({})
const origin = (over: Partial<Origin> = {}): Origin => ({
  placeId: 'Q36036', placeName: 'Львів', region: 'LVIV', regionName: 'Львівська область',
  basis: 'FORMATION', precision: 'SETTLEMENT', lat: 49.8, lon: 24, ...over,
})
const link = (group: Link['group'], url: string, platform: Link['platform'] = 'OTHER'): Link => ({ group, url, platform })

describe('originLabel', () => {
  it('labels by basis', () => {
    expect(originLabel(origin({ basis: 'BIRTH' }), t)).toBe('Місце народження: Львів')
    expect(originLabel(origin({ basis: 'FORMATION' }), t)).toBe('Місце заснування: Львів')
    expect(originLabel(origin({ basis: 'ORIGIN' }), t)).toBe('Походить з: Львів')
  })

  it('adds the precision note only for CITY', () => {
    const note = t('musician.precisionNote.CITY')
    expect(originLabel(origin({ precision: 'CITY' }), t)).toBe(`Місце заснування: Львів. ${note}`)
    expect(originLabel(origin({ precision: 'EXACT' }), t)).not.toContain(note)
    expect(originLabel(origin({ precision: 'SETTLEMENT' }), t)).not.toContain(note)
  })
})

describe('lifeDatesLabel', () => {
  const d = (over: Partial<components['schemas']['LifeDates']>) => ({ birth: null, death: null, activeFrom: null, activeTo: null, ...over })
  it('shows life years, then activity years, else null', () => {
    expect(lifeDatesLabel(d({ birth: '1872-09-23', death: '1916-05-01' }))).toBe('1872–1916')
    expect(lifeDatesLabel(d({ birth: '1872-09-23' }))).toBe('нар. 1872')
    expect(lifeDatesLabel(d({ death: '1916' }))).toBe('пом. 1916')
    expect(lifeDatesLabel(d({ activeFrom: '1994' }))).toBe('з 1994')
    expect(lifeDatesLabel(d({ activeFrom: '1994', activeTo: '2010-03' }))).toBe('1994–2010')
    expect(lifeDatesLabel(d({}))).toBeNull()
    expect(lifeDatesLabel(d({ birth: 'abc' }))).toBeNull()
  })
})

describe('isSafeHttpUrl', () => {
  it('accepts http and https', () => {
    expect(isSafeHttpUrl('https://example.com/a')).toBe(true)
    expect(isSafeHttpUrl('http://example.com')).toBe(true)
  })
  it('rejects javascript: and data: urls, relative and garbage', () => {
    for (const u of ['javascript:alert(1)', 'JaVaScRiPt:alert(1)', 'data:text/html,<b>x</b>', 'vbscript:x', '//evil.com', '/a', 'not a url', '', ' https://x.com\njavascript:1']) {
      expect(isSafeHttpUrl(u), u).toBe(false)
    }
    expect(isSafeHttpUrl(null)).toBe(false)
    expect(isSafeHttpUrl(undefined)).toBe(false)
  })
})

describe('groupLinks', () => {
  it('groups links MUSIC, VIDEO, REFERENCE, SOCIAL keeping order and always returning every group', () => {
    const a = link('MUSIC', 'https://a.example', 'SPOTIFY')
    const b = link('SOCIAL', 'https://b.example', 'INSTAGRAM')
    const c = link('MUSIC', 'https://c.example', 'DEEZER')
    const e = link('REFERENCE', 'https://e.example', 'WIKIPEDIA')
    expect(groupLinks([a, b, c, e])).toEqual({ MUSIC: [a, c], VIDEO: [], REFERENCE: [e], SOCIAL: [b] })
    expect(groupLinks([])).toEqual({ MUSIC: [], VIDEO: [], REFERENCE: [], SOCIAL: [] })
  })
})

describe('youtubeEmbedUrl', () => {
  it('uses the privacy-friendly nocookie host', () => {
    expect(youtubeEmbedUrl('dQw4w9WgXcQ')).toBe('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ')
  })
  it('escapes the id so it cannot change the url', () => {
    expect(youtubeEmbedUrl('a/../b?x=1')).toBe('https://www.youtube-nocookie.com/embed/a%2F..%2Fb%3Fx%3D1')
  })
})
