import { Link, NavLink } from 'react-router-dom'
import { useCopy } from '../../../core/i18n/copy'
import '../layout.css'

/**
 * nav.structure = TOP_BAR_MENU: a top bar with the wordmark and the app sections. Only sections that have UI copy
 * are listed (copy.uk.json has no keys for a menu toggle or the about page yet).
 */
export function TopBarMenu() {
  const t = useCopy()
  return (
    <header className="n-topbar">
      <Link to="/" className="n-topbar__brand">
        {t('app.name')}
      </Link>
      <p className="n-topbar__tagline">{t('app.tagline')}</p>
      <nav className="n-topbar__nav">
        <NavLink to="/suggest" className="n-topbar__link">
          {t('submit.title')}
        </NavLink>
      </nav>
    </header>
  )
}
