import { useQueryClient } from '@tanstack/react-query'
import type { components } from '../../../api/types.gen'
import { useFragmentPlayer } from '../../../core/audio/useFragmentPlayer'
import { useCopy } from '../../../core/i18n/copy'
import type { AudioSkinProps } from '../MusicianAudio'
import '../musician.css'

type Fragment = components['schemas']['Fragment']

/** audio.player = IN_CARD: play / stop and "another fragment" beside the musician, with the title and the audio source. */
export function InCardPlayer({ musicianId, hasAudio }: AudioSkinProps) {
  const t = useCopy()
  const queryClient = useQueryClient()
  const { state, noAudio, playMusician, stop } = useFragmentPlayer()
  if (!hasAudio || noAudio.has(musicianId)) return <p className="n-player__none">{t('musician.noAudio')}</p>

  const mine = state.musicianId === musicianId && state.status !== 'idle'
  const playing = mine && (state.status === 'playing' || state.status === 'loading')
  // The fragment was fetched through the player's query just before it started.
  const fragment = mine
    ? queryClient
        .getQueriesData<Fragment | null>({ queryKey: ['fragment', musicianId] })
        .map(([, data]) => data)
        .find((f): f is Fragment => f?.clipId === state.clipId)
    : undefined
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
      {fragment && (
        <div data-testid="audio-caption">
          <p className="n-player__title">
            <strong>{fragment.title}</strong> · {t('musician.clipDuration', { sec: fragment.durationSec })}
          </p>
          <p className="n-musician__note">{fragment.attribution}</p>
        </div>
      )}
    </div>
  )
}
