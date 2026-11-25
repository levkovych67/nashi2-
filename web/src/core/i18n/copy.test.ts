import { describe, expect, it } from 'vitest'
import { createCopy } from './copy'
import { DEFAULT_COPY } from './defaults.uk'

const SUBMIT_FIELDS = [
  'name', 'kind', 'originPlaceId', 'birthPlaceId', 'summary', 'genres', 'links',
  'youtubeChannelUrl', 'wikipediaUrl', 'contact', 'images',
]
const ERROR_CODES = [
  'VALIDATION_FAILED', 'PAYLOAD_TOO_LARGE', 'UNSUPPORTED_MEDIA_TYPE', 'RATE_LIMITED', 'INTERNAL', 'UNAVAILABLE',
]
// docs/02-contracts.md section 8
const REQUIRED = [
  'app.name', 'app.tagline', 'map.originNotice', 'map.soundOn', 'map.soundOff', 'map.soundBlockedHint',
  'map.nearMe', 'map.nearMeDenied', 'map.resultsCount', 'map.backToUkraine', 'cluster.ariaLabel', 'pin.ariaLabel',
  'pin.noAudio', 'preview.openProfile', 'preview.play', 'preview.stop', 'musician.originLabel.BIRTH',
  'musician.originLabel.FORMATION', 'musician.originLabel.ORIGIN', 'musician.precisionNote.CITY',
  'musician.likeAria', 'musician.likesCount', 'musician.listenOn', 'musician.youtubeChannel',
  'musician.textSource', 'musician.photoCredit', 'musician.noAudio', 'filters.title', 'filters.region',
  'filters.reset', 'filters.apply', 'list.empty', 'submit.title', 'submit.intro',
  ...SUBMIT_FIELDS.flatMap((f) => [`submit.field.${f}.label`, `submit.field.${f}.hint`]),
  'submit.consent', 'submit.send', 'submit.sending', 'submit.success',
  ...ERROR_CODES.map((c) => `submit.error.${c}`),
  'errors.network', 'errors.notFound', 'errors.rateLimited', 'errors.generic',
]

describe('copy', () => {
  it('plural uk: like count', () => {
    const t = createCopy({
      'musician.likesCount': '{count, plural, one {# лайк} few {# лайки} many {# лайків} other {# лайка}}',
    })
    expect([1, 3, 5, 11, 21, 22, 25].map((n) => t('musician.likesCount', { count: n }))).toEqual([
      '1 лайк', '3 лайки', '5 лайків', '11 лайків', '21 лайк', '22 лайки', '25 лайків',
    ])
  })

  it('falls back to default and then to the key', () => {
    const t = createCopy({ 'app.name': 'Override' })
    expect(t('app.name')).toBe('Override')
    expect(t('app.tagline')).toBe(DEFAULT_COPY['app.tagline'])
    expect(t('no.such.key' as never)).toBe('no.such.key')
  })

  it('substitutes {name}', () => {
    const t = createCopy({})
    expect(t('cluster.ariaLabel', { name: 'Львів', count: 3 })).toBe('Львів, музикантів: 3')
  })

  it('has every required key from contracts section 8', () => {
    const missing = REQUIRED.filter((k) => !(k in DEFAULT_COPY))
    expect(missing).toEqual([])
  })
})
