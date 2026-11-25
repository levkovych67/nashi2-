type Leaf = { type: string; value: unknown }
export type FlatTokens = Record<string, Leaf>
const group = (path: string, keys: string) =>
  keys.split(' ').map((key) => path + '.' + key)
export const REQUIRED_TOKEN_PATHS = [
  ...group(
    'color',
    'bg surface surface-raised overlay border text text-muted text-inverse accent accent-hover on-accent like on-like success danger focus',
  ),
  ...group(
    'color.map',
    'land water road label label-halo ua-border region-line region-fill-active',
  ),
  ...group(
    'color.pin',
    'ring ring-audio ring-playing fallback-bg fallback-text',
  ),
  ...group('color.cluster', 'bg text ring'),
  ...group('font.family', 'display body'),
  ...group('font.weight', 'regular medium bold'),
  ...group('font.size', 'xs sm md lg xl 2xl 3xl'),
  ...group('font.line-height', 'tight normal'),
  ...group('radius', 'sm md lg pill'),
  ...Array.from({ length: 12 }, (_, i) => 'space.' + (i + 1)),
  ...group('shadow', 'sm md lg'),
  ...group('motion.duration', 'fast base slow map-fly'),
  ...group('motion.easing', 'standard emphasized'),
  ...group('size', 'pin.sm pin.md pin.lg touch-min sheet.peek'),
]
export function flattenTokens(tree: object): FlatTokens {
  const flat: FlatTokens = {}
  function visit(node: Record<string, unknown>, path: string, type: string) {
    const next = typeof node.$type === 'string' ? node.$type : type
    if ('$value' in node) {
      flat[path] = { type: next, value: node.$value }
      return
    }
    for (const [key, item] of Object.entries(node))
      if (!key.startsWith('$') && item && typeof item === 'object')
        visit(
          item as Record<string, unknown>,
          path ? path + '.' + key : key,
          next,
        )
  }
  visit(tree as Record<string, unknown>, '', '')
  function resolve(key: string, trail: string[]): unknown {
    if (trail.includes(key))
      throw new Error('Circular alias: ' + [...trail, key].join(' / '))
    const token = flat[key]
    if (!token) throw new Error('Unknown token ' + key)
    const alias =
      typeof token.value === 'string' && /^\{([^}]+)\}$/.exec(token.value)
    return alias ? resolve(alias[1], [...trail, key]) : token.value
  }
  for (const key of Object.keys(flat)) flat[key].value = resolve(key, [])
  return flat
}
export const missingTokens = (flat: FlatTokens) =>
  REQUIRED_TOKEN_PATHS.filter((key) => !(key in flat))
function dimension(value: unknown) {
  const v = value as { value: number; unit: string }
  return String(v.value) + v.unit
}
function cssValue({ type, value }: Leaf): string {
  switch (type) {
    case 'duration':
    case 'dimension':
      return dimension(value)
    case 'cubicBezier':
      return 'cubic-bezier(' + (value as number[]).join(', ') + ')'
    case 'fontFamily':
      return (value as string[])
        .map((s) =>
          /^(serif|sans-serif|monospace|system-ui)$/.test(s)
            ? s
            : JSON.stringify(s),
        )
        .join(', ')
    case 'shadow': {
      const s = value as Record<string, unknown>
      return (
        [s.offsetX, s.offsetY, s.blur, s.spread].map(dimension).join(' ') +
        ' ' +
        s.color
      )
    }
    default:
      return String(value)
  }
}
export function tokensToCss(selector: string, flat: FlatTokens) {
  return (
    selector +
    '{' +
    Object.entries(flat)
      .map(
        ([path, token]) =>
          '--n-' + path.replaceAll('.', '-') + ':' + cssValue(token) + ';',
      )
      .join('') +
    '}'
  )
}
function luminance(hex: string) {
  if (!/^#[0-9a-f]{6}$/i.test(hex)) throw new Error('Невірний колір ' + hex)
  const channels = hex
    .slice(1)
    .match(/../g)!
    .map((v) => parseInt(v, 16) / 255)
    .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
}
export function contrastRatio(a: string, b: string) {
  const pair = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (pair[0] + 0.05) / (pair[1] + 0.05)
}
const pairs = [
  ['text', 'bg', 4.5],
  ['text', 'surface', 4.5],
  ['on-accent', 'accent', 4.5],
  ['on-like', 'like', 4.5],
  ['text-muted', 'surface', 3],
  ['cluster.text', 'cluster.bg', 3],
] as const
export function checkContrast(flat: FlatTokens) {
  return pairs
    .map(([a, b, min]) => ({
      pair: 'color.' + a + ' on color.' + b,
      min,
      ratio: contrastRatio(
        String(flat['color.' + a].value),
        String(flat['color.' + b].value),
      ),
    }))
    .filter((result) => result.ratio < result.min)
}
