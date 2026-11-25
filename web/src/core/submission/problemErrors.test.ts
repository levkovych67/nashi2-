import { describe, expect, it } from 'vitest'
import { ApiProblem } from '../../api/client'
import { baseField, problemMessageKey, problemToFieldErrors } from './problemErrors'

const problem = (init: Partial<ConstructorParameters<typeof ApiProblem>[0]> = {}) =>
  new ApiProblem({ status: 400, code: 'VALIDATION_FAILED', ...init })

describe('problem errors', () => {
  it('maps errors[] by field, keeping the index of images[i] and links[i]', () => {
    const p = problem({
      errors: [
        { field: 'summary', message: 'Мінімум 30 символів' },
        { field: 'images[2]', message: 'Файл більший за 10 МБ' },
        { field: 'links[0]', message: 'Потрібне повне посилання http(s)' },
        { field: 'summary', message: 'друга' },
      ],
    })
    expect(problemToFieldErrors(p)).toEqual({
      summary: 'Мінімум 30 символів',
      'images[2]': 'Файл більший за 10 МБ',
      'links[0]': 'Потрібне повне посилання http(s)',
    })
  })

  it('is empty without errors[]', () => {
    expect(problemToFieldErrors(problem({ status: 413, code: 'PAYLOAD_TOO_LARGE' }))).toEqual({})
  })

  it('baseField drops the index', () => {
    expect(baseField('images[2]')).toBe('images')
    expect(baseField('genres[0]')).toBe('genres')
    expect(baseField('name')).toBe('name')
  })

  it('picks the message key by code, then by status', () => {
    expect(problemMessageKey(problem({ status: 413, code: 'PAYLOAD_TOO_LARGE' }))).toBe('submit.error.PAYLOAD_TOO_LARGE')
    expect(problemMessageKey(problem({ status: 415, code: 'UNKNOWN' }))).toBe('submit.error.UNSUPPORTED_MEDIA_TYPE')
    expect(problemMessageKey(problem({ status: 429, code: 'UNKNOWN' }))).toBe('submit.error.RATE_LIMITED')
    expect(problemMessageKey(problem({ status: 503, code: 'UNAVAILABLE' }))).toBe('submit.error.UNAVAILABLE')
    expect(problemMessageKey(problem({ status: 0, code: 'NETWORK' }))).toBe('submit.error.offline')
    expect(problemMessageKey(problem({ status: 500, code: 'INTERNAL' }))).toBe('submit.error.INTERNAL')
    expect(problemMessageKey(problem({ status: 418, code: 'WEIRD' }))).toBe('errors.generic')
  })
})
