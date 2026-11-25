import { useCopy } from '../core/i18n/copy'

export default function AboutPage() {
  const t = useCopy()
  return (
    <main>
      <h1>{t('app.name')}</h1>
      <p>{t('app.tagline')}</p>
    </main>
  )
}
