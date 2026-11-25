import type { components } from '../../api/types.gen'
import { useCopy } from '../../core/i18n/copy'
import { ExternalLink } from './ExternalLink'
import './musician.css'

/** The source and licence of the text under it (Wikipedia text is CC BY-SA). */
export function TextSourceNote({ source }: { source: components['schemas']['TextSource'] | null }) {
  const t = useCopy()
  if (!source) return null
  return (
    <p className="n-musician__note" data-testid="text-source">
      {source.type === 'WIKIPEDIA' && <>{t('musician.textSource')}: </>}
      <ExternalLink href={source.url}>«{source.title}»</ExternalLink>
      {' · '}
      <ExternalLink href={source.licenseUrl}>{source.license}</ExternalLink>
    </p>
  )
}
