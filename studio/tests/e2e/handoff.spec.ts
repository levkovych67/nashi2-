import { expect, test } from '@playwright/test'

test('експортує статуси та лише явно погоджений варіант з його реквізитами', async ({
  page,
}) => {
  await page.goto('/handoff')
  const sign = page.getByRole('button', {
    name: 'Затвердити позначені рішення',
  })
  await expect(sign).toBeDisabled()
  await expect(page.getByRole('row')).toHaveCount(18)
  await page
    .getByRole('checkbox', { name: 'До погодження · Позначка музиканта' })
    .check()
  await page.getByLabel('Хто погоджує').fill('Тест браузера')
  await expect(sign).toBeDisabled()
  await page.getByLabel(/Підтверджую свій вибір/).check()
  await sign.click()
  await page.reload()
  await expect(page.getByText(/Затверджено 1 із 17 рішень/)).toBeVisible()
  const waiting = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Завантажити decisions.json' }).click()
  const download = await waiting
  expect(download.suggestedFilename()).toBe('decisions.json')
  const stream = await download.createReadStream()
  const chunks: Buffer[] = []
  for await (const chunk of stream!) chunks.push(Buffer.from(chunk))
  const file = JSON.parse(Buffer.concat(chunks).toString('utf8'))
  expect(Object.keys(file.decisions)).toHaveLength(17)
  expect(file.decisions['map.pin']).toMatchObject({
    status: 'APPROVED',
    approvedBy: 'Тест браузера',
  })
  expect(file.decisions['submission.form'].status).toBe('PROPOSED')
})
