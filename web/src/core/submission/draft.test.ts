import { afterEach, describe, expect, it } from 'vitest'
import { clearDraft, emptyState, formErrors, loadDraft, saveDraft, stepErrors, stepOfField, STEPS, toValues } from './draft'
import { createSubmissionSchema } from './schema'

const schema = createSubmissionSchema({ maxImages: 5, maxImageMb: 10, summaryMin: 30, summaryMax: 1000 })
const photo = new File([new Uint8Array(3)], 'p.jpg', { type: 'image/jpeg' })

afterEach(() => sessionStorage.clear())

describe('draft', () => {
  it('saves and restores the text fields, never the consent, photos or the bot trap', () => {
    saveDraft({
      ...emptyState(),
      name: 'Гурт',
      summary: 'Опис',
      origin: { id: 'Q1', text: 'Броди' },
      links: ['https://soundcloud.com/x'],
      consent: true,
      images: [photo],
      faxNumber: 'spam',
    })
    const restored = loadDraft()
    expect(restored).toMatchObject({ name: 'Гурт', summary: 'Опис', origin: { id: 'Q1', text: 'Броди' }, links: ['https://soundcloud.com/x'] })
    expect(restored?.consent).toBe(false)
    expect(restored?.images).toEqual([])
    expect(restored?.faxNumber).toBe('')
  })

  it('returns null for nothing or garbage, and clears', () => {
    expect(loadDraft()).toBeNull()
    sessionStorage.setItem('nashi-suggest-draft', '{oops')
    expect(loadDraft()).toBeNull()
    saveDraft({ ...emptyState(), name: 'Х' })
    clearDraft()
    expect(loadDraft()).toBeNull()
  })
})

describe('steps', () => {
  it('has six steps covering every field once', () => {
    expect(STEPS).toHaveLength(6)
    const all = STEPS.flatMap((s) => s.fields)
    expect(new Set(all).size).toBe(all.length)
    expect(stepOfField('images[2]')).toBe(4)
    expect(stepOfField('summary')).toBe(2)
    expect(stepOfField('consent')).toBe(5)
  })

  it('validates only the fields of the step', () => {
    const state = { ...emptyState(), name: 'Гурт' }
    const errors = formErrors(state, schema)
    expect(Object.keys(stepErrors(errors, 0))).toEqual([])
    expect(Object.keys(stepErrors(errors, 1))).toEqual(['originPlaceId'])
    expect(Object.keys(stepErrors(errors, 2))).toEqual(['summary'])
    expect(Object.keys(stepErrors(errors, 3))).toEqual(['links'])
    expect(Object.keys(stepErrors(errors, 4))).toEqual(['images'])
    expect(Object.keys(stepErrors(errors, 5))).toEqual(['consent'])
  })

  it('asks to pick a place from the list when text has no id', () => {
    const state = { ...emptyState(), kind: 'PERSON' as const, origin: { id: '', text: 'Бро' }, birth: { id: '', text: 'Льв' } }
    const errors = formErrors(state, schema)
    expect(errors.originPlaceId).toBe('submit.error.placePick')
    expect(errors.birthPlaceId).toBe('submit.error.placePick')
  })

  it('drops the birth place for a band and trims text', () => {
    const state = {
      ...emptyState(),
      name: '  Гурт ',
      kind: 'BAND' as const,
      birth: { id: 'Q9', text: 'Львів' },
      links: [' https://soundcloud.com/x ', ''],
      youtubeChannelUrl: ' ',
      genreInput: ' фолк ',
    }
    const v = toValues(state)
    expect(v).toMatchObject({ name: 'Гурт', birthPlaceId: null, links: ['https://soundcloud.com/x'], youtubeChannelUrl: null, genres: ['фолк'] })
  })
})
