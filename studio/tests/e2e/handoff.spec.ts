import { expect, test } from '@playwright/test'
import { DECISIONS } from '../../src/decisions/catalog'
import { variants } from '../../src/decisions/variants'
import { recordedDecisions } from './recordedDecisions'

// decisions/decisions.json may already approve every decision, so the draft proposes
// different variants for two of them: both are pending again, and only one is signed.
const other = (id: string) =>
  variants[id].find((v) => v.id !== recordedDecisions[id]?.variant)!.id
const draft = {
  'map.pin': { variant: other('map.pin') },
  'submission.form': { variant: other('submission.form') },
}
const approvedBefore = DECISIONS.filter(
  (d) => !(d.id in draft) && recordedDecisions[d.id]?.status === 'APPROVED',
).length

test('експортує статуси та лише явно погоджений варіант з його реквізитами', async ({
  page,
}) => {
  await page.addInitScript(
    ({ key, value }) => localStorage.setItem(key, value),
    {
      key: 'nashi-studio:draft:v1',
      value: Buffer.from(JSON.stringify({ v: 1, s: draft })).toString(
        'base64url',
      ),
    },
  )
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
  await expect(
    page.getByText(
      new RegExp('Затверджено ' + (approvedBefore + 1) + ' із 17 рішень'),
    ),
  ).toBeVisible()
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
    variant: draft['map.pin'].variant,
    status: 'APPROVED',
    approvedBy: 'Тест браузера',
  })
  expect(file.decisions['submission.form']).toMatchObject({
    variant: draft['submission.form'].variant,
    status: 'PROPOSED',
  })
})
