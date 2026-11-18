import { z } from 'zod'
import { t } from '../proto/copy/t'

const platforms = [
  ['music.youtube.com', 'YOUTUBE_MUSIC', 'MUSIC'],
  ['spotify.com', 'SPOTIFY', 'MUSIC'],
  ['spotify.link', 'SPOTIFY', 'MUSIC'],
  ['music.apple.com', 'APPLE_MUSIC', 'MUSIC'],
  ['deezer.com', 'DEEZER', 'MUSIC'],
  ['soundcloud.com', 'SOUNDCLOUD', 'MUSIC'],
  ['bandcamp.com', 'BANDCAMP', 'MUSIC'],
  ['youtube.com', 'YOUTUBE', 'VIDEO'],
  ['youtu.be', 'YOUTUBE', 'VIDEO'],
  ['wikipedia.org', 'WIKIPEDIA', 'REFERENCE'],
  ['instagram.com', 'INSTAGRAM', 'SOCIAL'],
  ['facebook.com', 'FACEBOOK', 'SOCIAL'],
  ['tiktok.com', 'TIKTOK', 'SOCIAL'],
  ['t.me', 'TELEGRAM', 'SOCIAL'],
  ['telegram.me', 'TELEGRAM', 'SOCIAL'],
  ['x.com', 'X', 'SOCIAL'],
  ['twitter.com', 'X', 'SOCIAL'],
] as const

function httpHost(value: string) {
  if (!/^https?:\/\//i.test(value)) return null
  try {
    return new URL(value).hostname.toLowerCase()
  } catch {
    return null
  }
}

function matchesDomain(host: string, domain: string) {
  return host === domain || host.endsWith('.' + domain)
}

export function detectPlatform(value: string) {
  const host = httpHost(value)
  if (!host) return null
  const match = platforms.find(([domain]) => matchesDomain(host, domain))
  return match
    ? { platform: match[1], group: match[2] }
    : ({ platform: 'WEBSITE', group: 'REFERENCE' } as const)
}

export const platformNames = {
  SPOTIFY: 'Spotify',
  APPLE_MUSIC: 'Apple Music',
  YOUTUBE_MUSIC: 'YouTube Music',
  DEEZER: 'Deezer',
  SOUNDCLOUD: 'SoundCloud',
  BANDCAMP: 'Bandcamp',
  YOUTUBE: 'YouTube',
  WIKIPEDIA: 'Вікіпедія',
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
  TIKTOK: 'TikTok',
  TELEGRAM: 'Telegram',
  X: 'X',
  WEBSITE: 'Вебсайт',
}

function searchText(value: string) {
  return value
    .trim()
    .toLocaleLowerCase('uk')
    .normalize('NFC')
    .replace(/[’ʼ`]/g, "'")
}

export function searchPlaces<T extends { name: string }>(
  query: string,
  places: T[],
  limit = 8,
) {
  const text = searchText(query)
  if (text.length < 2) return []
  return places
    .filter(({ name }) => {
      const normalized = searchText(name)
      return (
        normalized.startsWith(text) ||
        normalized.split(/[\s‐‑–-]+/u).some((word) => word.startsWith(text))
      )
    })
    .slice(0, limit)
}

export const imageAccept = 'image/jpeg,image/png,image/webp'
export const maxFileBytes = 10 * 1024 ** 2
export function fileIssue(file: File) {
  if (!imageAccept.split(',').includes(file.type))
    return 'Підходять лише JPEG, PNG або WebP'
  if (file.size > maxFileBytes) return 'Файл більший за 10 МБ'
  return null
}

function youtubeUrl(value: string) {
  const host = httpHost(value)
  return (
    !!host &&
    (matchesDomain(host, 'youtube.com') || matchesDomain(host, 'youtu.be'))
  )
}

export const submissionSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(2, 'Введіть щонайменше 2 символи')
      .max(100, 'Максимум 100 символів'),
    kind: z.enum(['PERSON', 'BAND'], { error: 'Оберіть тип виконавця' }),
    originPlaceId: z.string().min(1, 'Оберіть місто зі списку'),
    birthPlaceId: z.string().nullable(),
    summary: z
      .string()
      .trim()
      .min(30, 'Мінімум 30 символів')
      .max(1000, 'Максимум 1000 символів'),
    genres: z
      .array(
        z
          .string()
          .trim()
          .min(2, 'Жанр має містити від 2 до 40 символів')
          .max(40, 'Жанр має містити від 2 до 40 символів'),
      )
      .max(5, 'Можна додати до 5 жанрів'),
    links: z
      .array(
        z
          .string()
          .trim()
          .refine(
            (value) => !!httpHost(value),
            'Введіть повну адресу з http або https',
          ),
      )
      .min(1, 'Додайте хоча б одне посилання на музику або YouTube')
      .max(10, 'Можна додати до 10 посилань'),
    youtubeChannelUrl: z
      .string()
      .refine(youtubeUrl, 'Потрібне посилання на YouTube')
      .nullable(),
    wikipediaUrl: z
      .string()
      .refine(
        (value) =>
          ['uk.wikipedia.org', 'en.wikipedia.org'].includes(
            httpHost(value) ?? '',
          ),
        'Введіть адресу статті української або англійської Вікіпедії',
      )
      .nullable(),
    images: z
      .array(
        z.instanceof(File).superRefine((file, ctx) => {
          const message = fileIssue(file)
          if (message) ctx.addIssue({ code: 'custom', message })
        }),
      )
      .min(1, 'Додайте від 1 до 5 фото')
      .max(5, 'Додайте від 1 до 5 фото'),
    contact: z.string().max(200, 'Максимум 200 символів'),
    consent: z.boolean().refine(Boolean, 'Потрібна згода'),
    faxNumber: z.string(),
  })
  .superRefine((draft, ctx) => {
    if (draft.birthPlaceId && draft.kind !== 'PERSON')
      ctx.addIssue({
        code: 'custom',
        path: ['birthPlaceId'],
        message: 'Місце народження — лише для людини, не для гурту',
      })
    const hasMusic = draft.links.some((value) => {
      const group = detectPlatform(value)?.group
      return group === 'MUSIC' || group === 'VIDEO'
    })
    if (
      !hasMusic &&
      !(draft.youtubeChannelUrl && youtubeUrl(draft.youtubeChannelUrl))
    )
      ctx.addIssue({
        code: 'custom',
        path: ['links'],
        message: 'Додайте хоча б одне посилання на музику або YouTube',
      })
  })

export type SubmissionDraft = z.infer<typeof submissionSchema>
export type FieldName = keyof SubmissionDraft
export type FieldErrors = Partial<Record<FieldName, string>>

export function validateDraft(
  draft: SubmissionDraft,
  placeIds: Set<string>,
): FieldErrors {
  const parsed = submissionSchema.safeParse(draft)
  const errors: FieldErrors = {}
  for (const issue of parsed.error?.issues ?? []) {
    const key = issue.path[0] as FieldName
    errors[key] ??= issue.message
  }
  if (!placeIds.has(draft.originPlaceId))
    errors.originPlaceId = 'Оберіть місто зі списку'
  if (draft.birthPlaceId && !placeIds.has(draft.birthPlaceId))
    errors.birthPlaceId = 'Оберіть місце зі списку'
  return errors
}

export function targetSize(w: number, h: number, maxSide = 2048) {
  const scale = Math.min(1, maxSide / Math.max(w, h))
  return {
    w: Math.max(1, Math.round(w * scale)),
    h: Math.max(1, Math.round(h * scale)),
  }
}

export async function downscaleImage(
  file: File,
  maxSide = 2048,
): Promise<Blob> {
  const url = URL.createObjectURL(file)
  try {
    const img = new Image()
    await new Promise<void>((resolve, reject) => {
      img.onload = () => resolve()
      img.onerror = () => reject(new Error('Не вдалося прочитати зображення'))
      img.src = url
    })
    const { w, h } = targetSize(img.naturalWidth, img.naturalHeight, maxSide)
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('Не вдалося підготувати фото')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, w, h)
    ctx.drawImage(img, 0, 0, w, h)
    const jpeg = await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(
        (blob) =>
          blob
            ? resolve(blob)
            : reject(new Error('Не вдалося підготувати фото')),
        'image/jpeg',
        0.85,
      ),
    )
    return jpeg
  } finally {
    URL.revokeObjectURL(url)
  }
}

export const scenarioNames = {
  success: 'Успіх',
  VALIDATION_FAILED: '400 · Помилка у полі',
  PAYLOAD_TOO_LARGE: '413 · Завеликі фото',
  UNSUPPORTED_MEDIA_TYPE: '415 · Непідтримуваний файл',
  RATE_LIMITED: '429 · Забагато запитів',
  INTERNAL: '500 · Помилка сервера',
  offline: 'Офлайн',
}
export type SubmitScenario = keyof typeof scenarioNames
export type ErrorScenario = Exclude<SubmitScenario, 'success'>
export const messages: Record<ErrorScenario, string> = {
  VALIDATION_FAILED: t('submit.error.VALIDATION_FAILED'),
  PAYLOAD_TOO_LARGE: t('submit.error.PAYLOAD_TOO_LARGE'),
  UNSUPPORTED_MEDIA_TYPE: t('submit.error.UNSUPPORTED_MEDIA_TYPE'),
  RATE_LIMITED: t('submit.error.RATE_LIMITED'),
  INTERNAL: t('submit.error.INTERNAL'),
  offline: t('submit.error.offline'),
}

const statuses: Record<ErrorScenario, number> = {
  VALIDATION_FAILED: 400,
  PAYLOAD_TOO_LARGE: 413,
  UNSUPPORTED_MEDIA_TYPE: 415,
  RATE_LIMITED: 429,
  INTERNAL: 500,
  offline: 0,
}
export interface SubmissionProblem {
  code: ErrorScenario
  status: number
  type: string
  title: string
  detail: string
  instance: string
  errors?: { field: string; message: string }[]
  retryAfter?: number
}
export type SubmissionResponse =
  | { id: string; status: 'PENDING' }
  | SubmissionProblem

let demoSequence = 0
export function simulatedResponse(
  draft: SubmissionDraft,
  scenario: SubmitScenario,
): SubmissionResponse {
  const checked = submissionSchema.safeParse(draft)
  const code =
    !checked.success && !draft.faxNumber ? 'VALIDATION_FAILED' : scenario
  if (code === 'success' || draft.faxNumber)
    return {
      id: 'demo-' + Date.now().toString(36) + '-' + ++demoSequence,
      status: 'PENDING',
    }
  const problem: SubmissionProblem = {
    code,
    type: 'urn:nashi:problem:' + code.toLowerCase(),
    status: statuses[code],
    instance: '/api/v2/submissions',
    title: scenarioNames[code],
    detail: messages[code],
  }
  if (code === 'VALIDATION_FAILED')
    problem.errors = checked.error?.issues.map((issue) => ({
      field:
        String(issue.path[0]) +
        (typeof issue.path[1] === 'number' ? '[' + issue.path[1] + ']' : ''),
      message: issue.message,
    })) ?? [{ field: 'links', message: messages.VALIDATION_FAILED }]
  if (code === 'UNSUPPORTED_MEDIA_TYPE')
    problem.errors = [
      { field: 'images[0]', message: messages.UNSUPPORTED_MEDIA_TYPE },
    ]
  if (code === 'RATE_LIMITED') problem.retryAfter = 2
  return problem
}
export async function fixtureSubmit(
  draft: SubmissionDraft,
  scenario: SubmitScenario,
  onProgress: (percent: number) => void,
  signal = new AbortController().signal,
  slowConnection = false,
): Promise<SubmissionResponse> {
  // Keep the full draft and its photos on this device throughout the simulation.
  return new Promise((resolve, reject) => {
    signal.throwIfAborted()
    let percent = 0
    const abort = () => {
      window.clearInterval(timer)
      reject(signal.reason)
    }
    const timer = window.setInterval(
      () => {
        percent += 20
        onProgress(percent)
        if (percent === 100) {
          window.clearInterval(timer)
          signal.removeEventListener('abort', abort)
          resolve(
            simulatedResponse(
              draft,
              navigator.onLine === false ? 'offline' : scenario,
            ),
          )
        }
      },
      slowConnection ? 1600 : 260,
    )
    signal.addEventListener('abort', abort, { once: true })
  })
}
