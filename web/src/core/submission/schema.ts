import { z } from 'zod'
import type { components } from '../../api/types.gen'
import { detectPlatform, httpHost, platformGroup } from './platforms'

export type SubmissionRequest = components['schemas']['SubmissionRequest']
export type SubmissionFormValues = Omit<SubmissionRequest, 'faxNumber'> & { images: File[] }

/** The `submission` block of the public config (02-contracts 2.11). */
export interface SubmissionLimits {
  maxImages: number
  maxImageMb: number
  summaryMin: number
  summaryMax: number
  /** Whole-request cap for photos (02-contracts 2.10: 55 MB); not part of the public config. */
  maxTotalMb?: number
}

export const DEFAULT_LIMITS: SubmissionLimits = { maxImages: 5, maxImageMb: 10, summaryMin: 30, summaryMax: 1000, maxTotalMb: 55 }

export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

const MAX_URL = 2048
const MAX_PLACE_ID = 32
const MB = 1024 * 1024
const WIKIPEDIA_HOSTS = ['uk.wikipedia.org', 'en.wikipedia.org']
const YOUTUBE_HOSTS = ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtu.be', 'www.youtu.be']

// Messages are copy keys (`submit.error.*`); the UI translates them.
const E = {
  required: 'submit.error.required',
  name: 'submit.error.nameLength',
  placePick: 'submit.error.placePick',
  placeLong: 'submit.error.placeLong',
  birthOnlyPerson: 'submit.error.birthOnlyPerson',
  summaryShort: 'submit.error.summaryShort',
  summaryLong: 'submit.error.summaryLong',
  genreLength: 'submit.error.genreLength',
  genresMax: 'submit.error.genresMax',
  linksCount: 'submit.error.linksCount',
  linkInvalid: 'submit.error.linkInvalid',
  linkLong: 'submit.error.linkLong',
  linksMusic: 'submit.error.linksMusic',
  youtube: 'submit.error.youtube',
  wikipedia: 'submit.error.wikipedia',
  contact: 'submit.error.contactLong',
  consent: 'submit.error.consent',
  imagesCount: 'submit.error.imagesCount',
  imageType: 'submit.error.imageType',
  imageSize: 'submit.error.imageSize',
  imageEmpty: 'submit.error.imageEmpty',
  imagesTotal: 'submit.error.imagesTotal',
} as const

const blank = (s: string | null | undefined) => s == null || s.trim() === ''

/**
 * The same rules as the backend `SubmissionValidator` (02-contracts 2.10): every violation is collected, with the
 * contract's field names (`links[0]`, `images[2]`). Place existence is the server's check.
 */
export function createSubmissionSchema(limits: SubmissionLimits): z.ZodType<SubmissionFormValues> {
  return z.custom<SubmissionFormValues>().superRefine((v, ctx) => {
    const add = (path: (string | number)[], message: string) => ctx.addIssue({ code: 'custom', path, message })

    if (typeof v.name !== 'string' || blank(v.name)) add(['name'], E.required)
    else if (v.name.length < 2 || v.name.length > 100) add(['name'], E.name)

    if (v.kind !== 'PERSON' && v.kind !== 'BAND') add(['kind'], E.required)

    if (blank(v.originPlaceId)) add(['originPlaceId'], E.placePick)
    else if (v.originPlaceId.length > MAX_PLACE_ID) add(['originPlaceId'], E.placeLong)
    if (!blank(v.birthPlaceId)) {
      if ((v.birthPlaceId as string).length > MAX_PLACE_ID) add(['birthPlaceId'], E.placeLong)
      else if (v.kind !== undefined && v.kind !== 'PERSON') add(['birthPlaceId'], E.birthOnlyPerson)
    }

    if (typeof v.summary !== 'string') add(['summary'], E.required)
    else if (v.summary.length < limits.summaryMin) add(['summary'], E.summaryShort)
    else if (v.summary.length > limits.summaryMax) add(['summary'], E.summaryLong)

    const genres = v.genres ?? []
    if (genres.length > 5) add(['genres'], E.genresMax)
    genres.forEach((g, i) => {
      if (g.length < 2 || g.length > 40) add(['genres', i], E.genreLength)
    })

    const links = v.links ?? []
    if (links.length < 1 || links.length > 10) add(['links'], E.linksCount)
    let listedInvalid = false
    let musicOrVideo = false
    links.forEach((url, i) => {
      if (url.length > MAX_URL) add(['links', i], E.linkLong)
      if (httpHost(url) === null) {
        add(['links', i], E.linkInvalid)
        listedInvalid = true
      } else {
        const group = platformGroup(detectPlatform(url))
        musicOrVideo ||= group === 'MUSIC' || group === 'VIDEO'
      }
    })
    if (!blank(v.youtubeChannelUrl)) {
      const url = v.youtubeChannelUrl as string
      if (url.length > MAX_URL) add(['youtubeChannelUrl'], E.linkLong)
      const host = httpHost(url)
      if (host === null || !YOUTUBE_HOSTS.includes(host)) add(['youtubeChannelUrl'], E.youtube)
      else musicOrVideo = true
    }
    if (!blank(v.wikipediaUrl)) {
      const url = v.wikipediaUrl as string
      if (url.length > MAX_URL) add(['wikipediaUrl'], E.linkLong)
      const host = httpHost(url)
      if (host === null || !WIKIPEDIA_HOSTS.includes(host)) add(['wikipediaUrl'], E.wikipedia)
    }
    if (!musicOrVideo && !listedInvalid && links.length > 0) add(['links'], E.linksMusic)

    if ((v.contact ?? '').length > 200) add(['contact'], E.contact)
    if (v.consent !== true) add(['consent'], E.consent)

    const images = v.images ?? []
    if (images.length < 1 || images.length > limits.maxImages) add(['images'], E.imagesCount)
    else {
      images.forEach((f, i) => {
        if (!(IMAGE_TYPES as readonly string[]).includes(f.type)) add(['images', i], E.imageType)
        else if (f.size === 0) add(['images', i], E.imageEmpty)
        else if (f.size > limits.maxImageMb * MB) add(['images', i], E.imageSize)
      })
      const total = images.reduce((sum, f) => sum + f.size, 0)
      if (total > (limits.maxTotalMb ?? DEFAULT_LIMITS.maxTotalMb!) * MB) add(['images'], E.imagesTotal)
    }
  })
}

export const submissionSchema = createSubmissionSchema(DEFAULT_LIMITS)

/** First message per field for every violation, keyed `summary`, `links[0]`, `images[2]`; `{}` when valid. */
export function fieldErrors(schema: z.ZodType<SubmissionFormValues>, value: unknown): Record<string, string> {
  const result = schema.safeParse(value)
  const out: Record<string, string> = {}
  if (result.success) return out
  for (const issue of result.error.issues) {
    const key = issue.path.reduce<string>((acc, p) => (typeof p === 'number' ? `${acc}[${p}]` : acc ? `${acc}.${String(p)}` : String(p)), '')
    out[key] ??= issue.message
  }
  return out
}
