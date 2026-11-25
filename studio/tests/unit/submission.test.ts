import { describe, expect, it } from 'vitest'
import {
  detectPlatform,
  simulatedResponse,
  searchPlaces,
  submissionSchema,
  targetSize,
  type SubmissionDraft,
} from '../../src/workbench/submission'
const photo = new File(['x'], 'portrait.jpg', { type: 'image/jpeg' })
const draft: SubmissionDraft = {
  name: 'Гурт',
  kind: 'BAND',
  originPlaceId: 'Q36036',
  birthPlaceId: null,
  summary: 'а'.repeat(30),
  genres: [],
  links: ['https://open.spotify.com/artist/1'],
  youtubeChannelUrl: null,
  wikipediaUrl: null,
  images: [photo],
  contact: '',
  consent: true,
  faxNumber: '',
}
const places = [
  { name: 'Броди', placeId: 'a' },
  { name: "Кам'янець-Подільський", placeId: 'b' },
  { name: 'Івано-Франківськ', placeId: 'c' },
]
describe('пропозиція музиканта за контрактом 2.10', () => {
  it('приймає цілісну заявку і додатковий канал YouTube як музичне джерело', () => {
    expect(submissionSchema.safeParse(draft).success).toBe(true)
    expect(
      submissionSchema.safeParse({
        ...draft,
        links: ['https://example.com'],
        youtubeChannelUrl: 'https://www.youtube.com/@band',
      }).success,
    ).toBe(true)
  })
  it.each([
    ['name', 'а'],
    ['name', 'а'.repeat(101)],
    ['kind', ''],
    ['originPlaceId', ''],
    ['birthPlaceId', 'Q465104'],
    ['summary', 'а'.repeat(29)],
    ['summary', 'а'.repeat(1001)],
    ['genres', ['x']],
    ['genres', Array(6).fill('рок')],
    ['links', []],
    ['links', ['ftp://example.com']],
    ['links', ['https://example.com']],
    ['links', Array(11).fill('https://youtu.be/1')],
    ['youtubeChannelUrl', 'https://youtube.com.example.com/x'],
    ['wikipediaUrl', 'https://ru.wikipedia.org/wiki/X'],
    ['contact', 'a'.repeat(201)],
    ['consent', false],
    ['images', []],
    ['images', Array(6).fill(photo)],
    ['images', [new File(['gif'], 'x.gif', { type: 'image/gif' })]],
  ])('відхиляє неправильне значення у полі %s', (field, value) => {
    const parsed = submissionSchema.safeParse({ ...draft, [field]: value })
    expect(parsed.success).toBe(false)
    if (!parsed.success)
      expect(parsed.error.issues.map((issue) => issue.path[0])).toContain(field)
  })
  it('повертає PENDING чи проблему з полями та Retry-After відповідно до контракту', () => {
    expect(simulatedResponse(draft, 'success')).toMatchObject({
      status: 'PENDING',
    })
    expect(
      simulatedResponse({ ...draft, faxNumber: 'spam' }, 'INTERNAL'),
    ).toMatchObject({ status: 'PENDING' })
    expect(simulatedResponse(draft, 'RATE_LIMITED')).toMatchObject({
      status: 429,
      retryAfter: 2,
    })
    expect(simulatedResponse(draft, 'UNSUPPORTED_MEDIA_TYPE')).toMatchObject({
      status: 415,
      errors: [{ field: 'images[0]' }],
    })
    expect(
      simulatedResponse({ ...draft, summary: '' }, 'success'),
    ).toMatchObject({ status: 400, errors: [{ field: 'summary' }] })
  })
  it('відхиляє файли понад 10 МБ та зберігає індекс для поля', () => {
    const file = new File(['x'], 'file.jpg', { type: 'image/jpeg' })
    Object.defineProperty(file, 'size', { value: 10 * 1024 ** 2 + 1 })
    const parsed = submissionSchema.safeParse({
      ...draft,
      images: [photo, file],
    })
    expect(parsed.error?.issues[0].path).toEqual(['images', 1])
  })
  it('розпізнає хост, піддомени та протокол', () => {
    expect(detectPlatform('https://music.youtube.com/channel/x')).toEqual({
      platform: 'YOUTUBE_MUSIC',
      group: 'MUSIC',
    })
    expect(detectPlatform('https://youtu.be/x')?.group).toBe('VIDEO')
    expect(detectPlatform('https://x.bandcamp.com')?.platform).toBe('BANDCAMP')
    expect(detectPlatform('https://instagram.com/x')?.group).toBe('SOCIAL')
    expect(detectPlatform('https://uk.wikipedia.org/wiki/X')?.group).toBe(
      'REFERENCE',
    )
    expect(detectPlatform('https://notspotify.com')?.platform).toBe('WEBSITE')
    expect(detectPlatform('ftp://youtube.com/x')).toBeNull()
  })
  it('знаходить префікси слів, уніфікує апострофи й обмежує видачу', () => {
    expect(searchPlaces('б', places)).toEqual([])
    expect(searchPlaces(' БРО ', places)[0].name).toBe('Броди')
    expect(searchPlaces('камʼян', places)[0].placeId).toBe('b')
    expect(searchPlaces('франківськ', places)[0].placeId).toBe('c')
    expect(searchPlaces('фран', places, 0)).toEqual([])
  })
  it('зменшує довгу сторону, зберігає співвідношення сторін та уникає збільшення', () => {
    expect(targetSize(4032, 3024, 2048)).toEqual({ w: 2048, h: 1536 })
    expect(targetSize(800, 1000, 2048)).toEqual({ w: 800, h: 1000 })
  })
})
