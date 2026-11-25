import type { components } from '../../api/types.gen'

export type LinkPlatform = components['schemas']['LinkPlatform']
export type LinkGroup = 'MUSIC' | 'VIDEO' | 'REFERENCE' | 'SOCIAL'

const GROUPS: Record<LinkPlatform, LinkGroup> = {
  SPOTIFY: 'MUSIC',
  APPLE_MUSIC: 'MUSIC',
  DEEZER: 'MUSIC',
  YOUTUBE_MUSIC: 'MUSIC',
  SOUNDCLOUD: 'MUSIC',
  BANDCAMP: 'MUSIC',
  YOUTUBE: 'VIDEO',
  WIKIPEDIA: 'REFERENCE',
  WEBSITE: 'REFERENCE',
  INSTAGRAM: 'SOCIAL',
  FACEBOOK: 'SOCIAL',
  TIKTOK: 'SOCIAL',
  TELEGRAM: 'SOCIAL',
  X: 'SOCIAL',
  OTHER: 'SOCIAL',
}

export const platformGroup = (p: LinkPlatform): LinkGroup => GROUPS[p]

/** Lower-case host of an absolute http(s) URL, or null for anything else (same rule as the backend). */
export function httpHost(url: string): string | null {
  try {
    const u = new URL(url.trim())
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return null
    return u.hostname ? u.hostname.toLowerCase() : null
  } catch {
    return null
  }
}

const isDomain = (host: string, ...domains: string[]) => domains.some((d) => host === d || host.endsWith(`.${d}`))

// Order matters: music.youtube.com before youtube.com. Mirrors backend LinkClassifier.
const RULES: [LinkPlatform, string[]][] = [
  ['YOUTUBE_MUSIC', ['music.youtube.com']],
  ['SPOTIFY', ['spotify.com', 'spotify.link']],
  ['APPLE_MUSIC', ['music.apple.com']],
  ['DEEZER', ['deezer.com']],
  ['SOUNDCLOUD', ['soundcloud.com']],
  ['BANDCAMP', ['bandcamp.com']],
  ['YOUTUBE', ['youtube.com', 'youtu.be']],
  ['WIKIPEDIA', ['wikipedia.org']],
  ['INSTAGRAM', ['instagram.com']],
  ['FACEBOOK', ['facebook.com', 'fb.com']],
  ['TIKTOK', ['tiktok.com']],
  ['TELEGRAM', ['t.me', 'telegram.me']],
  ['X', ['x.com', 'twitter.com']],
]

export function detectPlatform(url: string): LinkPlatform {
  const host = httpHost(url)
  if (!host) return 'OTHER'
  return RULES.find(([, domains]) => isDomain(host, ...domains))?.[0] ?? 'WEBSITE'
}
