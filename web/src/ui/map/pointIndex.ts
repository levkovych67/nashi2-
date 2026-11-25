export interface PointIndex<T> {
  /** Points whose longitude lies within `lonRadius` degrees of `lon`. */
  nearLon(lon: number, lonRadius: number): T[]
  /** The closest point (longitude scaled by cos(lat)), ignoring `skip`; null when there is none. */
  nearest(lon: number, lat: number, skip?: (p: T) => boolean): T | null
}

/** Points sorted by longitude: a window lookup or a nearest search scans only the neighbourhood of `lon`. */
export function createPointIndex<T extends { lon: number; lat: number }>(points: readonly T[]): PointIndex<T> {
  const sorted = [...points].sort((a, b) => a.lon - b.lon)

  const firstAtOrAfter = (lon: number) => {
    let lo = 0
    let hi = sorted.length
    while (lo < hi) {
      const mid = (lo + hi) >> 1
      if (sorted[mid].lon < lon) lo = mid + 1
      else hi = mid
    }
    return lo
  }

  return {
    nearLon(lon, lonRadius) {
      const out: T[] = []
      for (let i = firstAtOrAfter(lon - lonRadius); i < sorted.length && sorted[i].lon <= lon + lonRadius; i++) {
        out.push(sorted[i])
      }
      return out
    },
    nearest(lon, lat, skip) {
      const k = Math.cos((lat * Math.PI) / 180)
      let best: T | null = null
      let bestDistance = Infinity
      let left = firstAtOrAfter(lon) - 1
      let right = left + 1
      // Walk outwards from `lon`; stop once the longitude gap alone exceeds the best distance.
      while (left >= 0 || right < sorted.length) {
        const gapLeft = left >= 0 ? (lon - sorted[left].lon) * k : Infinity
        const gapRight = right < sorted.length ? (sorted[right].lon - lon) * k : Infinity
        const takeRight = gapRight <= gapLeft
        if (Math.min(gapLeft, gapRight) ** 2 >= bestDistance) break
        const p = takeRight ? sorted[right++] : sorted[left--]
        if (skip?.(p)) continue
        const d = ((p.lon - lon) * k) ** 2 + (p.lat - lat) ** 2
        if (d < bestDistance) [best, bestDistance] = [p, d]
      }
      return best
    },
  }
}
