import { useEffect, useState } from 'react'
import { useRouteError } from 'react-router-dom'
import { canReloadForChunk, isChunkLoadError, markChunkReload } from '../../core/routing/chunkReload'
import { useCopy } from '../../core/i18n/copy'
import './layout.css'

/**
 * Error element of every route: a page that throws while rendering, or a lazy route chunk that is gone after a
 * deploy. The latter reloads the page once; otherwise a Ukrainian message offers a reload and the way to the map
 * (a full page load, so a new deploy is picked up).
 */
export function RouteError({ reload = () => window.location.reload() }: { reload?(): void }) {
  const t = useCopy()
  const error = useRouteError()
  const [reloading] = useState(() => isChunkLoadError(error) && canReloadForChunk())
  useEffect(() => {
    if (!reloading) return
    markChunkReload()
    reload()
  }, [reloading, reload])
  if (reloading) return null
  return (
    <div className="n-app">
      <main className="n-main">
        <div className="n-route-error" role="alert">
          <h1 className="n-route-error__title">{t('errors.page')}</h1>
          <p>{t('errors.generic')}</p>
          <div className="n-route-error__actions">
            <button type="button" className="n-route-error__button n-route-error__button--primary" onClick={() => reload()}>
              {t('errors.reload')}
            </button>
            <a className="n-route-error__button" href="/">
              {t('errors.backToMap')}
            </a>
          </div>
        </div>
      </main>
    </div>
  )
}
