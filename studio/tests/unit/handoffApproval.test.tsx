import { afterEach, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { HandoffPage } from '../../src/pages/HandoffPage'
import { effectiveReviewed } from '../../src/handoff/approvals'
import { choose, reset } from '../../src/selection/store'
afterEach(() => {
  cleanup()
  localStorage.clear()
  reset()
})
it('записує лише явно позначене погодження із зазначенням автора; зміна варіанта скасовує його дію', () => {
  choose('map.pin', 'DROP_ICON')
  render(
    <MemoryRouter>
      <HandoffPage />
    </MemoryRouter>,
  )
  const sign = screen.getByRole('button', {
    name: 'Затвердити позначені рішення',
  }) as HTMLButtonElement
  expect(sign.disabled).toBe(true)
  fireEvent.click(
    screen.getByRole('checkbox', {
      name: 'До погодження · Позначка музиканта',
    }),
  )
  fireEvent.change(screen.getByLabelText('Хто погоджує'), {
    target: { value: 'Тестова роль' },
  })
  expect(sign.disabled).toBe(true)
  fireEvent.click(screen.getByLabelText(/Підтверджую свій вибір/))
  fireEvent.click(sign)
  expect(
    effectiveReviewed({ 'map.pin': { variant: 'DROP_ICON' } }).file.decisions[
      'map.pin'
    ],
  ).toMatchObject({ status: 'APPROVED', approvedBy: 'Тестова роль' })
  expect(
    effectiveReviewed({
      'map.pin': { variant: 'DROP_ICON', params: { size: 52 } },
    }).file.decisions['map.pin'].status,
  ).toBe('PROPOSED')
  expect(
    effectiveReviewed({ 'map.pin': { variant: 'DOT_LABEL' } }).file.decisions[
      'map.pin'
    ].status,
  ).toBe('PROPOSED')
  expect(
    screen.getByText(
      'Тестова роль · ' + new Date().toLocaleDateString('en-CA'),
    ),
  ).toBeTruthy()
})
