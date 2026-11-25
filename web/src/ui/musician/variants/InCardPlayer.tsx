import { useFragmentPlayer } from '../../../core/audio/useFragmentPlayer'
import { useCopy } from '../../../core/i18n/copy'
import type { AudioSkinProps } from '../MusicianAudio'
import '../musician.css'

/** audio.player = IN_CARD: play / stop and "another fragment" beside the musician, with the title and the audio source. */
export function InCardPlayer({ musicianId, hasAudio }: AudioSkinProps) {
  const t = useCopy()
  const { state, fragment, noAudio, playMusician, stop } = useFragmentPlayer()
  if (!hasAudio || noAudio.has(musicianId)) return <p className="n-player__none">{t('musician.noAudio')}</p>

  const mine = state.musicianId === musicianId && state.status !== 'idle'
  const playing = mine && (state.status === 'playing' || state.status === 'loading')
  const current = mine ? fragment : null
  return (
    <div className="n-player" data-testid="audio-player" data-status={mine ? state.status : 'idle'}>
      <div className="n-player__controls">
        {playing ? (
          <button type="button" className="n-musician__button n-musician__button--primary" onClick={stop}>
            <span aria-hidden="true">Ⅱ</span> {t('preview.stop')}
          </button>
        ) : (
          <button type="button" className="n-musician__button n-musician__button--primary" onClick={() => playMusician(musicianId)}>
            <span aria-hidden="true">▷</span> {t('preview.play')}
          </button>
        )}
        {mine && (
          <button type="button" className="n-musician__button" onClick={() => playMusician(musicianId)}>
            {t('musician.nextFragment')}
          </button>
        )}
      </div>
      {mine && state.status === 'blocked' && <p role="status">{t('map.soundBlockedHint')}</p>}
      {current && (
        <div data-testid="audio-caption">
          <p className="n-player__title">
            <strong>{current.title}</strong> · {t('musician.clipDuration', { sec: current.durationSec })}
          </p>
          <p className="n-musician__note">{current.attribution}</p>
        </div>
      )}
    </div>
  )
}
