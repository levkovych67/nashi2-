import { describe, expect, it } from 'vitest'
import { createSubmissionSchema, DEFAULT_LIMITS, fieldErrors, submissionSchema, type SubmissionFormValues } from './schema'

const photo = (name = 'p.jpg', type = 'image/jpeg', size = 3) =>
  new File([new Uint8Array(size)], name, { type })

const valid = (): SubmissionFormValues => ({
  name: 'Океан Ельзи',
  kind: 'BAND',
  originPlaceId: 'Q36036',
  birthPlaceId: null,
  summary: 'Львівський гурт, що пише авторські композиції та грає на великих сценах.',
  genres: ['рок'],
  links: ['https://open.spotify.com/artist/abc'],
  youtubeChannelUrl: null,
  wikipediaUrl: null,
  contact: '',
  consent: true,
  images: [photo()],
})

const keys = (v: unknown) => Object.keys(fieldErrors(submissionSchema, v))

// The same cases as backend SubmissionValidatorTest.
describe('submissionSchema mirrors the backend SubmissionValidator', () => {
  it('accepts a valid request', () => {
    expect(submissionSchema.safeParse(valid()).success).toBe(true)
  })

  it('summary shorter than 30', () => {
    expect(fieldErrors(submissionSchema, { ...valid(), summary: 'Коротко' })).toEqual({
      summary: 'submit.error.summaryShort',
    })
  })

  it('summary longer than 1000', () => {
    expect(keys({ ...valid(), summary: 'я'.repeat(1001) })).toEqual(['summary'])
  })

  it('needs a music or video link', () => {
    expect(keys({ ...valid(), links: ['https://instagram.com/band'] })).toEqual(['links'])
  })

  it('a YouTube channel counts as a video link', () => {
    const v = { ...valid(), links: ['https://instagram.com/band'], youtubeChannelUrl: 'https://www.youtube.com/@band' }
    expect(keys(v)).toEqual([])
  })

  it('the YouTube channel must be on YouTube', () => {
    expect(keys({ ...valid(), youtubeChannelUrl: 'https://vimeo.com/band' })).toEqual(['youtubeChannelUrl'])
  })

  it('birth place is only for a person', () => {
    expect(keys({ ...valid(), birthPlaceId: 'Q465104' })).toEqual(['birthPlaceId'])
    expect(keys({ ...valid(), kind: 'PERSON', birthPlaceId: 'Q465104' })).toEqual([])
  })

  it('wikipedia must be uk or en', () => {
    for (const ok of ['https://uk.wikipedia.org/wiki/X', 'https://en.wikipedia.org/wiki/X']) {
      expect(keys({ ...valid(), wikipediaUrl: ok })).toEqual([])
    }
    expect(keys({ ...valid(), wikipediaUrl: 'https://ru.wikipedia.org/wiki/X' })).toEqual(['wikipediaUrl'])
  })

  it('rejects javascript: and relative links by index', () => {
    const yt = 'https://www.youtube.com/@band'
    expect(keys({ ...valid(), links: ['javascript:alert(1)'], youtubeChannelUrl: yt })).toEqual(['links[0]'])
    expect(keys({ ...valid(), links: ['open.spotify.com/artist/abc'], youtubeChannelUrl: yt })).toEqual(['links[0]'])
  })

  it('1..5 photos', () => {
    expect(keys({ ...valid(), images: Array.from({ length: 6 }, () => photo()) })).toEqual(['images'])
    expect(keys({ ...valid(), images: Array.from({ length: 5 }, () => photo()) })).toEqual([])
    expect(keys({ ...valid(), images: [] })).toEqual(['images'])
  })

  it('rejects an oversized, empty or wrong-type photo by index', () => {
    const big = photo('big.jpg', 'image/jpeg', 10 * 1024 * 1024 + 1)
    expect(keys({ ...valid(), images: [photo(), big] })).toEqual(['images[1]'])
    expect(keys({ ...valid(), images: [photo('e.jpg', 'image/jpeg', 0)] })).toEqual(['images[0]'])
    expect(keys({ ...valid(), images: [photo('a.gif', 'image/gif')] })).toEqual(['images[0]'])
  })

  it('consent is required', () => {
    expect(fieldErrors(submissionSchema, { ...valid(), consent: false })).toEqual({ consent: 'submit.error.consent' })
  })

  it('collects every violation at once', () => {
    expect(
      keys({ ...valid(), name: 'Г', kind: undefined, originPlaceId: '', summary: 'x', genres: ['а'], links: [], consent: false, images: [] }),
    ).toEqual(expect.arrayContaining(['name', 'kind', 'originPlaceId', 'summary', 'genres[0]', 'links', 'consent', 'images']))
  })

  it('caps URL, place id, name and contact lengths', () => {
    const url = (host: string) => `https://${host}/${'a'.repeat(2048)}`
    const v = {
      ...valid(),
      kind: 'PERSON' as const,
      name: 'а'.repeat(101),
      originPlaceId: 'Q' + '1'.repeat(32),
      birthPlaceId: 'Q' + '2'.repeat(32),
      links: ['https://open.spotify.com/artist/abc', url('open.spotify.com')],
      youtubeChannelUrl: url('www.youtube.com'),
      wikipediaUrl: url('uk.wikipedia.org'),
      contact: 'c'.repeat(201),
    }
    expect(keys(v).sort()).toEqual(
      ['birthPlaceId', 'contact', 'links[1]', 'name', 'originPlaceId', 'wikipediaUrl', 'youtubeChannelUrl'].sort(),
    )
  })

  it('accepts a URL exactly at the cap', () => {
    const base = 'https://open.spotify.com/artist/'
    expect(keys({ ...valid(), links: [base + 'a'.repeat(2048 - base.length)] })).toEqual([])
  })

  it('honours limits from the public config', async () => {
    const { createSubmissionSchema } = await import('./schema')
    const s = createSubmissionSchema({ maxImages: 2, maxImageMb: 1, summaryMin: 10, summaryMax: 100 })
    expect(Object.keys(fieldErrors(s, { ...valid(), images: [photo(), photo(), photo()] }))).toEqual(['images'])
    expect(Object.keys(fieldErrors(s, { ...valid(), summary: 'x'.repeat(101) }))).toEqual(['summary'])
  })

  it('genres: at most 5, each 2..40', () => {
    expect(keys({ ...valid(), genres: ['а', 'б', 'в', 'г', 'д', 'е'].map((g) => g + g) })).toEqual(['genres'])
    expect(keys({ ...valid(), genres: ['а'] })).toEqual(['genres[0]'])
    expect(keys({ ...valid(), genres: ['а'.repeat(41)] })).toEqual(['genres[0]'])
    expect(keys({ ...valid(), genres: ['аб', 'а'.repeat(40)] })).toEqual([])
  })

  it('links: 1..10', () => {
    const many = Array.from({ length: 11 }, (_, i) => `https://open.spotify.com/artist/${i}`)
    expect(keys({ ...valid(), links: many })).toEqual(['links'])
    expect(keys({ ...valid(), links: many.slice(0, 10) })).toEqual([])
  })

  it('name 2..100 and not blank, contact up to 200', () => {
    expect(keys({ ...valid(), name: '   ' })).toEqual(['name'])
    expect(keys({ ...valid(), name: 'аб' })).toEqual([])
    expect(keys({ ...valid(), name: 'а'.repeat(100), contact: 'c'.repeat(200) })).toEqual([])
  })

  it('place ids up to 32 characters', () => {
    expect(keys({ ...valid(), kind: 'PERSON', originPlaceId: 'Q'.repeat(32), birthPlaceId: 'Q'.repeat(32) })).toEqual([])
  })

  it('caps the whole photo set at 55 MB', () => {
    const big = () => photo('b.jpg', 'image/jpeg', 9 * 1024 * 1024)
    expect(keys({ ...valid(), images: Array.from({ length: 5 }, big) })).toEqual([]) // 45 MB
    const six = createSubmissionSchema({ ...DEFAULT_LIMITS, maxImages: 7 })
    expect(Object.keys(fieldErrors(six, { ...valid(), images: Array.from({ length: 7 }, big) }))).toEqual(['images']) // 63 MB
  })
})
