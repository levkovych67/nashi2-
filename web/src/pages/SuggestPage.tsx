import { useCopy } from '../core/i18n/copy'

export default function SuggestPage() {
  const t = useCopy()
  return (
    <main>
      <h1>{t('submit.title')}</h1>
    </main>
  )
}
