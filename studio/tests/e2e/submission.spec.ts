import { expect, test } from '@playwright/test'
import axe from 'axe-core'

for (const variant of ['SINGLE_PAGE', 'WIZARD']) {
  test(
    variant +
      ' перевіряє анкету, готує локальне фото та відновлюється після 413',
    async ({ page }) => {
      const outgoing: string[] = []
      page.on('request', (request) => {
        if (request.method() === 'POST') outgoing.push(request.url())
      })
      await page.goto('/v/submission.form/' + variant)
      const form = page.locator('.submission-study')
      const next = form.getByRole('button', { name: 'Далі', exact: true })
      const wizard = variant === 'WIZARD'
      await (
        wizard
          ? next
          : form.getByRole('button', { name: 'Надіслати пропозицію' })
      ).click()
      const name = form.getByLabel('Ім’я або назва гурту')
      await expect(name).toBeFocused()
      await name.fill('Польові записи')
      await form.getByText('Сценарій надсилання', { exact: true }).click()
      await form
        .getByLabel('Наступна відповідь демо')
        .selectOption('PAYLOAD_TOO_LARGE')
      if (wizard) await next.click()
      await form.getByRole('combobox', { name: 'Місто походження' }).fill('льв')
      await form
        .getByRole('option', { name: /Львів.*Львівська область/ })
        .first()
        .click()
      if (wizard) await next.click()
      await form
        .getByLabel('Короткий опис', { exact: true })
        .fill(
          'Український гурт створює авторську музику з польових записів і голосів.',
        )
      if (wizard) await next.click()
      await form
        .getByLabel('Посилання на виконавця')
        .fill('https://open.spotify.com/artist/demo')
      if (wizard) await next.click()
      const image = await page.evaluate(() => {
        const canvas = document.createElement('canvas')
        canvas.width = 2560
        canvas.height = 750
        const ctx = canvas.getContext('2d')!
        ctx.fillStyle = '#364f38'
        ctx.fillRect(0, 0, canvas.width, canvas.height)
        return canvas.toDataURL('image/png').split(',')[1]
      })
      await form.getByLabel('Додати фото').setInputFiles({
        name: 'гурт.png',
        mimeType: 'image/png',
        buffer: Buffer.from(image, 'base64'),
      })
      await expect(form.getByText('1. гурт.png')).toBeVisible()
      const thumb = form.locator('.sub-photos img')
      await expect
        .poll(() => thumb.evaluate((img: HTMLImageElement) => img.naturalWidth))
        .toBe(2048)
      if (wizard) await next.click()
      await form.getByRole('checkbox').check()
      expect(
        await form.locator('input[name="faxNumber"]').getAttribute('tabindex'),
      ).toBe('-1')
      await page.addScriptTag({ content: axe.source })
      const violations = await page.evaluate(async () => {
        const api = (window as unknown as { axe: typeof axe }).axe
        return (
          await api.run('.submission-study', {
            runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa'] },
          })
        ).violations
      })
      expect(
        violations.filter(
          (item) => item.impact === 'critical' || item.impact === 'serious',
        ),
      ).toEqual([])
      await form.getByRole('button', { name: 'Надіслати пропозицію' }).click()
      await expect(form.getByRole('alert')).toContainText('413')
      await expect(form.getByRole('checkbox')).toBeChecked()
      await form.getByRole('button', { name: 'Повторити надсилання' }).click()
      await expect(
        page.getByRole('heading', { name: 'Пропозицію прийнято' }),
      ).toBeVisible()
      expect(outgoing).toEqual([])
    },
  )
}
