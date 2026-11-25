import { readFile } from 'node:fs/promises'
import { cleanup, render, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeAll, afterAll, expect, it, vi } from 'vitest'
import { variants } from '../../src/decisions/variants'
import { Fullscreen } from '../../src/workbench/Workbench'

// jsdom has no WebGL context. Exercise the same fallback used on unsupported devices.
vi.mock('maplibre-gl', () => ({
  default: {Map: class {constructor(){throw new Error('WebGL unavailable')}}}
}))

beforeAll(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  )
  vi.stubGlobal('fetch', async (path: string) => {
    const json = await readFile(
      new URL('../../../contracts' + path, import.meta.url),
      'utf8',
    )
    return { ok: true, json: () => JSON.parse(json) }
  })
})
afterEach(cleanup)
afterAll(() => vi.unstubAllGlobals())

for (const [id, options] of Object.entries(variants)) {
  it('opens every direct variant of ' + id, async () => {
    for (const option of options) {
      const app = render(
        <MemoryRouter
          initialEntries={['/v/' + id + '/' + option.id + '?identity=ornament']}
        >
          <Routes>
            <Route path="/v/:decisionId/:variantId" element={<Fullscreen />} />
          </Routes>
        </MemoryRouter>,
      )
      await waitFor(() => {
        const prototype = app.container.querySelector('.full-demo .proto')
        expect(prototype?.getAttribute('data-decision')).toBe(id)
        expect(prototype?.getAttribute('data-variant')).toBe(option.id)
        expect(prototype?.getAttribute('data-identity')).toBe(
          id === 'identity' ? option.id : 'ornament',
        )
        expect(prototype?.querySelector('button,input,select')).toBeTruthy()
      })
      expect(app.container.textContent).not.toContain('Прототипи в роботі')
      for (const a of app.container.querySelectorAll('a'))
        expect(a.textContent).not.toMatch(/[↗↖→←]/)
      app.unmount()
    }
  })
}
