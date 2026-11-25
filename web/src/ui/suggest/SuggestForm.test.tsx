import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { createCopy } from '../../core/i18n/copy'
import { SuggestForm } from './SuggestForm'

const t = createCopy({})

function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SuggestForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  sessionStorage.clear()
})

const next = () => fireEvent.click(screen.getByRole('button', { name: t('submit.next') }))
const title = () => screen.getByTestId('step-title').textContent

describe('SuggestForm wizard', () => {
  it('validates the step before moving on, then keeps values when going back', async () => {
    mount()
    expect(title()).toBe(t('submit.step.who'))
    next()
    expect(await screen.findByText(t('submit.error.required'))).toBeTruthy()
    expect(title()).toBe(t('submit.step.who'))
    expect(document.activeElement).toBe(screen.getByLabelText(t('submit.field.name.label')))

    fireEvent.change(screen.getByLabelText(t('submit.field.name.label')), { target: { value: 'Тарас' } })
    next()
    await waitFor(() => expect(title()).toBe(t('submit.step.where')))
    expect(screen.getByText(t('submit.step.progress', { n: 2, total: 6 }))).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: t('submit.back') }))
    await waitFor(() => expect(title()).toBe(t('submit.step.who')))
    expect((screen.getByLabelText(t('submit.field.name.label')) as HTMLInputElement).value).toBe('Тарас')
  })

  it('asks for the origin to be picked from the list and shows the birth place only for a person', async () => {
    mount()
    fireEvent.change(screen.getByLabelText(t('submit.field.name.label')), { target: { value: 'Тарас' } })
    next()
    await waitFor(() => expect(title()).toBe(t('submit.step.where')))
    expect(screen.getByLabelText(t('submit.field.birthPlaceId.label'))).toBeTruthy()
    fireEvent.change(screen.getByLabelText(t('submit.field.originPlaceId.label')), { target: { value: 'Бро' } })
    next()
    expect(await screen.findByText(t('submit.error.placePick'))).toBeTruthy()
    expect(title()).toBe(t('submit.step.where'))

    fireEvent.click(screen.getByRole('button', { name: t('submit.back') }))
    await waitFor(() => expect(title()).toBe(t('submit.step.who')))
    fireEvent.click(screen.getByLabelText(t('submit.kind.BAND')))
    next()
    await waitFor(() => expect(title()).toBe(t('submit.step.where')))
    expect(screen.queryByLabelText(t('submit.field.birthPlaceId.label'))).toBeNull()
  })

  it('keeps the bot-trap field hidden from assistive tech and the tab order', () => {
    mount()
    const trap = document.querySelector<HTMLInputElement>('input[name="faxNumber"]')!
    expect(trap.getAttribute('aria-hidden')).toBe('true')
    expect(trap.tabIndex).toBe(-1)
    expect(trap.value).toBe('')
  })
})
