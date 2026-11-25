import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/types.gen'
import { useMapController } from './useMapController'

type MapPin = components['schemas']['MapPin']

const player = vi.hoisted(() => ({
  state: { status: 'idle', musicianId: null as string | null, clipId: null as string | null },
  previewId: null as string | null,
  noAudio: new Set<string>(),
  pointerEnter: vi.fn(),
  pointerLeave: vi.fn(),
  gesture: vi.fn(),
  playMusician: vi.fn(),
  stop: vi.fn(),
}))
vi.mock('../../core/audio/useFragmentPlayer', () => ({ useFragmentPlayer: () => ({ ...player }) }))

// A pin from contracts/fixtures/map-pins.json, served by MSW.
const okeanElzy: MapPin = {
  id: '945a9193544ad1ca7a278141', slug: 'okean-elzy', name: 'Океан Ельзи', kind: 'BAND', hasAudio: true,
  lat: 49.828536358078686, lon: 24.021302050093865, region: 'LVIV', placeId: 'Q36036',
}

function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/']}>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return renderHook(() => useMapController(), { wrapper })
}

beforeEach(() => {
  vi.clearAllMocks()
  player.state = { status: 'idle', musicianId: null, clipId: null }
  player.previewId = null
})

describe('useMapController', () => {
  it('a mouse click opens the preview without the audio gesture', async () => {
    const { result } = setup()
    act(() => result.current.handlers.onPin(okeanElzy, 'mouse'))
    await waitFor(() => expect(result.current.previewPin?.slug).toBe('okean-elzy'))
    expect(player.gesture).not.toHaveBeenCalled()
  })

  it('a tap (touch, pen, keyboard) goes to the audio core', () => {
    const { result } = setup()
    act(() => result.current.handlers.onPin(okeanElzy, 'tap'))
    expect(player.gesture).toHaveBeenCalledWith({ type: 'tap', musicianId: okeanElzy.id })
  })

  it('mouse hover is forwarded to the audio core', () => {
    const { result } = setup()
    result.current.handlers.onHoverStart('x')
    result.current.handlers.onHoverEnd('x')
    expect(player.pointerEnter).toHaveBeenCalledWith('x')
    expect(player.pointerLeave).toHaveBeenCalledWith('x')
  })

  it('handlers stay the same object when audio or preview state changes, and use the latest state', async () => {
    const { result, rerender } = setup()
    const before = result.current.handlers
    player.state = { status: 'playing', musicianId: okeanElzy.id, clipId: 'c1' }
    player.previewId = okeanElzy.id
    rerender()
    expect(result.current.handlers).toBe(before)
    expect(result.current.playingId).toBe(okeanElzy.id)
    act(() => result.current.handlers.closeSheets())
    expect(player.gesture).toHaveBeenCalledWith({ type: 'closePreview', musicianId: okeanElzy.id })
  })
})
