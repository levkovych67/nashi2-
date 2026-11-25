import { useCopy } from '../../core/i18n/copy'

/**
 * Placeholder content of the preview slot until the musician preview card (decision musician.preview) is built:
 * the name and the origin explanation (D-14).
 */
export function MusicianPreviewPlaceholder({ name }: { name: string }) {
  const t = useCopy()
  return (
    <div className="n-preview-placeholder">
      <h2 className="n-sheet__title">{name}</h2>
      <p>{t('map.originNotice')}</p>
    </div>
  )
}
