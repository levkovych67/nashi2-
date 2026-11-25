import type { z } from 'zod'
import { baseField } from './problemErrors'
import { fieldErrors, type SubmissionFormValues } from './schema'

export interface PlaceChoice {
  /** Chosen `placeId`, '' while the user has only typed text. */
  id: string
  text: string
}

/** Everything the wizard holds. Only the text fields are kept as a draft; photos, consent and the bot trap are not. */
export interface FormState {
  name: string
  kind: 'PERSON' | 'BAND'
  origin: PlaceChoice
  birth: PlaceChoice
  summary: string
  genres: string[]
  /** Genre typed but not yet added as a chip. */
  genreInput: string
  links: string[]
  youtubeChannelUrl: string
  wikipediaUrl: string
  contact: string
  consent: boolean
  images: File[]
  faxNumber: string
}

export const emptyState = (): FormState => ({
  name: '',
  kind: 'PERSON',
  origin: { id: '', text: '' },
  birth: { id: '', text: '' },
  summary: '',
  genres: [],
  genreInput: '',
  links: [''],
  youtubeChannelUrl: '',
  wikipediaUrl: '',
  contact: '',
  consent: false,
  images: [],
  faxNumber: '',
})

/** Wizard steps in order and the schema fields each one owns. */
export const STEPS = [
  { id: 'who', fields: ['name', 'kind'] },
  { id: 'where', fields: ['originPlaceId', 'birthPlaceId'] },
  { id: 'about', fields: ['summary', 'genres'] },
  { id: 'links', fields: ['links', 'youtubeChannelUrl', 'wikipediaUrl'] },
  { id: 'photos', fields: ['images'] },
  { id: 'review', fields: ['contact', 'consent'] },
] as const

/** Index of the step that owns the field (`images[2]` belongs with `images`); -1 for an unknown field. */
export const stepOfField = (field: string) => STEPS.findIndex((s) => (s.fields as readonly string[]).includes(baseField(field)))

/** The genres the form would send: chips plus a pending, not yet added, one. */
export function allGenres(s: FormState): string[] {
  const pending = s.genreInput.trim()
  return [...new Set([...s.genres, ...(pending ? [pending] : [])])]
}

/** Form state as the schema/upload values (trimmed, empty optionals as null, no birth place for a band). */
export function toValues(s: FormState): SubmissionFormValues {
  const optional = (v: string) => v.trim() || null
  return {
    name: s.name.trim(),
    kind: s.kind,
    originPlaceId: s.origin.id,
    birthPlaceId: s.kind === 'PERSON' && s.birth.id ? s.birth.id : null,
    summary: s.summary.trim(),
    genres: allGenres(s),
    links: s.links.map((l) => l.trim()).filter(Boolean),
    youtubeChannelUrl: optional(s.youtubeChannelUrl),
    wikipediaUrl: optional(s.wikipediaUrl),
    contact: s.contact.trim(),
    consent: s.consent as true,
    images: s.images,
  }
}

/** Violations of the whole form, keyed by field (`links[0]`, `images[1]`); values are copy keys. */
export function formErrors(s: FormState, schema: z.ZodType<SubmissionFormValues>): Record<string, string> {
  const errors = fieldErrors(schema, toValues(s))
  if (s.kind === 'PERSON' && s.birth.text.trim() && !s.birth.id) errors.birthPlaceId ??= 'submit.error.placePick'
  return errors
}

/** The part of `errors` that belongs to step `index`. */
export function stepErrors(errors: Record<string, string>, index: number): Record<string, string> {
  return Object.fromEntries(Object.entries(errors).filter(([field]) => stepOfField(field) === index))
}

const KEY = 'nashi-suggest-draft'

type Draft = Pick<FormState, 'name' | 'kind' | 'origin' | 'birth' | 'summary' | 'genres' | 'genreInput' | 'links' | 'youtubeChannelUrl' | 'wikipediaUrl' | 'contact'>

export function saveDraft(s: FormState): void {
  const draft: Draft = {
    name: s.name, kind: s.kind, origin: s.origin, birth: s.birth, summary: s.summary, genres: s.genres,
    genreInput: s.genreInput, links: s.links, youtubeChannelUrl: s.youtubeChannelUrl,
    wikipediaUrl: s.wikipediaUrl, contact: s.contact,
  }
  try {
    sessionStorage.setItem(KEY, JSON.stringify(draft))
  } catch {
    // storage blocked or full: the form keeps working, only the reload safety net is gone
  }
}

export function loadDraft(): FormState | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const d = JSON.parse(raw) as Partial<Draft>
    return { ...emptyState(), ...d }
  } catch {
    return null
  }
}

export function clearDraft(): void {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // nothing to clear
  }
}
