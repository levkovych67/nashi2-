import {
  cp,
  mkdir,
  mkdtemp,
  open,
  readFile,
  readdir,
  rename,
  rm,
  stat,
  writeFile,
} from 'node:fs/promises'
import { basename, dirname, join, resolve } from 'node:path'
import { tokensToCss } from '../tokens/tokens'
import { studyMapStyle } from '../workbench/mapStyle'
import { inspectResources, reviewDecisions } from './review'
import { handoffMarkdown } from './document'

export interface HandoffOptions {
  decisions: unknown
  root: string
  outDir: string
  screens: boolean
  studioCommit: string
  now: Date
}
export interface HandoffResult {
  ok: boolean
  errors: string[]
  files: string[]
}
async function json(path: string) {
  return JSON.parse(await readFile(path, 'utf8'))
}
async function list(dir: string, prefix = ''): Promise<string[]> {
  const files: string[] = []
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = prefix + entry.name
    if (entry.isDirectory())
      files.push(...(await list(join(dir, entry.name), path + '/')))
    else if (entry.isFile()) files.push(path)
    else throw Error('Непідтримуваний ресурс ' + path)
  }
  return files.sort()
}
function assetErrors(files: string[]) {
  const errors: string[] = []
  for (const file of ['logo.svg', 'logo-mark.svg', 'favicon.svg'])
    if (!files.includes(file)) errors.push('Бракує assets/' + file)
  if (!files.some((path) => /^icons\/.+\.svg$/.test(path)))
    errors.push('Бракує іконок у assets/icons')
  const fonts = files.filter((path) => /^fonts\/.+\.woff2$/.test(path))
  if (!fonts.length) errors.push('Бракує локальних шрифтів')
  for (const font of fonts)
    if (!files.includes('fonts/LICENSE-' + basename(font, '.woff2') + '.txt'))
      errors.push('Бракує ліцензії для ' + font)
  return errors
}
const absent = (error: unknown) =>
  (error as NodeJS.ErrnoException).code === 'ENOENT'

export async function buildHandoff(
  options: HandoffOptions,
): Promise<HandoffResult> {
  const { root, screens, now, studioCommit } = options
  const { file, errors } = reviewDecisions(options.decisions, now, studioCommit)
  const failed = (): HandoffResult => ({ ok: false, errors, files: [] })
  if (errors.length) return failed()
  const output = resolve(options.outDir),
    parent = dirname(output)
  let staging: string | undefined
  let lock: Awaited<ReturnType<typeof open>> | undefined
  const lockFile = join(parent, '.' + basename(output) + '.lock')
  try {
    const identity = file.decisions.identity.variant
    const design = join(root, 'src', 'identities', identity)
    const [tokens, baseCopy, override, assets] = await Promise.all([
      json(join(design, 'tokens.json')),
      json(join(root, 'src/proto/copy/copy.uk.json')),
      json(join(design, 'copy.uk.json')),
      list(join(design, 'assets')),
    ])
    const {
      flat,
      copy,
      errors: resourceErrors,
    } = inspectResources(identity, tokens, baseCopy, override)
    errors.push(...resourceErrors, ...assetErrors(assets))
    if (errors.length) return failed()
    await mkdir(parent, { recursive: true })
    lock = await open(lockFile, 'wx')
    staging = await mkdtemp(join(parent, '.' + basename(output) + '-'))
    const target = join(staging, 'package')
    await mkdir(target)
    const writeJson = (key: string, value: unknown) =>
      writeFile(join(target, key), JSON.stringify(value, null, 2) + '\n')
    const writes = await Promise.allSettled([
      writeJson('decisions.json', file),
      writeJson('copy.uk.json', copy),
      writeJson('tokens.json', tokens),
      writeFile(join(target, 'tokens.css'), tokensToCss(':root', flat) + '\n'),
      writeJson(
        'map-style.json',
        studyMapStyle(
          file.decisions['map.basemap'].variant,
          '{{TILES_URL}}',
          '{{GLYPHS_URL}}',
          flat,
        ),
      ),
      cp(join(design, 'assets'), join(target, 'assets'), { recursive: true }),
    ])
    for (const result of writes)
      if (result.status === 'rejected') throw result.reason
    const shots = screens
      ? await (await import('./screens')).captureScreens(root, target, file)
      : []
    await writeFile(join(target, 'HANDOFF.md'), handoffMarkdown(file, shots))
    const files = await list(target)
    const backup = join(staging, 'previous')
    let previous = false
    try {
      if (!(await stat(output)).isDirectory())
        throw Error('Шлях передачі має бути текою')
      if (
        (await readdir(output)).length &&
        !(await list(output)).includes('HANDOFF.md')
      )
        throw Error('Тека призначення містить сторонні файли')
      await rename(output, backup)
      previous = true
    } catch (err) {
      if (!absent(err)) throw err
    }
    try {
      await rename(target, output)
    } catch (err) {
      if (previous)
        try {
          await rename(backup, output)
        } catch {
          staging = undefined
          throw Error(
            'Попередній пакет збережено у ' +
              backup +
              '. Публікація не вдалася.',
          )
        }
      throw err
    }
    return { ok: true, errors: [], files }
  } catch (err) {
    errors.push(err instanceof Error ? err.message : String(err))
    return failed()
  } finally {
    if (staging) await rm(staging, { recursive: true, force: true })
    if (lock) {
      await lock.close()
      await rm(lockFile, { force: true })
    }
  }
}
