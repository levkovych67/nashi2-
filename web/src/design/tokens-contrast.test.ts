import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

// read from disk: vitest stubs css imports (even ?raw) to an empty string
const css = readFileSync(resolve(process.cwd(), 'src/design/tokens.css'), 'utf8')

function token(name: string): string {
  const m = new RegExp(`--n-${name}\\s*:\\s*([^;]+);`).exec(css)
  if (!m) throw new Error(`token ${name} missing`)
  return m[1].trim()
}

function luminance(hex: string): number {
  const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(hex)
  if (!m) throw new Error(`unsupported colour ${hex}`)
  const h = m[1].length === 3 ? [...m[1]].map((c) => c + c).join('') : m[1]
  const [r, g, b] = [0, 2, 4].map((i) => {
    const c = parseInt(h.slice(i, i + 2), 16) / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

export function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

describe('contrast helper', () => {
  it('matches known WCAG values', () => {
    expect(contrast('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contrast('#777', '#fff')).toBeCloseTo(4.48, 1)
  })
})

// docs/02-contracts.md section 5
const PAIRS: [fg: string, bg: string, min: number][] = [
  ['color-text', 'color-bg', 4.5],
  ['color-text', 'color-surface', 4.5],
  ['color-on-accent', 'color-accent', 4.5],
  ['color-on-like', 'color-like', 4.5],
  ['color-text-muted', 'color-surface', 3],
  ['color-cluster-text', 'color-cluster-bg', 3],
]

describe('tokens.css contrast (WCAG)', () => {
  it.each(PAIRS)('%s on %s >= %s', (fg, bg, min) => {
    expect(contrast(token(fg), token(bg))).toBeGreaterThanOrEqual(min)
  })
})
