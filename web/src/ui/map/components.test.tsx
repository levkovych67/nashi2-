import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ComponentProps } from 'react'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/types.gen'
import { DesignContext, loadDesignConfig } from '../../core/design/config'
import { DEFAULT_COPY } from '../../core/i18n/defaults.uk'
import { BottomSheet } from '../layout/BottomSheet'
import { OriginNotice } from './OriginNotice'
import { PinMarker } from './PinMarker'
import { PinStackList } from './PinStackList'
import { RegionBubble } from './RegionBubble'
import { AvatarRing, initials } from './variants/AvatarRing'
import { BubbleCount } from './variants/BubbleCount'

type MapPin = components['schemas']['MapPin']

const pin = (over: Partial<MapPin> = {}): MapPin => ({
  id: 'id-1', slug: 'okean-elzy', name: 'Океан Ельзи', kind: 'BAND', lon: 24, lat: 49.8, region: 'LVIV',
  placeId: 'Q36036', hasAudio: true, ...over,
})
afterEach(cleanup)

// jsdom has no PointerEvent; React reads `pointerType` from the native event.
const realPointerEvent = window.PointerEvent
beforeAll(() => {
  class TestPointerEvent extends MouseEvent {
    pointerType: string
    constructor(type: string, init: PointerEventInit = {}) {
      super(type, init)
      this.pointerType = init.pointerType ?? ''
    }
  }
  window.PointerEvent = TestPointerEvent as unknown as typeof PointerEvent
})
afterAll(() => {
  window.PointerEvent = realPointerEvent
})

function renderPin(over: Partial<ComponentProps<typeof PinMarker>> = {}) {
  const calls = { onActivate: vi.fn(), onHoverStart: vi.fn(), onHoverEnd: vi.fn() }
  render(<PinMarker pin={pin()} placeName="Львів" playing={false} selected={false} skin={AvatarRing} {...calls} {...over} />)
  return { ...calls, button: screen.getByTestId('pin') }
}

describe('PinMarker', () => {
  it('carries the e2e contract and names the musician and the place', () => {
    const { button } = renderPin()
    expect(button).toBe(screen.getByRole('button', { name: 'Океан Ельзи, Львів' }))
    expect(button.dataset.slug).toBe('okean-elzy')
    expect(button.dataset.hasAudio).toBe('true')
  })

  it('hover reaches the audio core only from a mouse: a touch pointerenter does not start playback', () => {
    const { button, ...props } = renderPin()
    fireEvent.pointerEnter(button, { pointerType: 'touch' })
    fireEvent.pointerLeave(button, { pointerType: 'touch' })
    expect(props.onHoverStart).not.toHaveBeenCalled()
    expect(props.onHoverEnd).not.toHaveBeenCalled()
    fireEvent.pointerEnter(button, { pointerType: 'mouse' })
    fireEvent.pointerLeave(button, { pointerType: 'mouse' })
    expect(props.onHoverStart).toHaveBeenCalledWith('id-1')
    expect(props.onHoverEnd).toHaveBeenCalledWith('id-1')
  })

  it('tells a mouse click from a tap by the pointer that pressed it; keyboard counts as a tap', () => {
    const { button, ...props } = renderPin()
    fireEvent.pointerDown(button, { pointerType: 'mouse' })
    fireEvent.click(button)
    fireEvent.pointerDown(button, { pointerType: 'touch' })
    fireEvent.click(button)
    fireEvent.pointerDown(button, { pointerType: 'pen' })
    fireEvent.click(button)
    fireEvent.click(button) // Enter / Space: no pointer
    expect(props.onActivate.mock.calls.map((c) => c[1])).toEqual(['mouse', 'tap', 'tap', 'tap'])
    expect(props.onActivate).toHaveBeenCalledWith(pin(), 'mouse')
  })

  it('shows initials and drops a portrait that fails to load', () => {
    renderPin({ pin: pin({ thumbUrl: 'https://example.org/a.jpg' }) })
    const button = screen.getByTestId('pin')
    expect(button.textContent).toContain('ОЕ')
    fireEvent.error(button.querySelector('img')!)
    expect(button.querySelector('img')).toBeNull()
  })

  it('initials take the first letters of the first two words', () => {
    expect(initials('Один в каное')).toBe('Ов')
    expect(initials('  DOVI ')).toBe('D')
  })
})

describe('RegionBubble', () => {
  it('is named with cluster.ariaLabel and reports the region on tap', () => {
    const onSelect = vi.fn()
    const item = { kind: 'region' as const, code: 'LVIV' as const, name: 'Львівська область', lon: 24, lat: 49.7, count: 484 }
    render(<RegionBubble item={item} choropleth={false} skin={BubbleCount} onSelect={onSelect} />)
    const button = screen.getByRole('button', { name: 'Львівська область, музикантів: 484' })
    expect(button.dataset.testid).toBe('region-bubble')
    fireEvent.click(button)
    expect(onSelect).toHaveBeenCalledWith(item)
  })
})

describe('PinStackList', () => {
  it('lists the stack by name and selects a musician', () => {
    const onSelect = vi.fn()
    const b = pin({ id: 'b', slug: 'b', name: 'Бумбокс' })
    const a = pin({ id: 'a', slug: 'a', name: 'Антитіла' })
    render(<PinStackList title="Київ, музикантів: 2" pins={[b, a]} onSelect={onSelect} />)
    expect(screen.getByRole('heading', { name: 'Київ, музикантів: 2' })).toBeTruthy()
    const items = screen.getAllByRole('button')
    expect(items.map((i) => i.textContent)).toEqual(['ААнтитіла', 'ББумбокс'])
    fireEvent.pointerDown(items[1], { pointerType: 'mouse' })
    fireEvent.click(items[1])
    expect(onSelect).toHaveBeenCalledWith(b, 'mouse')
  })
})

describe('OriginNotice', () => {
  it('renders the copy through the approved variant', () => {
    const design = loadDesignConfig({
      schemaVersion: 1,
      decisions: { 'map.origin-notice': { variant: 'LEGEND_BAR', status: 'APPROVED' } },
    })
    render(
      <DesignContext.Provider value={design}>
        <OriginNotice />
      </DesignContext.Provider>,
    )
    expect(screen.getByTestId('origin-notice').textContent).toBe(DEFAULT_COPY['map.originNotice'])
  })
})

describe('BottomSheet', () => {
  it('closes on Escape', () => {
    const onClose = vi.fn()
    render(<BottomSheet label="Океан Ельзи" onClose={onClose}>x</BottomSheet>)
    expect(screen.getByRole('dialog', { name: 'Океан Ельзи' })).toBeTruthy()
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
