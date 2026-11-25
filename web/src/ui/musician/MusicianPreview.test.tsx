import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE } from '../../api/client'
import { server } from '../../api/mocks/node'
import type { components } from '../../api/types.gen'
import { AudioEngineContext } from '../../core/audio/AudioRoot'
import type { AudioEngine, EngineState } from '../../core/audio/engine'
import { DesignContext, loadDesignConfig } from '../../core/design/config'
import { CopyContext, createCopy } from '../../core/i18n/copy'
import { LikesRoot } from '../../core/likes/LikesRoot'
import okean from '../../../../contracts/fixtures/musicians/okean-elzy.json'
import { MusicianPreview } from './MusicianPreview'

type Musician = components['schemas']['MusicianDetails']
type MapPin = components['schemas']['MapPin']

const t = createCopy({})
const MUSICIAN = okean as Musician
const PIN: MapPin = { id: MUSICIAN.id, slug: MUSICIAN.slug, name: MUSICIAN.name, kind: 'BAND', lat: 49.84, lon: 24.03, region: 'LVIV', placeId: 'Q36036', hasAudio: true }

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

function serve(m: Musician, hasAudio = m.hasAudio) {
  server.use(
    http.get(`${API_BASE}/musicians/${m.slug}`, () => HttpResponse.json({ ...m, hasAudio })),
    http.get(`${API_BASE}/musicians/${m.id}/fragment`, () =>
      hasAudio
        ? HttpResponse.json({ clipId: 'c1', title: 'Демоетюд 2', url: '/a.wav', startSec: 0, durationSec: 15, fadeInMs: 0, fadeOutMs: 0, source: 'UPLOAD', attribution: 'Тестове джерело звуку' })
        : new HttpResponse(null, { status: 204 }),
    ),
  )
}

function renderPreview(pin: MapPin, engine = fakeEngine().engine, onClose = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <DesignContext.Provider value={loadDesignConfig(null)}>
        <CopyContext.Provider value={t}>
          <AudioEngineContext.Provider value={engine}>
            <LikesRoot>
              <MemoryRouter>
                <div style={{ position: 'relative' }}>
                  <MusicianPreview pin={pin} onClose={onClose} />
                </div>
              </MemoryRouter>
            </LikesRoot>
          </AudioEngineContext.Provider>
        </CopyContext.Provider>
      </DesignContext.Provider>
    </QueryClientProvider>,
  )
}

beforeEach(() => vi.spyOn(console, 'warn').mockImplementation(() => {}))
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('MusicianPreview', () => {
  it('shows the name as the sheet title beside the close button right away', () => {
    serve(MUSICIAN)
    renderPreview(PIN)
    const head = screen.getByTestId('musician-preview').querySelector('.n-sheet__head') as HTMLElement
    expect(within(head).getByRole('heading', { name: 'Океан Ельзи' })).toBeTruthy()
    expect(within(head).getByRole('button', { name: t('common.close') })).toBeTruthy()
  })

  it('renders photo, origin place and notice, sound, like and the link to the page', async () => {
    serve(MUSICIAN)
    renderPreview(PIN)
    const card = screen.getByTestId('musician-preview')
    await waitFor(() => expect(within(card).getByTestId('likes-count').textContent).toMatch(/5\s?313/))
    expect(within(card).getByTestId('preview-photo').querySelector('img')).toBeTruthy()
    expect(card.textContent).toContain('Місце заснування: Львів')
    expect(within(card).getByTestId('preview-origin-notice').textContent).toBe(t('map.originNotice'))
    expect(within(card).getByTestId('audio-player')).toBeTruthy()
    expect(within(card).getByTestId('like-button')).toBeTruthy()
    const link = within(card).getByRole('link', { name: t('preview.openProfile') })
    expect(link.getAttribute('href')).toBe('/m/okean-elzy')
  })

  it('falls back to the monogram without a photo', async () => {
    serve({ ...MUSICIAN, images: [] })
    renderPreview(PIN)
    const photo = screen.getByTestId('preview-photo')
    await screen.findByTestId('likes-count')
    expect(photo.querySelector('img')).toBeNull()
    expect(photo.textContent).toBe('ОЕ')
  })

  it('shows musician.noAudio instead of the player when there is no audio', async () => {
    serve(MUSICIAN, false)
    renderPreview({ ...PIN, hasAudio: false })
    await screen.findByTestId('likes-count')
    expect(screen.queryByRole('button', { name: t('preview.play') })).toBeNull()
    expect(screen.getByText(t('musician.noAudio'))).toBeTruthy()
  })

  it('plays the fragment from the card and shows its title and source', async () => {
    serve(MUSICIAN)
    const { engine, plays } = fakeEngine()
    renderPreview(PIN, engine)
    fireEvent.click(await screen.findByRole('button', { name: t('preview.play') }))
    await waitFor(() => expect(plays).toEqual([MUSICIAN.id]))
    expect(await screen.findByText('Тестове джерело звуку')).toBeTruthy()
  })

  it('close button calls onClose', () => {
    serve(MUSICIAN)
    const onClose = vi.fn()
    renderPreview(PIN, undefined, onClose)
    fireEvent.click(screen.getByRole('button', { name: t('common.close') }))
    expect(onClose).toHaveBeenCalled()
  })
})
