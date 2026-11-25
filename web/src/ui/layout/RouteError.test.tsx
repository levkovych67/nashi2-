import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RouteError } from './RouteError'

function Boom({ error }: { error: Error }): never {
  throw error
}

function renderFailing(error: Error, reload = vi.fn()) {
  const router = createMemoryRouter([
    { errorElement: <RouteError reload={reload} />, children: [{ path: '/', element: <Boom error={error} /> }] },
  ])
  render(<RouterProvider router={router} />)
  return reload
}

const chunkError = () => new TypeError('Failed to fetch dynamically imported module: http://x/assets/MusicianPage-abc.js')

describe('RouteError', () => {
  afterEach(() => {
    cleanup()
    window.sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('a page that breaks shows a Ukrainian message with a reload and a way back to the map', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {}) // React logs the caught render error
    const reload = renderFailing(new Error('boom'))
    expect(screen.getByRole('alert').textContent).toContain('Не вдалося показати сторінку.')
    expect(screen.getByRole('link', { name: 'Повернутися до карти' }).getAttribute('href')).toBe('/')
    fireEvent.click(screen.getByRole('button', { name: 'Оновити сторінку' }))
    expect(reload).toHaveBeenCalledTimes(1)
    expect(document.body.textContent).not.toMatch(/Unexpected Application Error/)
  })

  it('a missing chunk after a deploy reloads the page once', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const reload = renderFailing(chunkError())
    expect(reload).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('a chunk that is still missing after that reload shows the message instead of reloading in a loop', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    renderFailing(chunkError())
    cleanup()
    const again = renderFailing(chunkError())
    expect(again).not.toHaveBeenCalled()
    expect(screen.getByRole('alert').textContent).toContain('Не вдалося показати сторінку.')
  })
})
