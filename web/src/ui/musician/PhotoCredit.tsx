import type { components } from '../../api/types.gen'
import { useCopy } from '../../core/i18n/copy'
import { ExternalLink, hostOf } from './ExternalLink'
import './musician.css'

/** Author and licence of a photo; our own uploads have no credit and show nothing. */
export function PhotoCredit({ credit }: { credit: components['schemas']['ImageCredit'] | null }) {
  const t = useCopy()
  if (!credit) return null
  return (
    <p className="n-musician__note" data-testid="photo-credit">
      {t('musician.photoCredit')}: <ExternalLink href={credit.sourceUrl}>{credit.author ?? hostOf(credit.sourceUrl)}</ExternalLink>
      {credit.license && (
        <>
          {' · '}
          {credit.licenseUrl ? <ExternalLink href={credit.licenseUrl}>{credit.license}</ExternalLink> : credit.license}
        </>
      )}
    </p>
  )
}
