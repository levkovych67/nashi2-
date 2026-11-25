import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/types.gen'
import { DesignContext, loadDesignConfig } from '../../core/design/config'
import { DEFAULT_COPY } from '../../core/i18n/defaults.uk'
import { BottomSheet } from '../layout/BottomSheet'
import { OriginNotice } from './OriginNotice'
import { PinMarker } from './PinMarker'
import { PinStackList } from './PinStackList'
import { RegionBubble } from './RegionBubble'
import { initials } from './variants/AvatarRing'

type MapPin = components['schemas']['MapPin']

const pin = (over: Partial<MapPin> = {}): MapPin => ({
  id: 'id-1', slug: 'okean-elzy', name: 'Океан Ельзи', kind: 'BAND', lon: 24, lat: 49.8, region: 'LVIV',
  placeId: 'Q36036', hasAudio: true, ...over,
})
const noop = () => {}

afterEach(cleanup)

describe('PinMarker', () => {
  it('carries the e2e contract and names the musician and the place', () => {
    const onActivate = vi.fn()
    render(<PinMarker pin={pin()} placeName="Львів" playing={false} selected={false} onActivate={onActivate} onPointerEnter={noop} onPointerLeave={noop} />)
    const button = screen.getByRole('button', { name: 'Океан Ельзи, Львів' })
    expect(button.dataset.testid).toBe('pin')
    expect(button.dataset.slug).toBe('okean-elzy')
    expect(button.dataset.hasAudio).toBe('true')
    fireEvent.click(button)
    expect(onActivate).toHaveBeenCalledWith(pin())
  })

  it('shows initials and drops a portrait that fails to load', () => {
    const { container } = render(
      <PinMarker pin={pin({ thumbUrl: 'https://example.org/a.jpg' })} placeName="Львів" playing={false} selected={false} onActivate={noop} onPointerEnter={noop} onPointerLeave={noop} />,
    )
    expect(container.textContent).toContain('ОЕ')
    const img = container.querySelector('img')!
    fireEvent.error(img)
    expect(container.querySelector('img')).toBeNull()
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
    render(<RegionBubble item={item} choropleth={false} onSelect={onSelect} />)
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
    fireEvent.click(items[1])
    expect(onSelect).toHaveBeenCalledWith(b)
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
