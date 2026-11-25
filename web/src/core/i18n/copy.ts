import { createContext, useContext } from 'react'
import { DEFAULT_COPY } from './defaults.uk'

export type CopyKey = keyof typeof DEFAULT_COPY
export type CopyVars = Record<string, string | number>
export type CopyFn = (key: CopyKey, vars?: CopyVars) => string

const rules = new Intl.PluralRules('uk')

// Index of the "}" matching the "{" at `open`.
function closing(src: string, open: number): number {
  let depth = 0
  for (let i = open; i < src.length; i++) {
    if (src[i] === '{') depth++
    else if (src[i] === '}' && --depth === 0) return i
  }
  return src.length
}

// Branches of a plural body: `one {…} few {…} =0 {…}` -> { one: '…', few: '…' }.
function parseBranches(body: string): Record<string, string> {
  const out: Record<string, string> = {}
  let i = 0
  while (i < body.length) {
    const open = body.indexOf('{', i)
    if (open < 0) break
    const name = body.slice(i, open).trim()
    const end = closing(body, open)
    out[name] = body.slice(open + 1, end)
    i = end + 1
  }
  return out
}

function format(tpl: string, vars: CopyVars, count?: number): string {
  let out = ''
  let i = 0
  while (i < tpl.length) {
    const ch = tpl[i]
    if (ch === '#' && count !== undefined) {
      out += String(count)
      i++
    } else if (ch === '{') {
      const end = closing(tpl, i)
      const inner = tpl.slice(i + 1, end)
      const comma = inner.indexOf(',')
      if (comma < 0) {
        const v = vars[inner.trim()]
        out += v === undefined ? `{${inner}}` : String(v)
      } else {
        const name = inner.slice(0, comma).trim()
        const rest = inner.slice(comma + 1)
        const second = rest.indexOf(',')
        const type = rest.slice(0, second < 0 ? undefined : second).trim()
        const n = Number(vars[name])
        if (type === 'plural' && second >= 0 && Number.isFinite(n)) {
          const branches = parseBranches(rest.slice(second + 1))
          const branch = branches[`=${n}`] ?? branches[rules.select(n)] ?? branches.other ?? ''
          out += format(branch, vars, n)
        }
      }
      i = end + 1
    } else {
      out += ch
      i++
    }
  }
  return out
}

export function createCopy(overrides: Record<string, string>, defaults: Record<string, string> = DEFAULT_COPY) {
  return (key: CopyKey, vars: CopyVars = {}): string => {
    const tpl = overrides[key] ?? defaults[key]
    return tpl === undefined ? key : format(tpl, vars)
  }
}

export const CopyContext = createContext<CopyFn>(createCopy({}))

export function useCopy(): CopyFn {
  return useContext(CopyContext)
}
