import { describe, expect, it } from 'vitest'
import { ApiProblem } from '../../api/client'
import { loadErrorKey } from './loadError'

describe('loadErrorKey', () => {
  it('maps a failed load to the copy key that says what to do', () => {
    expect(loadErrorKey(new ApiProblem({ status: 0, code: 'NETWORK' }))).toBe('errors.network')
    expect(loadErrorKey(new ApiProblem({ status: 429, code: 'RATE_LIMITED' }))).toBe('errors.rateLimited')
    expect(loadErrorKey(new ApiProblem({ status: 500, code: 'INTERNAL' }))).toBe('errors.generic')
    expect(loadErrorKey(new Error('boom'))).toBe('errors.generic')
  })
})
