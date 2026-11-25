import { useCopy } from '../../core/i18n/copy'

/** Mutes or unmutes the fragments (hover and tap). While the browser blocks sound, a hint says what to do. */
export function SoundToggle({ muted, blocked, onToggle }: { muted: boolean; blocked: boolean; onToggle(): void }) {
  const t = useCopy()
  return (
    <div className="n-sound">
      <button type="button" className="n-map-button" data-testid="sound-toggle" data-muted={muted} onClick={onToggle}>
        {muted ? t('map.soundOff') : t('map.soundOn')}
      </button>
      {blocked && !muted && (
        <p className="n-sound__hint" role="status" data-testid="sound-hint">
          {t('map.soundBlockedHint')}
        </p>
      )}
    </div>
  )
}
