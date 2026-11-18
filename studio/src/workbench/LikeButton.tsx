import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
} from 'react'
import { likesFor } from './likes'
import { t } from '../proto/copy/t'

export const ReactionFeedback = createContext<(() => void) | undefined>(
  undefined,
)
export const likesLabel = (count: number) => t('musician.likesCount', { count })
export function LikeButton({
  id,
  initial,
  variant = 'HEART_BURST',
}: {
  id: string
  initial: number
  variant?: string
}) {
  const onReaction = useContext(ReactionFeedback)
  const batch = likesFor(id, initial)
  const value = useSyncExternalStore(batch.subscribe, batch.get)
  const [live, setLive] = useState(value)
  const [burst, setBurst] = useState(0)
  const lastPointer = useRef(false)
  const lastAnnounced = useRef(0)
  useEffect(() => {
    const next = setTimeout(
      () => {
        setLive(value)
        lastAnnounced.current = Date.now()
      },
      Math.max(0, 1000 - (Date.now() - lastAnnounced.current)),
    )
    return () => clearTimeout(next)
  }, [value])
  useEffect(() => {
    const save = () => {
      void batch.flush(true)
    }
    const hidden = () => {
      if (document.visibilityState === 'hidden') save()
    }
    window.addEventListener('pagehide', save)
    document.addEventListener('visibilitychange', hidden)
    return () => {
      window.removeEventListener('pagehide', save)
      document.removeEventListener('visibilitychange', hidden)
      save()
    }
  }, [batch])
  function tap() {
    batch.tap()
    onReaction?.()
    setBurst((n) => n + 1)
    navigator.vibrate?.(10)
  }
  const symbol =
    variant === 'APPLAUSE' ? '👏' : variant === 'CUSTOM_SYMBOL' ? '❖' : '♥'
  return (
    <div className={'artist-reaction ' + variant}>
      <button
        type="button"
        data-testid="like-button"
        aria-label={t('musician.likeAria')}
        onPointerDown={(e) => {
          if (e.button !== 0 || !e.isPrimary) return
          lastPointer.current = true
          tap()
        }}
        onClick={(e) => {
          if (e.detail === 0 || !lastPointer.current) tap()
          lastPointer.current = false
        }}
      >
        <span className="reaction-symbol" aria-hidden="true">
          {symbol}
          {burst > 0 && (
            <i key={burst}>{variant === 'HEART_BURST' ? '+1' : ''}</i>
          )}
        </span>
        {variant === 'APPLAUSE' ? 'Аплодувати' : 'Подобається'}
      </button>
      <strong aria-hidden="true" data-testid="likes-count">
        {likesLabel(value)}
      </strong>
      <span className="artist-sr" aria-live="polite">
        {likesLabel(live)}
      </span>
    </div>
  )
}
