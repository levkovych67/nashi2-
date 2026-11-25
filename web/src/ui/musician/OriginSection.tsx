import type { components } from '../../api/types.gen'
import { useCopy } from '../../core/i18n/copy'
import { originLabel } from '../../core/musician/view'
import { OriginNotice } from '../map/OriginNotice'
import './musician.css'

/** Where the musician comes from, with the standing explanation of what a pin on the map means (D-14). */
export function OriginSection({ origin }: { origin: components['schemas']['Origin'] }) {
  const t = useCopy()
  return (
    <section className="n-musician__section" data-testid="musician-origin" aria-labelledby="musician-origin-title">
      <h2 id="musician-origin-title">{t('musician.originTitle')}</h2>
      <p className="n-musician__place">{originLabel(origin, t)}</p>
      <p>{origin.regionName}</p>
      <OriginNotice />
    </section>
  )
}
