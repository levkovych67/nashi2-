import { useEffect, useId, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { useCopy } from '../../../core/i18n/copy'
import logoUrl from '../../../design/assets/logo.svg'
import logoMarkUrl from '../../../design/assets/logo-mark.svg'
import '../layout.css'

/** nav.structure = TOP_BAR_MENU: a top bar with the identity logo and a «Меню» button that opens the app sections. */
export function TopBarMenu() {
  const t = useCopy()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const listId = useId()
  const { pathname } = useLocation()

  useEffect(() => setOpen(false), [pathname])
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      setOpen(false)
      button.current?.focus()
    }
    const onPointer = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('pointerdown', onPointer)
    }
  }, [open])

  return (
    <header className="n-topbar">
      <Link to="/" className="n-topbar__brand">
        <picture>
          <source media="(max-width: 359px)" srcSet={logoMarkUrl} />
          <img src={logoUrl} alt={t('app.name')} className="n-topbar__logo" />
        </picture>
      </Link>
      <p className="n-topbar__tagline">{t('app.tagline')}</p>
      <nav ref={root} className="n-topbar__nav" aria-label={t('nav.menu')}>
        <button
          ref={button}
          type="button"
          className="n-topbar__link"
          data-testid="menu-open"
          aria-expanded={open}
          aria-controls={listId}
          onClick={() => setOpen((v) => !v)}
        >
          {t('nav.menu')}
        </button>
        {open && (
          <ul id={listId} className="n-topbar__list">
            <li>
              <NavLink to="/" end className="n-topbar__item">
                {t('nav.map')}
              </NavLink>
            </li>
            <li>
              <NavLink to="/suggest" className="n-topbar__item">
                {t('submit.title')}
              </NavLink>
            </li>
            <li>
              <NavLink to="/about" className="n-topbar__item">
                {t('nav.about')}
              </NavLink>
            </li>
          </ul>
        )}
      </nav>
    </header>
  )
}
