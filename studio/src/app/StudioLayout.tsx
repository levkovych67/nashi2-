import { useEffect, useRef } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import '../shell/studio.css'

const sections = [
  { path: '/', label: 'Каталог' },
  { path: '/audit', label: 'Аудит V1' },
  { path: '/flow/map-to-musician', label: 'Сценарії' },
  { path: '/states', label: 'Стани' },
  { path: '/handoff', label: 'Передача' },
]

export function StudioLayout() {
  const { pathname, hash } = useLocation()
  const content = useRef<HTMLDivElement>(null)
  const previousPath = useRef(pathname)
  useEffect(() => {
    if (pathname !== previousPath.current) {
      previousPath.current = pathname
      // A link to an anchor (/audit#A-10) is scrolled by the page itself; this
      // effect runs after the page's, so resetting here would undo that scroll.
      if (!hash) window.scrollTo(0, 0)
      content.current?.focus({ preventScroll: true })
    }
  }, [pathname, hash])

  return (
    <>
      <a className="studio-skip" href="#studio-content">До вмісту</a>
      <header className="studio-chrome" data-testid="studio-chrome">
        <div className="studio-bar">
          <Link className="studio-brand" to="/" aria-label="Наші. На головну студії">
            <img src="/studio-mark.svg" width="34" height="34" alt="" />
            <strong>Наші</strong><span>Студія</span>
          </Link>
          <nav aria-label="Розділи студії" className="studio-nav">
            {sections.map(({path, label}) => {
              const isCurrent = path === '/' ? pathname === '/' || pathname.startsWith('/d/')
                : path.startsWith('/flow/') ? pathname.startsWith('/flow/') : pathname === path
              return <Link key={path} to={path} className={isCurrent ? 'studio-current' : ''} aria-current={isCurrent ? 'page' : undefined}>{label}</Link>
            })}
          </nav>
        </div>
      </header>
      <div className="studio-content" id="studio-content" tabIndex={-1} ref={content}><Outlet /></div>
    </>
  )
}
