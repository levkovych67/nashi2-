import { cleanup, render } from '@testing-library/react'
import type { ComponentType, ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/types.gen'
import type { MapItem } from '../../core/map/aggregation'
import { VARIANTS } from '../variants'
import { MapMarkers, type MarkerHandlers } from './MapMarkers'
import type { PinSkinProps } from './variants/AvatarRing'

// MapLibre needs WebGL; the markers' content is what matters here.
vi.mock('./MapMarker', () => ({ MapMarker: ({ children }: { children: ReactNode }) => <>{children}</> }))

type MapPin = components['schemas']['MapPin']

const pin = (id: string): MapPin => ({
  id, slug: id, name: id, kind: 'PERSON', lon: 24, lat: 49.8, region: 'LVIV', placeId: 'Q36036', hasAudio: true,
})
const items: MapItem[] = [
  { kind: 'pin', pin: pin('a') },
  { kind: 'pin', pin: pin('b') },
  { kind: 'region', code: 'LVIV', name: 'Львівська область', lon: 24, lat: 49.7, count: 484 },
  { kind: 'pinCluster', id: 7, lon: 24.1, lat: 49.8, count: 3 },
]
const handlers: MarkerHandlers = {
  onRegion: vi.fn(), onPlace: vi.fn(), onCluster: vi.fn(), onPin: vi.fn(), onHoverStart: vi.fn(), onHoverEnd: vi.fn(),
}
const names = new Map([['c:7', 'Львів']])
const placeName = new Map([['Q36036', 'Львів']])

const pinRenders = new Map<string, number>()
let bubbleRenders = 0
function CountingPin({ name }: PinSkinProps) {
  pinRenders.set(name, (pinRenders.get(name) ?? 0) + 1)
  return null
}
function CountingBubble() {
  bubbleRenders++
  return null
}

const registry = VARIANTS as Record<string, Record<string, ComponentType<never>>>
const saved = { pin: registry['map.pin'], cluster: registry['map.cluster'], badge: registry['audio.desktop-hover'] }
beforeEach(() => {
  registry['map.pin'] = { AVATAR_RING: CountingPin }
  registry['map.cluster'] = { BUBBLE_COUNT: CountingBubble }
  registry['audio.desktop-hover'] = { RING_PROGRESS: () => null } // the default variant, so no fallback warning
  pinRenders.clear()
  bubbleRenders = 0
})
afterEach(() => {
  cleanup()
  registry['map.pin'] = saved.pin
  registry['map.cluster'] = saved.cluster
  registry['audio.desktop-hover'] = saved.badge
})

const markers = (playingId: string | null, selectedId: string | null) => (
  <MapMarkers
    items={items}
    names={names}
    placeName={placeName}
    numeralRegions={false}
    playingId={playingId}
    selectedId={selectedId}
    handlers={handlers}
  />
)

describe('MapMarkers', () => {
  it('re-renders only the pin whose playing or selected state changed', () => {
    const { rerender } = render(markers(null, null))
    expect(Object.fromEntries(pinRenders)).toEqual({ a: 1, b: 1 })
    expect(bubbleRenders).toBe(2)

    rerender(markers('b', null)) // audio starts on b
    expect(Object.fromEntries(pinRenders)).toEqual({ a: 1, b: 2 })

    rerender(markers('b', 'a')) // preview opens for a
    expect(Object.fromEntries(pinRenders)).toEqual({ a: 2, b: 2 })

    rerender(markers('b', 'a')) // an unrelated page update
    expect(Object.fromEntries(pinRenders)).toEqual({ a: 2, b: 2 })
    expect(bubbleRenders).toBe(2)
  })
})
