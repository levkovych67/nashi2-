import { useEffect, useState } from 'react'

export interface Credit {
  author: string
  license: string
  licenseUrl: string | null
  sourceUrl: string
}
export interface Profile {
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
  origin: { placeName: string; regionName: string; basis: string }
  links: { platform: string; url: string; group: string }[]
  materials: {
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
export function useProfile(slug: string) {
  const [result, setResult] = useState<{
    slug: string
    value: Profile | null
  }>()
  useEffect(() => {
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
  }, [slug])
  return result?.slug === slug ? result.value : undefined
}
