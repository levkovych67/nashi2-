// @vitest-environment node
import { mkdtemp, readFile, writeFile, rm, cp, mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { buildHandoff } from '../../src/handoff/build'
import { reviewDecisions } from '../../src/handoff/review'
import { DECISIONS } from '../../src/decisions/catalog'

const root = resolve(import.meta.dirname, '../..')
const now = new Date('2026-11-18T10:00:00Z')
let workspace: string
beforeEach(async () => {
  workspace = await mkdtemp(join(tmpdir(), 'nashi-handoff-test-'))
})
afterEach(async () => {
  await rm(workspace, { recursive: true, force: true })
})
const run = (decisions: unknown, source = root) =>
  buildHandoff({
    decisions: { schemaVersion: 1, decisions },
    root: source,
    outDir: join(workspace, 'bundle'),
    screens: false,
    studioCommit: 'test-commit',
    now,
  })

describe('передача дизайн-рішень', () => {
  it('включає всі 17 рішень і позначає початкові значення як пропозиції', async () => {
    const r = await run({
      'like.button': {
        status: 'PROPOSED',
        variant: 'APPLAUSE',
        params: { maxBatch: 50 },
      },
    })
    expect(r.errors).toEqual([])
    expect(r.files).toEqual(
      expect.arrayContaining([
        'decisions.json',
        'tokens.css',
        'tokens.json',
        'map-style.json',
        'copy.uk.json',
        'HANDOFF.md',
        'assets/logo.svg',
      ]),
    )
    const read = (path: string) =>
      readFile(join(workspace, 'bundle', path), 'utf8')
    const file = JSON.parse(await read('decisions.json'))
    expect(Object.keys(file.decisions)).toHaveLength(17)
    expect(file.decisions['like.button']).toMatchObject({
      variant: 'APPLAUSE',
      params: { maxBatch: 50 },
    })
    expect(
      Object.values(file.decisions).every(
        (e) => (e as { status: string }).status === 'PROPOSED',
      ),
    ).toBe(true)
    expect(file.generatedAt).toBe(now.toISOString())
    expect(file.studioCommit).toBe('test-commit')
    expect((await read('tokens.css')).match(/--n-[\w-]+:/g)).toHaveLength(76)
    const style = await read('map-style.json')
    expect(style).toContain('{{TILES_URL}}')
    expect(style).toContain('{{GLYPHS_URL}}')
    const text = await read('HANDOFF.md')
    for (const d of DECISIONS) expect(text).toContain('## ' + d.id)
    expect(text).toContain('⚠️ like.button — НЕ ЗАТВЕРДЖЕНО')
    expect(text).toMatch(/A-\d\d/)
  })
  it('зберігає попередній пакет після помилки і відрізняє доповнення від обраного варіанта', async () => {
    expect((await run({})).ok).toBe(true)
    const path = join(workspace, 'bundle', 'decisions.json')
    const original = await readFile(path, 'utf8')
    const r = await run({
      'map.origin-notice': { variant: 'PIN_CARD_LABEL', status: 'PROPOSED' },
      'map.pin': { variant: 'UNKNOWN', status: 'APPROVED' },
    })
    expect(r.ok).toBe(false)
    expect(r.errors).toHaveLength(2)
    expect(await readFile(path, 'utf8')).toBe(original)
  })
  it('відхиляє неповні реквізити затвердження і неправильні параметри', () => {
    for (const entry of [
      { status: 'APPROVED', variant: 'atlas' },
      {
        status: 'APPROVED',
        variant: 'atlas',
        approvedBy: ' ',
        approvedAt: '2026-11-18',
      },
      {
        status: 'APPROVED',
        variant: 'atlas',
        approvedBy: 'Учасник',
        approvedAt: '2026-02-30',
      },
      { status: 'PROPOSED', variant: 'atlas', approvedAt: '2026-11-18' },
      {
        status: 'APPROVED',
        variant: 'atlas',
        approvedAt: '2026-11-18',
        approvedBy: 'Учасник',
        params: { x: [1] },
      },
    ])
      expect(
        reviewDecisions({ schemaVersion: 1, decisions: { identity: entry } })
          .errors.length,
      ).toBeGreaterThan(0)
    const entry = {
      status: 'APPROVED',
      variant: 'atlas',
      approvedBy: 'Учасник',
      approvedAt: '2026-11-18',
    }
    expect(
      reviewDecisions({ schemaVersion: 1, decisions: { identity: entry } }).file
        .decisions.identity,
    ).toEqual(entry)
  })
  it('перевіряє ресурси з обраного кореня і не створює неповний пакет', async () => {
    const source = join(workspace, 'studio')
    await mkdir(join(source, 'src/identities'), { recursive: true })
    await cp(
      join(root, 'src/identities/atlas'),
      join(source, 'src/identities/atlas'),
      { recursive: true },
    )
    await mkdir(join(source, 'src/proto/copy'), { recursive: true })
    const tokenPath = join(source, 'src/identities/atlas/tokens.json')
    const tokens = JSON.parse(await readFile(tokenPath, 'utf8'))
    delete tokens.color.like
    await writeFile(tokenPath, JSON.stringify(tokens))
    await writeFile(join(source, 'src/proto/copy/copy.uk.json'), '{}')
    const r = await run({}, source)
    expect(r.errors.join('\n')).toContain('color.like')
    expect(r.errors.join('\n')).toContain('map.originNotice')
    await expect(
      readFile(join(workspace, 'bundle/decisions.json')),
    ).rejects.toThrow()
  })
})
