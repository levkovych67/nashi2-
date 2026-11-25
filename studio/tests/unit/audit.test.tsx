import { existsSync } from 'node:fs'
import { fireEvent, render, screen, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it } from 'vitest'
import { FINDINGS, sourceUrl } from '../../src/audit/findings'
import { DECISIONS } from '../../src/decisions/catalog'
import { DecisionEvidence } from '../../src/audit/DecisionEvidence'
import { AuditPage } from '../../src/pages/AuditPage'
afterEach(cleanup)
it('усі рішення прив’язані до спостережень, докази існують', () => {
  expect(FINDINGS.length).toBeGreaterThanOrEqual(15)
  expect(new Set(FINDINGS.map((f) => f.id)).size).toBe(FINDINGS.length)
  expect(new Set(FINDINGS.flatMap((f) => f.affects))).toEqual(
    new Set(DECISIONS.map((d) => d.id)),
  )
  for (const finding of FINDINGS) {
    expect(sourceUrl(finding.v1Ref)).toMatch(
      /github.com\/levkovych67\/nashi-frontend\/blob\/[0-9a-f]{40}\//,
    )
    for (const image of finding.evidence)
      expect(existsSync(new URL('../../public' + image, import.meta.url))).toBe(
        true,
      )
  }
})
it('фільтрує спостереження за розділом та важливістю, скидає обидва значення', () => {
  render(
    <MemoryRouter>
      <AuditPage />
    </MemoryRouter>,
  )
  expect(screen.getAllByRole('article')).toHaveLength(FINDINGS.length)
  fireEvent.change(screen.getByLabelText('Розділ'), {
    target: { value: 'pin' },
  })
  fireEvent.change(screen.getByLabelText('Важливість'), {
    target: { value: 'high' },
  })
  expect(screen.getAllByRole('article')).toHaveLength(
    FINDINGS.filter((f) => f.area === 'pin' && f.severity === 'high').length,
  )
  fireEvent.click(screen.getByRole('button', { name: 'Скинути' }))
  expect(screen.getAllByRole('article')).toHaveLength(FINDINGS.length)
})
it('з рішення можна відкрити конкретне спостереження в аудиті', () => {
  render(
    <MemoryRouter>
      <DecisionEvidence id="map.cluster" />
    </MemoryRouter>,
  )
  fireEvent.click(screen.getByText(/Що ми бачили у V1/))
  expect(screen.getByRole('link', { name: /A-09/ }).getAttribute('href')).toBe(
    '/audit#A-09',
  )
})
