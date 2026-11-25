import { Link } from 'react-router-dom'
import type { components } from '../../api/types.gen'
import { useCopy } from '../../core/i18n/copy'
import { lifeDatesLabel } from '../../core/musician/view'
import './musician.css'

type Musician = components['schemas']['MusicianDetails']

/** Kind and years, the name, genres and the short tagline; links the page back to the musician's pin on the map. */
export function MusicianHeader({ musician }: { musician: Musician }) {
  const t = useCopy()
  const dates = lifeDatesLabel(musician.dates, t)
  const summary = musician.description ?? musician.summary ?? ''
  const tagline = musician.tagline && !summary.includes(musician.tagline) ? musician.tagline : null
  return (
    <header className="n-musician__header">
      <p className="n-musician__eyebrow">{[t(`musician.kind.${musician.kind}`), dates].filter(Boolean).join(' · ')}</p>
      <h1 className="n-musician__name" data-testid="musician-name">
        {musician.name}
      </h1>
      {musician.genres.length > 0 && <p className="n-musician__genres">{musician.genres.join(' · ')}</p>}
      {tagline && (
        <p className="n-musician__tagline" data-testid="musician-tagline">
          {tagline}
        </p>
      )}
      {musician.origin && (
        <Link className="n-musician__button" to={{ pathname: '/', search: `?musician=${encodeURIComponent(musician.slug)}` }}>
          {t('musician.showOnMap')}
        </Link>
      )}
    </header>
  )
}
