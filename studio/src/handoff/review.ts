import { DECISIONS, known } from '../decisions/catalog'
import { variants } from '../decisions/variants'
import {
  checkContrast,
  flattenTokens,
  missingTokens,
  type FlatTokens,
} from '../tokens/tokens'
import { REQUIRED_COPY_KEYS } from '../proto/copy/required'

export type Params = Record<string, string | number | boolean>
export interface DecisionEntry {
  variant: string
  status: 'PROPOSED' | 'APPROVED'
  params?: Params
  note?: string
  approvedAt?: string
  approvedBy?: string
}
export interface DecisionFile {
  schemaVersion: 1
  generatedAt: string
  studioCommit: string
  decisions: Record<string, DecisionEntry>
}
export const proposalNote = 'Пропозиція за замовчуванням. Потребує погодження.'
export function defaultVariant(id: string) {
  return id === 'identity' ? 'atlas' : variants[id][0].id
}
function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
export function localDate(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}
function validDate(value: unknown, now: Date) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value))
    return false
  const date = new Date(value + 'T00:00:00Z')
  return (
    Number.isFinite(date.getTime()) &&
    date.toISOString().slice(0, 10) === value &&
    value <= localDate(now)
  )
}
export function reviewDecisions(
  raw: unknown,
  now = new Date(),
  commit?: string,
) {
  const errors: string[] = []
  const entries: Record<string, DecisionEntry> = {}
  const obj = record(raw) ? raw : {}
  if (obj.schemaVersion !== 1)
    errors.push('decisions.json має використовувати schemaVersion 1')
  if (obj.studioCommit !== undefined && typeof obj.studioCommit !== 'string')
    errors.push('studioCommit має бути рядком')
  if (
    obj.generatedAt !== undefined &&
    obj.generatedAt !== '' &&
    (typeof obj.generatedAt !== 'string' ||
      !Number.isFinite(Date.parse(obj.generatedAt)))
  )
    errors.push('generatedAt має містити дійсну дату генерації')
  if (!record(obj.decisions))
    errors.push('decisions.json має містити об’єкт decisions')
  const source = record(obj.decisions) ? obj.decisions : {}
  for (const key of Object.keys(source))
    if (!DECISIONS.some((d) => d.id === key))
      errors.push('Невідоме рішення ' + key)
  for (const { id } of DECISIONS) {
    entries[id] = {
      variant: defaultVariant(id),
      status: 'PROPOSED',
      note: proposalNote,
    }
    if (!Object.hasOwn(source, id)) continue
    const value = source[id]
    if (!record(value)) {
      errors.push(id + ' має бути об’єктом')
      continue
    }
    if (typeof value.variant !== 'string' || !known(id, value.variant)) {
      errors.push(
        id +
          '=' +
          String(value.variant) +
          ': варіанта немає в реєстрі доступних до вибору',
      )
      continue
    }
    if (value.status !== 'APPROVED' && value.status !== 'PROPOSED') {
      errors.push(id + ' містить невідомий статус')
      continue
    }
    const entry: DecisionEntry = {
      status: value.status,
      variant: value.variant,
    }
    if (value.params !== undefined) {
      if (
        !record(value.params) ||
        !Object.values(value.params).every(
          (v) =>
            typeof v === 'string' ||
            typeof v === 'boolean' ||
            (typeof v === 'number' && Number.isFinite(v)),
        )
      )
        errors.push(id + ' містить неправильні параметри')
      else entry.params = value.params as Params
    }
    if (typeof value.note === 'string') entry.note = value.note
    else if (value.note !== undefined)
      errors.push(id + ' містить неправильну примітку')
    if (entry.status === 'APPROVED') {
      if (
        typeof value.approvedBy !== 'string' ||
        !value.approvedBy.trim() ||
        !validDate(value.approvedAt, now)
      )
        errors.push(id + ' потребує імені або ролі та дійсної дати погодження')
      else {
        entry.approvedBy = value.approvedBy.trim()
        entry.approvedAt = value.approvedAt as string
      }
    } else if (value.approvedBy !== undefined || value.approvedAt !== undefined)
      errors.push(id + ' має реквізити затвердження при статусі PROPOSED')
    entries[id] = entry
  }
  const file: DecisionFile = {
    schemaVersion: 1,
    generatedAt: now.toISOString(),
    studioCommit:
      commit ?? (typeof obj.studioCommit === 'string' ? obj.studioCommit : ''),
    decisions: entries,
  }
  return {
    file,
    errors,
    pending: DECISIONS.filter(
      ({ id }) => entries[id].status !== 'APPROVED',
    ).map((d) => d.id),
  }
}

export function inspectResources(
  identity: string,
  tokens: unknown,
  base: unknown,
  override: unknown,
) {
  const errors: string[] = []
  let flat: FlatTokens = {}
  if (!record(tokens)) errors.push('Бракує tokens.json для ' + identity)
  else
    try {
      flat = flattenTokens(tokens)
      const missing = missingTokens(flat)
      if (missing.length)
        errors.push(
          'identity ' + identity + ': бракує токенів ' + missing.join(', '),
        )
      else
        for (const issue of checkContrast(flat))
          errors.push(
            identity + ' · контраст ' + issue.pair + ' нижче ' + issue.min,
          )
    } catch (err) {
      errors.push(String(err))
    }
  const copy = {
    ...(record(base) ? base : {}),
    ...(record(override) ? override : {}),
  }
  for (const key of REQUIRED_COPY_KEYS)
    if (typeof copy[key] !== 'string' || !String(copy[key]).trim())
      errors.push('copy.uk.json: бракує ключа ' + key)
  for (const [key, value] of Object.entries(copy))
    if (typeof value !== 'string')
      errors.push('copy.uk.json: значення ' + key + ' має бути текстом')
  return { errors, flat, copy }
}

export function sameChoice(first: DecisionEntry, second: DecisionEntry) {
  const a = first.params ?? {},
    b = second.params ?? {}
  return (
    first.variant === second.variant &&
    Object.keys(a).length === Object.keys(b).length &&
    Object.keys(a).every((key) => a[key] === b[key])
  )
}
