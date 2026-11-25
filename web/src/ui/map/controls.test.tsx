import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DesignContext, loadDesignConfig } from '../../core/design/config'
import { BottomSheet } from '../layout/BottomSheet'
import { Toast, useToast } from '../layout/Toast'
import { TopBarMenu } from '../layout/variants/TopBarMenu'
import { FilterSheet } from './FilterSheet'
import { NearMeButton } from './NearMeButton'
import { RegionFilter } from './RegionFilter'
import { SoundToggle } from './SoundToggle'
import { EqualizerBadge } from './variants/EqualizerBadge'

const options = [
  { value: 'LVIV', label: 'Львівська область', count: 484 },
  { value: 'KYIV', label: 'Київська область', count: 100 },
  { value: 'VOLYN', label: 'Волинська область', count: 69 },
]

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  window.localStorage.clear()
})

describe('Toast', () => {
  function Demo() {
    const { message, show } = useToast()
    return (
      <>
        <button onClick={() => show('Повідомлення')}>go</button>
        <Toast message={message} />
      </>
    )
  }

  it('is a polite live region that is there before the message and clears itself', () => {
    vi.useFakeTimers()
    render(<Demo />)
    const region = screen.getByRole('status')
    expect(region.getAttribute('aria-live')).toBe('polite')
    expect(screen.queryByTestId('toast')).toBeNull()
    fireEvent.click(screen.getByText('go'))
    expect(region.textContent).toBe('Повідомлення')
    act(() => void vi.advanceTimersByTime(7000))
    expect(screen.queryByTestId('toast')).toBeNull()
  })
})

describe('BottomSheet', () => {
  it('has a visible close button, takes focus on open and returns it to the trigger on close', () => {
    render(<button data-testid="trigger">open</button>)
    const trigger = screen.getByTestId('trigger')
    trigger.focus()
    const onClose = vi.fn()
    const { unmount } = render(
      <BottomSheet label="Фільтри" onClose={onClose}>
        <p>x</p>
      </BottomSheet>,
    )
    expect(document.activeElement).toBe(screen.getByRole('dialog'))
    fireEvent.click(screen.getByRole('button', { name: 'Закрити' }))
    expect(onClose).toHaveBeenCalledTimes(1)
    unmount()
    expect(document.activeElement).toBe(trigger)
  })

  it('closes on Escape', () => {
    const onClose = vi.fn()
    render(
      <BottomSheet label="Фільтри" onClose={onClose}>
        <p>x</p>
      </BottomSheet>,
    )
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})

describe('TopBarMenu', () => {
  const renderMenu = () =>
    render(
      <MemoryRouter initialEntries={['/']}>
        <TopBarMenu />
      </MemoryRouter>,
    )

  it('opens a menu with the map, the suggestion form and the about page', () => {
    renderMenu()
    const button = screen.getByRole('button', { name: 'Меню' })
    expect(screen.queryByRole('link', { name: 'Карта' })).toBeNull()
    fireEvent.click(button)
    expect(button.getAttribute('aria-expanded')).toBe('true')
    const hrefs = Object.fromEntries(screen.getAllByRole('link').filter((a) => a.closest('ul')).map((a) => [a.textContent, a.getAttribute('href')]))
    expect(hrefs).toEqual({ Карта: '/', 'Запропонувати музиканта': '/suggest', 'Про проєкт': '/about' })
  })

  it('closes on Escape and gives the focus back to the button', () => {
    renderMenu()
    const button = screen.getByRole('button', { name: 'Меню' })
    fireEvent.click(button)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('link', { name: 'Карта' })).toBeNull()
    expect(document.activeElement).toBe(button)
  })
})

describe('NearMeButton', () => {
  const withView = (variant: string, children: ReactNode) => (
    <DesignContext.Provider
      value={loadDesignConfig({ schemaVersion: 1, decisions: { 'map.initial-view': { variant, status: 'APPROVED' } } })}
    >
      {children}
    </DesignContext.Provider>
  )

  it('is a plain button while map.initial-view is UKRAINE (the PROPOSED default)', () => {
    render(<NearMeButton onClick={() => {}} />)
    expect(screen.getByTestId('near-me').dataset.prompt).toBe('false')
  })

  it('is a prompt on the first open with UKRAINE_WITH_NEAR_ME_PROMPT, and only asks when pressed', () => {
    const onClick = vi.fn()
    render(withView('UKRAINE_WITH_NEAR_ME_PROMPT', <NearMeButton onClick={onClick} />))
    const button = screen.getByTestId('near-me')
    expect(button.dataset.prompt).toBe('true')
    expect(onClick).not.toHaveBeenCalled()
    fireEvent.click(button)
    expect(onClick).toHaveBeenCalledTimes(1)
    expect(button.dataset.prompt).toBe('false')
  })

  it('does not prompt again after it has been seen', () => {
    window.localStorage.setItem('nashi.nearMeSeen', '1')
    render(withView('UKRAINE_WITH_NEAR_ME_PROMPT', <NearMeButton onClick={() => {}} />))
    expect(screen.getByTestId('near-me').dataset.prompt).toBe('false')
  })
})

describe('SoundToggle', () => {
  it('names the current state and toggles', () => {
    const onToggle = vi.fn()
    const { rerender } = render(<SoundToggle muted={false} blocked={false} onToggle={onToggle} />)
    expect(screen.getByTestId('sound-toggle').textContent).toBe('Звук увімкнено')
    fireEvent.click(screen.getByTestId('sound-toggle'))
    expect(onToggle).toHaveBeenCalledTimes(1)
    rerender(<SoundToggle muted blocked={false} onToggle={onToggle} />)
    expect(screen.getByTestId('sound-toggle').textContent).toBe('Звук вимкнено')
  })

  it('shows the hint only while the browser blocks sound', () => {
    const { rerender } = render(<SoundToggle muted={false} blocked={false} onToggle={() => {}} />)
    expect(screen.queryByText('Натисніть, щоб увімкнути звук')).toBeNull()
    rerender(<SoundToggle muted={false} blocked onToggle={() => {}} />)
    expect(screen.getByText('Натисніть, щоб увімкнути звук')).toBeTruthy()
  })
})

describe('RegionFilter', () => {
  it('searches the list, applies the draft and resets', () => {
    const onApply = vi.fn()
    const onReset = vi.fn()
    render(<RegionFilter options={options} selected={['KYIV']} onApply={onApply} onReset={onReset} />)
    expect(screen.getAllByRole('checkbox')).toHaveLength(3)
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'ЛЬВ' } })
    expect(screen.getAllByRole('checkbox')).toHaveLength(1)
    fireEvent.click(screen.getByRole('checkbox', { name: /Львівська область/ }))
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'xyz' } })
    expect(screen.getByText('Музикантів за цим запитом не знайдено.')).toBeTruthy()
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '' } })
    expect(onApply).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Застосувати' }))
    expect(onApply).toHaveBeenCalledWith(['KYIV', 'LVIV'])
    fireEvent.click(screen.getByRole('button', { name: 'Скинути' }))
    expect(onReset).toHaveBeenCalledTimes(1)
  })
})

describe('FilterSheet', () => {
  const props = { options, selected: ['LVIV', 'KYIV'], activeCount: 2, onApply: vi.fn() }

  beforeEach(() => props.onApply.mockClear())

  it('on a phone: a button with the number of active conditions opens the list in a sheet; apply closes it', () => {
    render(<FilterSheet slot="button" {...props} />)
    const open = screen.getByTestId('filters-open')
    expect(open.textContent).toBe('Фільтри (2)')
    expect(screen.queryByRole('dialog')).toBeNull()
    fireEvent.click(open)
    expect(screen.getByRole('dialog', { name: 'Фільтри' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Застосувати' }))
    expect(props.onApply).toHaveBeenCalledWith(['LVIV', 'KYIV'])
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('the panel slot is empty on a phone', () => {
    const { container } = render(<FilterSheet slot="panel" {...props} />)
    expect(container.firstChild).toBeNull()
  })

  it('from 1024 px: a left panel instead of the button', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener() {}, removeEventListener() {} }))
    const panel = render(<FilterSheet slot="panel" {...props} />)
    expect(screen.getByRole('complementary', { name: 'Фільтри' })).toBeTruthy()
    panel.unmount()
    const { container } = render(<FilterSheet slot="button" {...props} />)
    expect(container.firstChild).toBeNull()
  })
})

describe('EqualizerBadge', () => {
  it('is three bars, hidden from assistive tech', () => {
    render(<EqualizerBadge />)
    const badge = screen.getByTestId('playing-badge')
    expect(badge.getAttribute('aria-hidden')).toBe('true')
    expect(badge.querySelectorAll('b')).toHaveLength(3)
  })
})
