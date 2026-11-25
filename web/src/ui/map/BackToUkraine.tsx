import { useCopy } from '../../core/i18n/copy'

export function BackToUkraine({ onClick }: { onClick(): void }) {
  const t = useCopy()
  return (
    <button type="button" className="n-map-button" data-testid="back-to-ukraine" onClick={onClick}>
      {t('map.backToUkraine')}
    </button>
  )
}
