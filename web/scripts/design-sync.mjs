// Copies the studio handoff package into web/src/design after validating it against docs/02-contracts.md.
// Usage: node scripts/design-sync.mjs <handoffDir> <destDir>
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  FREE_ID_DECISIONS, REQUIRED_COPY_KEYS, REQUIRED_TOKENS, TECHNICAL_VARIANTS, tokenToCssVar,
} from './contract-lists.mjs'

const FILES = ['decisions.json', 'tokens.css', 'map-style.json', 'copy.uk.json']

function readJson(dir, name, errors) {
  const path = join(dir, name)
  if (!existsSync(path)) {
    errors.push(`${name}: file is missing`)
    return null
  }
  try {
    return JSON.parse(readFileSync(path, 'utf8'))
  } catch (e) {
    errors.push(`${name}: invalid JSON (${e.message})`)
    return null
  }
}

/** Returns the list of validation errors (empty when the package is valid). Status PROPOSED is valid. */
export function validateHandoff(dir) {
  const errors = []

  const decisions = readJson(dir, 'decisions.json', errors)
  if (decisions) {
    if (decisions.schemaVersion !== 1) errors.push(`decisions.json: schemaVersion must be 1, got ${decisions.schemaVersion}`)
    for (const [id, entry] of Object.entries(decisions.decisions ?? {})) {
      if (FREE_ID_DECISIONS.includes(id)) continue
      const allowed = TECHNICAL_VARIANTS[id]
      if (!allowed) errors.push(`decisions.json: unknown decision "${id}"`)
      else if (!allowed.includes(entry?.variant)) {
        errors.push(`decisions.json: ${id} = "${entry?.variant}" is not a technical variant (allowed: ${allowed.join(', ')})`)
      }
    }
  }

  const tokensPath = join(dir, 'tokens.css')
  if (!existsSync(tokensPath)) errors.push('tokens.css: file is missing')
  else {
    const css = readFileSync(tokensPath, 'utf8')
    const missing = REQUIRED_TOKENS.filter((t) => !new RegExp(`${tokenToCssVar(t)}\\s*:`).test(css))
    if (missing.length) errors.push(`tokens.css: missing tokens: ${missing.join(', ')}`)
  }

  const style = join(dir, 'map-style.json')
  if (!existsSync(style)) errors.push('map-style.json: file is missing')
  else {
    const text = readFileSync(style, 'utf8')
    for (const p of ['{{TILES_URL}}', '{{GLYPHS_URL}}']) {
      if (!text.includes(p)) errors.push(`map-style.json: placeholder ${p} is missing`)
    }
  }

  const copy = readJson(dir, 'copy.uk.json', errors)
  if (copy) {
    const missing = REQUIRED_COPY_KEYS.filter((k) => typeof copy[k] !== 'string')
    if (missing.length) errors.push(`copy.uk.json: missing keys: ${missing.join(', ')}`)
  }
  return errors
}

export function syncDesign(handoffDir, destDir) {
  const errors = validateHandoff(handoffDir)
  if (errors.length) throw new Error(`design:sync failed:\n - ${errors.join('\n - ')}`)
  mkdirSync(destDir, { recursive: true })
  for (const f of FILES) cpSync(join(handoffDir, f), join(destDir, f))
  const assetsDest = join(destDir, 'assets')
  rmSync(assetsDest, { recursive: true, force: true })
  if (existsSync(join(handoffDir, 'assets'))) cpSync(join(handoffDir, 'assets'), assetsDest, { recursive: true })
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [src, dest] = process.argv.slice(2)
  if (!src || !dest) {
    console.error('usage: design-sync.mjs <handoffDir> <destDir>')
    process.exit(2)
  }
  try {
    syncDesign(resolve(src), resolve(dest))
    console.log(`design:sync ok: ${src} -> ${dest}`)
  } catch (e) {
    console.error(e.message)
    process.exit(1)
  }
}
