import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  REQUIRED_COPY_KEYS, REQUIRED_TOKENS, TECHNICAL_VARIANTS, tokenToCssVar,
} from './contract-lists.mjs'
import { syncDesign } from './design-sync.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const tmp = () => mkdtempSync(join(tmpdir(), 'design-sync-'))

function makeHandoff(overrides = {}) {
  const dir = tmp()
  const files = {
    'decisions.json': JSON.stringify({
      schemaVersion: 1,
      decisions: {
        identity: { variant: 'x', status: 'PROPOSED' },
        'audio.mobile-trigger': { variant: 'LONG_PRESS_PLAY', status: 'PROPOSED' },
      },
    }),
    'tokens.css': `:root{${REQUIRED_TOKENS.map((t) => `${tokenToCssVar(t)}:1;`).join('')}}`,
    'map-style.json': JSON.stringify({ glyphs: '{{GLYPHS_URL}}', sources: { s: { url: '{{TILES_URL}}' } } }),
    'copy.uk.json': JSON.stringify(Object.fromEntries(REQUIRED_COPY_KEYS.map((k) => [k, 'x']))),
    ...overrides,
  }
  for (const [name, content] of Object.entries(files)) writeFileSync(join(dir, name), content)
  mkdirSync(join(dir, 'assets', 'icons'), { recursive: true })
  writeFileSync(join(dir, 'assets', 'icons', 'a.svg'), '<svg/>')
  return dir
}

test('copies a valid handoff (PROPOSED status is accepted) including assets', () => {
  const src = makeHandoff()
  const dest = join(tmp(), 'design')
  syncDesign(src, dest)
  for (const f of ['decisions.json', 'tokens.css', 'map-style.json', 'copy.uk.json', 'assets/icons/a.svg']) {
    assert.ok(existsSync(join(dest, f)), f)
  }
  assert.equal(readFileSync(join(dest, 'tokens.css'), 'utf8'), readFileSync(join(src, 'tokens.css'), 'utf8'))
})

test('fails listing missing tokens', () => {
  const css = `:root{${REQUIRED_TOKENS.filter((t) => t !== 'color.like').map((t) => `${tokenToCssVar(t)}:1;`).join('')}}`
  const src = makeHandoff({ 'tokens.css': css })
  assert.throws(() => syncDesign(src, join(tmp(), 'd')), /color\.like/)
})

test('does not confuse a token with a longer one sharing its prefix', () => {
  const css = `:root{${REQUIRED_TOKENS.filter((t) => t !== 'color.map.road').map((t) => `${tokenToCssVar(t)}:1;`).join('')}--n-color-map-road-x:1;}`
  assert.throws(() => syncDesign(makeHandoff({ 'tokens.css': css }), join(tmp(), 'd')), /color\.map\.road/)
})

test('fails on missing copy key', () => {
  const copy = Object.fromEntries(REQUIRED_COPY_KEYS.filter((k) => k !== 'list.empty').map((k) => [k, 'x']))
  assert.throws(() => syncDesign(makeHandoff({ 'copy.uk.json': JSON.stringify(copy) }), join(tmp(), 'd')), /list\.empty/)
})

test('fails on schemaVersion other than 1', () => {
  const src = makeHandoff({ 'decisions.json': JSON.stringify({ schemaVersion: 2, decisions: {} }) })
  assert.throws(() => syncDesign(src, join(tmp(), 'd')), /schemaVersion/)
})

test('fails on unknown technical variant', () => {
  const src = makeHandoff({
    'decisions.json': JSON.stringify({
      schemaVersion: 1,
      decisions: { 'audio.mobile-trigger': { variant: 'SHAKE', status: 'APPROVED' } },
    }),
  })
  assert.throws(() => syncDesign(src, join(tmp(), 'd')), /audio\.mobile-trigger.*SHAKE/)
})

test('fails when map style has no placeholders', () => {
  const src = makeHandoff({ 'map-style.json': JSON.stringify({ glyphs: 'https://x', sources: {} }) })
  assert.throws(() => syncDesign(src, join(tmp(), 'd')), /TILES_URL[\s\S]*GLYPHS_URL|GLYPHS_URL[\s\S]*TILES_URL/)
})

test('failure copies nothing', () => {
  const dest = join(tmp(), 'd')
  assert.throws(() => syncDesign(makeHandoff({ 'map-style.json': '{}' }), dest))
  assert.equal(existsSync(dest), false)
})

test('CLI exits non-zero with the list on failure and zero on success', () => {
  const bad = spawnSync('node', [join(here, 'design-sync.mjs'), makeHandoff({ 'map-style.json': '{}' }), join(tmp(), 'd')])
  assert.equal(bad.status, 1)
  assert.match(bad.stderr.toString(), /TILES_URL/)
  const ok = spawnSync('node', [join(here, 'design-sync.mjs'), makeHandoff(), join(tmp(), 'd')])
  assert.equal(ok.status, 0)
})

// The checked-in lists must match docs/02-contracts.md.
const doc = readFileSync(join(here, '../../docs/02-contracts.md'), 'utf8')
const section = (n) => doc.split(new RegExp(`^## ${n}\\. `, 'm'))[1].split(/^## /m)[0]

test('contract-lists: tokens match section 5', () => {
  const rows = section(5).split('\n').filter((l) => /^\| (Кольори|Піни|Типографіка|Форма|Рух|Розміри)/.test(l))
  const plain = rows.flatMap((l) => [...l.split('|')[2].matchAll(/`([^`]+)`/g)].map((m) => m[1]))
    .filter((t) => !/…/.test(t) && !t.includes('(') )
  assert.ok(plain.length > 40)
  for (const t of plain) assert.ok(REQUIRED_TOKENS.includes(t), `token ${t} from doc missing in list`)
  // ranges
  for (const t of ['font.size.xs', 'font.size.3xl', 'space.1', 'space.12']) assert.ok(REQUIRED_TOKENS.includes(t))
  assert.equal(REQUIRED_TOKENS.filter((t) => t.startsWith('font.size.')).length, 7)
  assert.equal(REQUIRED_TOKENS.filter((t) => t.startsWith('space.')).length, 12)
  assert.equal(new Set(REQUIRED_TOKENS).size, REQUIRED_TOKENS.length)
  // every list entry is mentioned in the doc (directly or via a range)
  const direct = new Set(plain)
  const extra = REQUIRED_TOKENS.filter((t) => !direct.has(t) && !/^(font\.size|space)\./.test(t))
  assert.deepEqual(extra, [])
})

test('contract-lists: technical variants match section 6', () => {
  const parsed = {}
  for (const l of section(6).split('\n')) {
    const cols = l.split('|').map((s) => s.trim())
    const id = /^`([^`]+)`$/.exec(cols[1] ?? '')?.[1]
    if (!id || /вільні/.test(cols[3])) continue
    parsed[id] = [...cols[3].matchAll(/`([A-Z_]+)`/g)].map((m) => m[1])
  }
  assert.deepEqual(parsed, TECHNICAL_VARIANTS)
})

test('contract-lists: required copy keys match section 8', () => {
  const body = section(8)
  const keys = [...body.slice(body.indexOf('Обов')).matchAll(/`([^`]+)`/g)].map((m) => m[1])
  const staticKeys = keys.filter((k) => !k.includes('<') && !k.startsWith('.'))
  for (const k of staticKeys) assert.ok(REQUIRED_COPY_KEYS.includes(k), `copy key ${k} from doc missing in list`)
  assert.ok(REQUIRED_COPY_KEYS.some((k) => k.startsWith('submit.field.name.')))
  assert.ok(REQUIRED_COPY_KEYS.some((k) => k.startsWith('submit.error.')))
})
