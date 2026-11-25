import { Link } from 'react-router-dom'
import { useCopy } from '../core/i18n/copy'
import { AppShell } from '../ui/layout/AppShell'
import '../ui/musician/musician.css'

export default function NotFoundPage() {
  const t = useCopy()
  return (
    <AppShell>
      <div className="n-musician">
        <div className="n-musician__page">
          <h1 className="n-musician__name">{t('errors.notFound')}</h1>
          <Link className="n-musician__button" to="/">
            {t('errors.backToMap')}
          </Link>
        </div>
      </div>
    </AppShell>
  )
}
