import { describe, expect, it } from 'vitest'
import { createPointIndex } from './pointIndex'

const pt = (id: string, lon: number, lat: number) => ({ id, lon, lat })

describe('createPointIndex', () => {
  const points = [pt('a', 24, 49), pt('b', 24.2, 49), pt('c', 30, 50), pt('d', 23.9, 49.5)]
  const index = createPointIndex(points)

  it('nearLon returns only points inside the longitude window', () => {
    expect(index.nearLon(24, 0.15).map((p) => p.id).sort()).toEqual(['a', 'd'])
    expect(index.nearLon(40, 1)).toEqual([])
  })

  it('nearest agrees with a full scan', () => {
    let seed = 7
    const random = () => ((seed = (seed * 16807) % 2147483647) / 2147483647)
    const many = Array.from({ length: 500 }, (_, i) => pt(String(i), 22 + random() * 18, 44 + random() * 8))
    const big = createPointIndex(many)
    for (let q = 0; q < 50; q++) {
      const lon = 22 + random() * 18
      const lat = 44 + random() * 8
      const k = Math.cos((lat * Math.PI) / 180)
      const brute = [...many].sort(
        (x, y) => ((x.lon - lon) * k) ** 2 + (x.lat - lat) ** 2 - (((y.lon - lon) * k) ** 2 + (y.lat - lat) ** 2),
      )[0]
      expect(big.nearest(lon, lat)?.id).toBe(brute.id)
    }
  })

  it('nearest can skip points and is null when nothing is left', () => {
    expect(index.nearest(24, 49, (p) => p.id === 'a')?.id).not.toBe('a')
    expect(createPointIndex([]).nearest(24, 49)).toBeNull()
    expect(index.nearest(24, 49, () => true)).toBeNull()
  })
})
