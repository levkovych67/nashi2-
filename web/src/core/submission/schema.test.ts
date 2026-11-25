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

// Rejection just past each limit of SubmissionRequest.java (acceptance at the limit is covered above too).
describe('limits of SubmissionRequest.java: just past each one is rejected', () => {
  const url = (prefix: string, total: number) => prefix + 'a'.repeat(total - prefix.length)
  const SPOTIFY = 'https://open.spotify.com/artist/'
  const YT = 'https://www.youtube.com/@'
  const WIKI = 'https://uk.wikipedia.org/wiki/'

  it('originPlaceId: 32 ok, 33 rejected, blank or missing rejected', () => {
    expect(keys({ ...valid(), originPlaceId: 'Q' + '1'.repeat(31) })).toEqual([])
    expect(keys({ ...valid(), originPlaceId: 'Q' + '1'.repeat(32) })).toEqual(['originPlaceId'])
    expect(keys({ ...valid(), originPlaceId: '' })).toEqual(['originPlaceId'])
    expect(keys({ ...valid(), originPlaceId: '  ' })).toEqual(['originPlaceId'])
    expect(keys({ ...valid(), originPlaceId: undefined })).toEqual(['originPlaceId'])
  })

  it('birthPlaceId: 32 ok, 33 rejected', () => {
    const person = { ...valid(), kind: 'PERSON' as const }
    expect(keys({ ...person, birthPlaceId: 'Q' + '2'.repeat(31) })).toEqual([])
    expect(keys({ ...person, birthPlaceId: 'Q' + '2'.repeat(32) })).toEqual(['birthPlaceId'])
  })

  it('youtubeChannelUrl and wikipediaUrl: 2048 ok, 2049 rejected', () => {
    expect(keys({ ...valid(), youtubeChannelUrl: url(YT, 2048), wikipediaUrl: url(WIKI, 2048) })).toEqual([])
    expect(keys({ ...valid(), youtubeChannelUrl: url(YT, 2049) })).toEqual(['youtubeChannelUrl'])
    expect(keys({ ...valid(), wikipediaUrl: url(WIKI, 2049) })).toEqual(['wikipediaUrl'])
  })

  it('a link: 2048 ok, 2049 rejected', () => {
    expect(keys({ ...valid(), links: [url(SPOTIFY, 2049)] })).toEqual(['links[0]'])
  })

  it('name: 1 and 101 characters and whitespace only are rejected, 2 and 100 accepted', () => {
    expect(keys({ ...valid(), name: 'а' })).toEqual(['name'])
    expect(keys({ ...valid(), name: 'а'.repeat(101) })).toEqual(['name'])
    expect(keys({ ...valid(), name: '   ' })).toEqual(['name'])
    expect(keys({ ...valid(), name: 'аб' })).toEqual([])
    expect(keys({ ...valid(), name: 'а'.repeat(100) })).toEqual([])
  })

  it('links: an empty list is rejected', () => {
    expect(keys({ ...valid(), links: [] })).toEqual(['links'])
  })

  it('contact: 200 ok, 201 rejected', () => {
    expect(keys({ ...valid(), contact: 'c'.repeat(200) })).toEqual([])
    expect(keys({ ...valid(), contact: 'c'.repeat(201) })).toEqual(['contact'])
  })

  it('summary: 29 and 1001 rejected, 30 and 1000 accepted', () => {
    expect(keys({ ...valid(), summary: 'я'.repeat(29) })).toEqual(['summary'])
    expect(keys({ ...valid(), summary: 'я'.repeat(30) })).toEqual([])
    expect(keys({ ...valid(), summary: 'я'.repeat(1000) })).toEqual([])
    expect(keys({ ...valid(), summary: 'я'.repeat(1001) })).toEqual(['summary'])
  })

  it('consent: false, missing and null are rejected', () => {
    for (const consent of [false, undefined, null]) expect(keys({ ...valid(), consent })).toEqual(['consent'])
  })
})
