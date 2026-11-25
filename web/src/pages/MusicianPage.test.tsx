import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE } from '../api/client'
import { server } from '../api/mocks/node'
import type { components } from '../api/types.gen'
import { AudioEngineContext } from '../core/audio/AudioRoot'
import type { AudioEngine, EngineState } from '../core/audio/engine'
import { DesignContext, loadDesignConfig } from '../core/design/config'
import { CopyContext, createCopy } from '../core/i18n/copy'
import { LikesRoot } from '../core/likes/LikesRoot'
import okean from '../../../contracts/fixtures/musicians/okean-elzy.json'
import noImages from '../../../contracts/fixtures/musicians/999.json'
import MusicianPage from './MusicianPage'
import NotFoundPage from './NotFoundPage'

type Musician = components['schemas']['MusicianDetails']

const t = createCopy({})
const FULL: Musician = {
  ...(okean as Musician),
  tagline: 'Український рок-гурт зі Львова',
  materials: {
    clips: (okean as Musician).materials.clips,
    works: [{ title: 'Земля', year: 2013, type: 'ALBUM', url: 'https://example.com/zemlya' }],
    videos: [{ youtubeId: 'dQw4w9WgXcQ', title: 'Не вдавайся у відчай' }],
  },
}
const NO_IMAGES = noImages as Musician

function fakeEngine() {
  let state: EngineState = { status: 'idle', musicianId: null, clipId: null }
  const subs = new Set<(s: EngineState) => void>()
  const set = (s: EngineState) => {
    state = s
    subs.forEach((l) => l(s))
  }
  const plays: string[] = []
  const engine: AudioEngine = {
    async play(id, f) {
      plays.push(id)
      set({ status: 'playing', musicianId: id, clipId: f.clipId })
    },
    unlock: async () => true,
    stop: () => set({ status: 'idle', musicianId: null, clipId: null }),
    getState: () => state,
    subscribe: (l) => (subs.add(l), () => subs.delete(l)),
  }
  return { engine, plays }
}

function serve(m: Musician) {
  server.use(
    http.get(`${API_BASE}/musicians/${m.slug}`, () => HttpResponse.json(m)),
    http.get(`${API_BASE}/musicians/${m.id}/fragment`, () =>
      m.hasAudio
        ? HttpResponse.json({ clipId: 'c1', title: 'Демоетюд 2', url: '/a.wav', startSec: 0, durationSec: 15, fadeInMs: 0, fadeOutMs: 0, source: 'UPLOAD', attribution: 'Тестове джерело звуку' })
        : new HttpResponse(null, { status: 204 }),
    ),
  )
}

function renderPage(slug: string, engine = fakeEngine().engine) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <DesignContext.Provider value={loadDesignConfig(null)}>
        <CopyContext.Provider value={t}>
          <AudioEngineContext.Provider value={engine}>
            <LikesRoot>
              <MemoryRouter initialEntries={[`/m/${slug}`]}>
                <Routes>
                  <Route path="/m/:slug" element={<MusicianPage />} />
                  <Route path="*" element={<NotFoundPage />} />
                </Routes>
              </MemoryRouter>
            </LikesRoot>
          </AudioEngineContext.Provider>
        </CopyContext.Provider>
      </DesignContext.Provider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.spyOn(console, 'warn').mockImplementation(() => {}) // "variant not implemented" notes while decisions are PROPOSED
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('MusicianPage', () => {
  it('renders all required blocks', async () => {
    serve(FULL)
    renderPage(FULL.slug)
    expect((await screen.findByTestId('musician-name')).textContent).toContain('Океан Ельзи')
    for (const id of ['musician-tagline', 'musician-summary', 'musician-image', 'musician-materials', 'musician-music-links', 'musician-youtube', 'musician-origin', 'like-button', 'likes-count']) {
      expect(screen.getByTestId(id), id).toBeTruthy()
    }
    expect(within(screen.getByTestId('musician-origin')).getByTestId('origin-notice').textContent).toContain(t('map.originNotice'))
    expect(screen.getByTestId('musician-origin').textContent).toContain('Місце заснування: Львів')
    expect(screen.getByTestId('likes-count').textContent).toMatch(/5\s?313/)
  })

  it('sets the document title and description', async () => {
    serve(FULL)
    renderPage(FULL.slug)
    await screen.findByTestId('musician-name')
    await waitFor(() => expect(document.title).toContain('Океан Ельзи'))
    expect(document.head.querySelector('meta[name="description"]')?.getAttribute('content')).toContain('український рок-гурт')
  })

  it('hides the listen button and shows musician.noAudio when hasAudio is false', async () => {
    serve({ ...FULL, hasAudio: false })
    renderPage(FULL.slug)
    await screen.findByTestId('musician-name')
    expect(screen.queryByRole('button', { name: t('preview.play') })).toBeNull()
    expect(within(screen.getByTestId('musician-materials')).getByText(t('musician.noAudio'))).toBeTruthy()
  })

  it('plays a fragment of the musician and shows its title and source next to the controls', async () => {
    serve(FULL)
    const { engine, plays } = fakeEngine()
    renderPage(FULL.slug, engine)
    fireEvent.click(await screen.findByRole('button', { name: t('preview.play') }))
    await waitFor(() => expect(plays).toEqual([FULL.id]))
    expect(await screen.findByText('Тестове джерело звуку')).toBeTruthy()
    expect(screen.getByRole('button', { name: t('preview.stop') })).toBeTruthy()
    expect(screen.getByRole('button', { name: t('musician.nextFragment') })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: t('preview.stop') }))
    expect(await screen.findByRole('button', { name: t('preview.play') })).toBeTruthy()
  })

  it('shows the CC BY-SA source under the Wikipedia text and the photo credit', async () => {
    serve(FULL)
    renderPage(FULL.slug)
    const source = await screen.findByTestId('text-source')
    expect(source.textContent).toContain('CC BY-SA 4.0')
    expect(within(source).getByRole('link', { name: /Океан Ельзи/ }).getAttribute('href')).toBe(FULL.textSource!.url)
    expect(within(source).getByRole('link', { name: 'CC BY-SA 4.0' }).getAttribute('href')).toBe(FULL.textSource!.licenseUrl)
    const credit = screen.getByTestId('photo-credit')
    expect(credit.textContent).toContain('Alexander Khanin')
    expect(credit.textContent).toContain('CC BY-SA 4.0')
    expect(within(credit).getAllByRole('link').every((a) => a.getAttribute('rel') === 'noopener noreferrer' && a.getAttribute('target') === '_blank')).toBe(true)
  })

  it('renders without images', async () => {
    serve(NO_IMAGES)
    renderPage(NO_IMAGES.slug)
    expect(await screen.findByTestId('musician-name')).toBeTruthy()
    expect(screen.queryByRole('img')).toBeNull()
    expect(screen.queryByTestId('photo-credit')).toBeNull()
    expect(screen.getByTestId('musician-image')).toBeTruthy() // the monogram placeholder keeps the layout
    expect(screen.getByTestId('text-source').textContent).toContain('CC BY-SA 4.0')
  })

  it('opens external links in a new tab and drops unsafe urls', async () => {
    serve({
      ...FULL,
      links: [
        { platform: 'SPOTIFY', group: 'MUSIC', url: 'https://open.spotify.com/artist/1' },
        { platform: 'OTHER', group: 'MUSIC', url: 'javascript:alert(1)' },
        { platform: 'OTHER', group: 'SOCIAL', url: 'data:text/html,x' },
      ],
    })
    renderPage(FULL.slug)
    const block = await screen.findByTestId('musician-music-links')
    const links = within(block).getAllByRole('link')
    expect(links).toHaveLength(1)
    expect(links[0].getAttribute('target')).toBe('_blank')
    expect(links[0].getAttribute('rel')).toBe('noopener noreferrer')
    expect(document.querySelector('a[href^="javascript"], a[href^="data"]')).toBeNull()
  })

  it('loads the YouTube iframe only after the click', async () => {
    serve(FULL)
    renderPage(FULL.slug)
    const block = await screen.findByTestId('musician-youtube')
    expect(block.querySelector('iframe')).toBeNull()
    fireEvent.click(within(block).getByRole('button', { name: t('musician.playVideo', { title: 'Не вдавайся у відчай' }) }))
    expect(block.querySelector('iframe')?.getAttribute('src')).toBe('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ')
  })

  it('links to the map with the musician slug', async () => {
    serve(FULL)
    renderPage(FULL.slug)
    expect((await screen.findByRole('link', { name: t('musician.showOnMap') })).getAttribute('href')).toBe('/?musician=okean-elzy')
  })

  it('every tap on the heart adds one like at once and shows the burst', async () => {
    serve(FULL)
    renderPage(FULL.slug)
    const button = await screen.findByTestId('like-button')
    for (let i = 0; i < 3; i++) fireEvent.click(button)
    expect(screen.getByTestId('likes-count').textContent).toMatch(/5\s?316/)
    expect(button.querySelectorAll('[data-burst]').length).toBeGreaterThan(0)
  })

  it('shows not found with a link to the map for an unknown slug', async () => {
    renderPage('no-such-musician')
    expect(await screen.findByText(t('errors.notFound'))).toBeTruthy()
    expect(screen.getByRole('link', { name: t('errors.backToMap') }).getAttribute('href')).toBe('/')
  })
})
