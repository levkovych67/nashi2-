import type { Page } from '@playwright/test'
type Save = (relative: string) => Promise<void>
export async function submissionScreens(
  page: Page,
  save: Save,
  wizard: boolean,
) {
  await page.getByTestId('flow-next').waitFor()
  await save('01-navigation')
  await page.getByTestId('flow-next').click()
  const form = page.locator('.submission-study')
  await form.getByLabel('Ім’я або назва гурту').waitFor()
  await save('02-form')
  const next = form.getByRole('button', { name: 'Далі', exact: true })
  const send = form.getByRole('button', { name: 'Надіслати пропозицію' })
  await (wizard ? next : send).click()
  await save('03-validation')
  await form.getByLabel('Ім’я або назва гурту').fill('Польові записи')
  await form.getByText('Сценарій надсилання', { exact: true }).click()
  await form
    .getByLabel('Наступна відповідь демо')
    .selectOption('PAYLOAD_TOO_LARGE')
  await form.getByText('Сценарій надсилання', { exact: true }).click()
  if (wizard) await next.click()
  await form.getByRole('combobox', { name: 'Місто походження' }).fill('Льв')
  await form
    .getByRole('option', { name: /Львів.*Львівська область/ })
    .first()
    .click()
  await save('04-origin')
  if (wizard) await next.click()
  await form
    .getByLabel('Короткий опис', { exact: true })
    .fill(
      'Львівський гурт записує авторські композиції та польові звуки міста.',
    )
  await save('05-music')
  if (wizard) await next.click()
  await form
    .getByLabel('Посилання на виконавця')
    .fill('https://soundcloud.com/polovi-zapysy')
  await save('06-links')
  if (wizard) await next.click()
  const picture = await page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 960
    canvas.height = 720
    const ctx = canvas.getContext('2d')!
    ctx.fillStyle = '#234835'
    ctx.fillRect(0, 0, 960, 720)
    ctx.fillStyle = '#efebd8'
    ctx.font = '56px Georgia'
    ctx.fillText('Польові записи', 68, 410)
    return canvas.toDataURL('image/jpeg').split(',')[1]
  })
  const file = form.getByLabel('Додати фото')
  await file.setInputFiles({
    name: 'обкладинка.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from(picture, 'base64'),
  })
  await form.getByText('1. обкладинка.jpg').waitFor()
  await save('07-photo')
  if (wizard) await next.click()
  await form.getByRole('checkbox').check()
  await save('08-review')
  await send.click()
  await form.getByRole('progressbar').waitFor()
  await save('09-sending')
  await form.getByRole('alert').waitFor()
  await save('10-error-413')
  if (wizard) await form.getByRole('button', { name: 'Змінити фото' }).click()
  await form
    .getByRole('button', { name: 'Видалити фото обкладинка.jpg' })
    .click()
  await file.setInputFiles({
    name: 'обрізана-обкладинка.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from(picture, 'base64'),
  })
  await form.getByText('1. обрізана-обкладинка.jpg').waitFor()
  await save('11-replace-photo')
  if (wizard) await next.click()
  await form
    .getByRole('button', { name: /Повторити надсилання|Надіслати пропозицію/ })
    .click()
  await page.getByRole('heading', { name: 'Пропозицію прийнято' }).waitFor()
  await save('12-success')
}
