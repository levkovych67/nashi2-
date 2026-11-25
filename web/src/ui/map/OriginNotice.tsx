import { useDesign } from '../../core/design/config'
import { useCopy } from '../../core/i18n/copy'
import { resolveVariant } from '../variants'

/** The always-visible explanation of what a pin marks (D-14); the variant decides how it looks. */
export function OriginNotice() {
  const t = useCopy()
  const Skin = resolveVariant<{ text: string }>('map.origin-notice', useDesign())
  return (
    <div className="n-origin-notice" data-testid="origin-notice">
      {Skin && <Skin text={t('map.originNotice')} />}
    </div>
  )
}
