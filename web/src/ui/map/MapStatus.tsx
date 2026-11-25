import { useCopy } from '../../core/i18n/copy'
import type { LoadErrorKey } from '../../core/i18n/loadError'

/**
 * State of the map data under the controls: a quiet note while it loads, an alert with a retry when it failed.
 * Nothing once the map is ready.
 */
export function MapStatus({ loading, error, onRetry }: { loading: boolean; error: LoadErrorKey | null; onRetry(): void }) {
  const t = useCopy()
  if (error) {
    return (
      <div className="n-map-status n-map-status--error" role="alert" data-testid="map-error">
        <p>{t(error)}</p>
        <button type="button" className="n-map-button n-map-button--primary" data-testid="map-retry" onClick={onRetry}>
          {t('errors.retry')}
        </button>
      </div>
    )
  }
  if (!loading) return null
  return (
    <p className="n-map-status n-map-status--loading" role="status" data-testid="map-loading">
      {t('map.loading')}
    </p>
  )
}
