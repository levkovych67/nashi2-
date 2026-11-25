import { describe, expect, it } from 'vitest'
import { canReloadForChunk, isChunkLoadError, markChunkReload } from './chunkReload'

const memory = () => {
  const m = new Map<string, string>()
  return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v) }
}

describe('chunk reload', () => {
  it('recognises a lazy chunk that failed to load in Chromium, Firefox and Safari', () => {
    expect(isChunkLoadError(new TypeError('Failed to fetch dynamically imported module: https://x/assets/a.js'))).toBe(true)
    expect(isChunkLoadError(new TypeError('error loading dynamically imported module: https://x/assets/a.js'))).toBe(true)
    expect(isChunkLoadError(new TypeError('Importing a module script failed.'))).toBe(true)
    expect(isChunkLoadError(new Error('Unable to preload CSS for /assets/a.css'))).toBe(true)
    expect(isChunkLoadError(new Error('Cannot read properties of undefined'))).toBe(false)
    expect(isChunkLoadError(null)).toBe(false)
  })

  it('allows one reload a minute, and none without storage to remember it', () => {
    const store = memory()
    expect(canReloadForChunk(store, 1_000_000)).toBe(true)
    markChunkReload(store, 1_000_000)
    expect(canReloadForChunk(store, 1_030_000)).toBe(false)
    expect(canReloadForChunk(store, 1_070_000)).toBe(true)
    expect(canReloadForChunk(null)).toBe(false)
  })
})
