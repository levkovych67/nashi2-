import { execFileSync } from 'node:child_process'
import { readFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildHandoff } from '../src/handoff/build'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
try {
  const argv = process.argv.slice(2)
  const args = new Set(argv)
  if (argv.some((arg) => arg !== '--no-screens'))
    throw Error('Доступний прапорець --no-screens')
  const decisions = JSON.parse(
    await readFile(resolve(root, 'decisions/decisions.json'), 'utf8'),
  )
  const studioCommit =
    process.env.VITE_STUDIO_COMMIT ||
    execFileSync('git', ['rev-parse', 'HEAD'], {
      cwd: root,
      encoding: 'utf8',
    }).trim()
  const result = await buildHandoff({
    decisions,
    root,
    outDir: resolve(root, 'handoff'),
    screens: !args.has('--no-screens'),
    studioCommit,
    now: new Date(),
  })
  if (!result.ok) throw Error(result.errors.join('\n'))
  process.stdout.write(
    'Пакет studio/handoff/ зібрано. Файлів — ' + result.files.length + '.\n',
  )
} catch (err) {
  process.stderr.write(
    (err instanceof Error ? err.message : String(err)) + '\n',
  )
  process.exitCode = 1
}
