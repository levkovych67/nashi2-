import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, test } from 'vitest'
import { HandoffPage } from '../../src/pages/HandoffPage'
import { QrCode } from '../../src/shell/QrCode'
import { DECISIONS } from '../../src/decisions/catalog'

afterEach(cleanup)

test('renders a local SVG QR code and regenerates it when the target changes', async () => {
  const { rerender } = render(<QrCode value="https://studio.example/v/map.pin/AVATAR_RING?identity=atlas" />)
  const original = (await screen.findByRole('img')).getAttribute('src')
  expect(original).toMatch(/^data:image\/svg\+xml/)
  expect(decodeURIComponent(original!)).toContain('<path')
  rerender(<QrCode value="https://studio.example/v/map.pin/DOT_LABEL?identity=atlas" />)
  await waitFor(() => expect(screen.getByRole('img').getAttribute('src')).not.toBe(original))
})

test('handoff lists each catalog decision and links back to its comparison', () => {
  render(<MemoryRouter><HandoffPage /></MemoryRouter>)
  for (const decision of DECISIONS) {
    expect(screen.getByRole('link', { name: decision.title }).getAttribute('href')).toBe('/d/' + decision.id)
  }
  expect(screen.getAllByRole('row')).toHaveLength(DECISIONS.length + 1)
})
