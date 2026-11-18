import { expect, test } from 'vitest'
import { variantUrl } from '../../src/shell/urls'

test('keeps a dotted decision id and the chosen identity', () => {
  expect(variantUrl('https://studio.example', 'map.pin', 'AVATAR_RING', 'atlas'))
    .toBe('https://studio.example/v/map.pin/AVATAR_RING?identity=atlas')
})

test('encodes path segments and resets the query from the original URL', () => {
  const url = new URL(variantUrl('https://studio.example/d/map.pin?test=1', 'a/b', 'c#d', 'архів & мапа'))
  expect(url.pathname).toBe('/v/a%2Fb/c%23d')
  expect([...url.searchParams]).toEqual([['identity', 'архів & мапа']])
})
