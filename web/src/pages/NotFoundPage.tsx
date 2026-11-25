import { useCopy } from '../core/i18n/copy'

export default function NotFoundPage() {
  const t = useCopy()
  return (
    <main>
      <h1>{t('errors.notFound')}</h1>
    </main>
  )
}
