import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation, useSearchParams } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { useMapModel } from './useMapModel'

function setup(url: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[url]}>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return renderHook(
    () => ({ model: useMapModel(), search: useLocation().search, setParams: useSearchParams()[1] }),
    { wrapper },
  )
}

describe('useMapModel', () => {
  it('builds indexes from fixtures and reads filters from the URL', async () => {
    const { result } = setup('/?region=LVIV')
    await waitFor(() => expect(result.current.model.loading).toBe(false))
    expect(result.current.model.filters).toEqual({ region: ['LVIV'] })
    const idx = result.current.model.indexes!
    expect(idx.level(5)).toBe('REGION') // default design variant is REGION_PLACE_PIN
    expect(idx.level(12)).toBe('PIN')
    expect(idx.total).toBeGreaterThan(0)
    expect(result.current.model.error).toBeNull()
  })

  it('writes filters to the URL with replace and clears deselected keys', async () => {
    const { result } = setup('/?region=LVIV')
    await waitFor(() => expect(result.current.model.loading).toBe(false))
    act(() => result.current.model.setFilters({ region: ['ODESA'] }))
    expect(result.current.search).toBe('?region=ODESA')
    act(() => result.current.model.setFilters({}))
    expect(result.current.search).toBe('')
  })

  it('keeps the same indexes when an unrelated search param changes', async () => {
    const { result } = setup('/?region=LVIV')
    await waitFor(() => expect(result.current.model.loading).toBe(false))
    const before = result.current.model.indexes
    act(() => result.current.setParams((sp) => new URLSearchParams([...sp, ['pin', 'x']])))
    expect(result.current.search).toContain('pin=x')
    expect(result.current.model.indexes).toBe(before)
    act(() => result.current.model.setFilters({ region: ['ODESA'] }))
    expect(result.current.model.indexes).not.toBe(before)
  })
})
