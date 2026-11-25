import { useCopy } from '../../core/i18n/copy'

// Below this distance from a limit the zoom counts as at the limit (fit and easing leave rounding noise).
const EPSILON = 0.01

/**
 * + / − over the map (FR-01: zoom by gestures, wheel and buttons; WCAG 2.5.1 single-pointer alternative to pinch).
 * The symbols are drawn in CSS; the buttons are named by copy keys.
 */
export function ZoomButtons({ zoom, minZoom, maxZoom, onZoomIn, onZoomOut }: {
  zoom: number
  minZoom: number
  maxZoom: number
  onZoomIn(): void
  onZoomOut(): void
}) {
  const t = useCopy()
  return (
    <div className="n-zoom" role="group" aria-label={t('map.zoomGroup')} data-testid="zoom">
      <button
        type="button"
        className="n-zoom__button n-zoom__button--in"
        aria-label={t('map.zoomIn')}
        disabled={zoom >= maxZoom - EPSILON}
        onClick={onZoomIn}
      />
      <button
        type="button"
        className="n-zoom__button n-zoom__button--out"
        aria-label={t('map.zoomOut')}
        disabled={zoom <= minZoom + EPSILON}
        onClick={onZoomOut}
      />
    </div>
  )
}
