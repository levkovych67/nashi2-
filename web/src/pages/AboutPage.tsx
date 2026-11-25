import { Link } from 'react-router-dom'
import { useCopy } from '../core/i18n/copy'
import { AppShell } from '../ui/layout/AppShell'
import '../ui/musician/musician.css'

export default function AboutPage() {
  const t = useCopy()
  return (
    <AppShell>
      <div className="n-musician" data-testid="about-page">
        <div className="n-musician__page">
          <h1 className="n-musician__name">{t('about.title')}</h1>
          <div className="n-musician__text">
            <p>{t('app.tagline')}</p>
            <p>{t('about.intro')}</p>
          </div>
          <section className="n-musician__section">
            <h2>{t('about.sourcesTitle')}</h2>
            <ul className="n-musician__list">
              <li>{t('about.source.wikipedia')}</li>
              <li>{t('about.source.map')}</li>
              <li>{t('about.source.borders')}</li>
            </ul>
          </section>
          <div className="n-musician__text">
            <p>{t('about.suggest')}</p>
            <Link className="n-musician__button" to="/suggest">
              {t('submit.title')}
            </Link>
          </div>
        </div>
      </div>
    </AppShell>
  )
}
