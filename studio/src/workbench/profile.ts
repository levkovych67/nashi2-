import type { AtlasData, Pin } from './data'
import { useEffect, useState } from 'react'

export interface Credit {
  author: string
  license: string
  licenseUrl: string | null
  sourceUrl: string
}
export interface Profile {
  partial?: boolean
  id: string
  name: string
  kind: string
  description: string
  summary: string
  tagline: string | null
  textSource: {
    title: string
    url: string
    license: string
    licenseUrl: string
  } | null
  images: { url: string; alt: string; credit: Credit | null }[]
  origin: {
    placeName: string
    regionName: string
    basis: string
    precision: string
    region: string
    lat: number
    lon: number
  }
  dates: {
    birth: string | null
    death: string | null
    activeFrom: string | number | null
    activeTo: string | number | null
  }
  genres: string[]
  links: { platform: string; url: string; group: string }[]
  materials: {
    clips: { clipId: string; title: string; durationSec: number }[]
    videos: { title: string; url: string }[]
    works: { title: string; year: number | null; url: string | null }[]
  }
  likesCount: number
  hasAudio: boolean
  youtubeChannelUrl: string | null
}
const cache = new Map<string, Promise<Profile>>()
export function loadProfile(slug: string) {
  const found = cache.get(slug)
  if (found) return found
  const request = fetch(
    '/fixtures/musicians/' + encodeURIComponent(slug) + '.json',
  )
    .then((r) => {
      if (!r.ok) throw Error()
      return r.json() as Promise<Profile>
    })
    .catch((e) => {
      cache.delete(slug)
      throw e
    })
  cache.set(slug, request)
  return request
}
export function useProfile(slug: string, fallback?: Profile) {
  const [result, setResult] = useState<{
    slug: string
    value: Profile | null
  }>()
  useEffect(() => {
    if (fallback) return
    let live = true
    loadProfile(slug).then(
      (value) => {
        if (live) setResult({ slug, value })
      },
      () => {
        if (live) setResult({ slug, value: null })
      },
    )
    return () => {
      live = false
    }
  }, [slug, fallback])
  if (fallback) return fallback
  return result?.slug === slug ? result.value : undefined
}

export function formatLifeDates(d: Profile['dates'], kind: string) {
  function format(value: string | number | null) {
    if (value == null) return ''
    const [year, month, day] = String(value).split('-')
    if (!month) return year
    const date = new Date(Date.UTC(+year, Number(month) - 1, Number(day || 1)))
    if (day)
      return (
        new Intl.DateTimeFormat('uk', {
          day: 'numeric',
          month: 'long',
          timeZone: 'UTC',
        }).format(date) +
        ' ' +
        year
      )
    return (
      new Intl.DateTimeFormat('uk', { month: 'long', timeZone: 'UTC' }).format(
        date,
      ) +
      ' ' +
      year
    )
  }
  if (kind === 'BAND')
    return d.activeFrom
      ? d.activeTo
        ? `${format(d.activeFrom)}–${format(d.activeTo)}`
        : 'з ' + format(d.activeFrom)
      : d.activeTo
        ? 'до ' + format(d.activeTo)
        : ''
  return d.birth
    ? d.death
      ? `${format(d.birth)} — ${format(d.death)}`
      : 'нар. ' + format(d.birth)
    : d.death
      ? 'пом. ' + format(d.death)
      : ''
}
const platforms: Record<string, string> = {
  SPOTIFY: 'Spotify',
  APPLE_MUSIC: 'Apple Music',
  YOUTUBE_MUSIC: 'YouTube Music',
  DEEZER: 'Deezer',
  SOUNDCLOUD: 'SoundCloud',
  BANDCAMP: 'Bandcamp',
  YOUTUBE: 'YouTube',
  WEBSITE: 'Вебсайт',
  WIKIPEDIA: 'Вікіпедія',
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
  X: 'X',
}
export function groupLinks(links: Profile['links']) {
  const order = Object.keys(platforms)
  return ['MUSIC', 'VIDEO', 'REFERENCE', 'SOCIAL']
    .map((group) => ({
      group,
      links: links
        .filter((l) => l.group === group && /^https?:\/\//i.test(l.url))
        .map((l) => {
          let host = l.platform
          try {
            host = new URL(l.url).hostname.replace(/^www\./, '')
          } catch {
            /* Keep platform label for incomplete metadata. */
          }
          return { ...l, label: platforms[l.platform] || host }
        })
        .sort(
          (a, b) =>
            (order.includes(a.platform)
              ? order.indexOf(a.platform)
              : order.length) -
            (order.includes(b.platform)
              ? order.indexOf(b.platform)
              : order.length),
        ),
    }))
    .filter((group) => group.links.length)
}

export function partialProfile(pin: Pin, data: AtlasData): Profile | undefined {
  if (data.profiles.has(pin.slug)) return undefined
  return {
    partial: true,
    id: pin.id,
    name: pin.name,
    kind: pin.kind,
    description: '',
    summary: 'У цьому наборі вказані ім’я та місце походження виконавця.',
    tagline: null,
    textSource: null,
    images: [],
    origin: {
      placeName: data.places.find((p) => p.placeId === pin.placeId)?.name || '',
      regionName: data.regions.find((r) => r.code === pin.region)?.name || '',
      basis: pin.kind === 'BAND' ? 'FORMATION' : 'BIRTH',
      precision: 'SETTLEMENT',
      region: pin.region,
      lat: pin.lat,
      lon: pin.lon,
    },
    dates: { birth: null, death: null, activeFrom: null, activeTo: null },
    genres: [],
    links: [],
    materials: { clips: [], works: [], videos: [] },
    likesCount: 0,
    hasAudio: pin.hasAudio,
    youtubeChannelUrl: null,
  }
}
