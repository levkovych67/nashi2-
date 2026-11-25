import { t } from '../proto/copy/t'
import { useMemo } from 'react'
import { useSound, type AudioPin, type Clip, type SoundDeck } from './sound'

export function PlaybackButton({
  pin,
  deck,
  clip,
}: {
  pin: AudioPin
  deck: SoundDeck
  clip?: Clip
}) {
  const tracks = deck.lists[pin.id]
  if (tracks === null)
    return (
      <button onClick={deck.retry}>Повторити завантаження фрагмента</button>
    )
  if (tracks?.length === 0 || (!pin.hasAudio && !tracks))
    return <p>{t('musician.noAudio')}</p>
  const active =
    deck.state.id === pin.id &&
    (!clip || deck.state.clip?.clipId === clip.clipId)
  const playing =
    active &&
    (deck.state.status === 'playing' || deck.state.status === 'loading')
  return (
    <>
      <button
        type="button"
        className="artist-play"
        disabled={!tracks || deck.muted}
        aria-pressed={playing}
        onClick={() => deck.toggle(pin, false, clip)}
      >
        <span aria-hidden="true">{playing ? 'Ⅱ' : '▷'}</span>{' '}
        {playing
          ? 'Пауза'
          : active && deck.state.status === 'paused'
            ? 'Продовжити'
            : t('preview.play')}
      </button>
      {active && deck.state.status === 'blocked' && (
        <p role="status">
          Відтворення недоступне. Натисніть кнопку, щоб спробувати знову.
        </p>
      )}
    </>
  )
}
export function SampleButton({ id }: { id: string }) {
  const pins = useMemo(() => [{ id, hasAudio: true }], [id])
  const deck = useSound(pins)
  return <PlaybackButton pin={pins[0]} deck={deck} />
}
