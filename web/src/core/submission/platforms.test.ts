import { describe, expect, it } from 'vitest'
import links from '../../../../contracts/test-vectors/links.json'
import { detectPlatform, platformGroup } from './platforms'

describe('detectPlatform (parity with backend LinkClassifier)', () => {
  it.each(links)('$url -> $platform', ({ url, platform }) => {
    expect(detectPlatform(url)).toBe(platform)
  })

  it('groups platforms like the backend', () => {
    expect(platformGroup('SPOTIFY')).toBe('MUSIC')
    expect(platformGroup('YOUTUBE')).toBe('VIDEO')
    expect(platformGroup('WEBSITE')).toBe('REFERENCE')
    expect(platformGroup('INSTAGRAM')).toBe('SOCIAL')
  })

  it('treats junk and unfinished input as OTHER', () => {
    expect(detectPlatform('')).toBe('OTHER')
    expect(detectPlatform('open.spotify.com/x')).toBe('OTHER')
  })
})
