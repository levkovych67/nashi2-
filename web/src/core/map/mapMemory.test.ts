import { describe, expect, it } from 'vitest'
import { MAP_MEMORY_LIMIT, readMapMemory, saveMapMemory } from './mapMemory'

const memoryStore = () => {
  const m = new Map<string, string>()
  return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v) }
}

const at = (zoom: number, previewId: string | null = null) => ({ center: [24.03, 49.84] as [number, number], zoom, previewId })

describe('map memory (camera and preview per history entry)', () => {
  it('reads back what was saved for the same entry, and nothing for another', () => {
    const store = memoryStore()
    saveMapMemory('k1', at(12.5, 'abc'), store)
    expect(readMapMemory('k1', store)).toEqual(at(12.5, 'abc'))
    expect(readMapMemory('k2', store)).toBeNull()
  })

  it('never uses the "default" key: every new document starts with it', () => {
    const store = memoryStore()
    saveMapMemory('default', at(9), store)
    expect(readMapMemory('default', store)).toBeNull()
  })

  it('ignores broken or foreign data and works without storage', () => {
    const store = memoryStore()
    store.setItem('nashi.mapMemory', '{"k1":{"center":["x",1],"zoom":"far"}}')
    expect(readMapMemory('k1', store)).toBeNull()
    store.setItem('nashi.mapMemory', 'not json')
    expect(readMapMemory('k1', store)).toBeNull()
    expect(() => saveMapMemory('k1', at(7), null)).not.toThrow()
    expect(readMapMemory('k1', null)).toBeNull()
  })

  it(`keeps only the ${MAP_MEMORY_LIMIT} most recent entries`, () => {
    const store = memoryStore()
    for (let i = 0; i <= MAP_MEMORY_LIMIT; i++) saveMapMemory(`k${i}`, at(i), store)
    expect(readMapMemory('k0', store)).toBeNull()
    expect(readMapMemory(`k${MAP_MEMORY_LIMIT}`, store)?.zoom).toBe(MAP_MEMORY_LIMIT)
    saveMapMemory('k1', at(1.5), store) // saving again makes it the most recent
    saveMapMemory('new', at(3), store)
    expect(readMapMemory('k1', store)?.zoom).toBe(1.5)
    expect(readMapMemory('k2', store)).toBeNull()
  })
})
