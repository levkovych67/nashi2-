import { useEffect, useRef, useState } from 'react'
import { useCopy } from '../../../core/i18n/copy'
import { formatLikes } from '../LikesCountLabel'
import type { LikeSkinProps } from '../LikeButton'
import '../../icon.css'
import '../musician.css'

const MAX_BURSTS = 6
const ANNOUNCE_MS = 1000

/** like.button = HEART_BURST: each tap adds one like and lifts a "+1" above the heart. */
export function HeartBurst({ count, onTap }: LikeSkinProps) {
  const t = useCopy()
  const [bursts, setBursts] = useState<number[]>([])
  const seq = useRef(0)
  const label = formatLikes((n) => t('musician.likesCount', { count: n }), count)

  // Screen readers hear the total at most once a second while the visitor keeps tapping.
  const [announced, setAnnounced] = useState(label)
  useEffect(() => {
    const id = window.setTimeout(() => setAnnounced(label), ANNOUNCE_MS)
    return () => window.clearTimeout(id)
  }, [label])

  const tap = () => {
    onTap()
    setBursts((b) => [...b.slice(1 - MAX_BURSTS), ++seq.current])
    navigator.vibrate?.(10)
  }
  const done = (id: number) => setBursts((b) => b.filter((x) => x !== id))

  return (
    <div className="n-like">
      {/* the name starts with the visible text (WCAG 2.5.3 label in name) */}
      <button type="button" className="n-like__button" data-testid="like-button" aria-label={`${t('musician.likeLabel')}. ${t('musician.likeAria')}`} onClick={tap}>
        <span className="n-like__heart" aria-hidden="true">
          <span className="n-icon n-icon--heart" />
          {bursts.map((id) => (
            <i key={id} data-burst="" onAnimationEnd={() => done(id)}>
              +1
            </i>
          ))}
        </span>
        <span aria-hidden="true">{t('musician.likeLabel')}</span>
      </button>
      <strong className="n-like__count" data-testid="likes-count" aria-hidden="true">
        {label}
      </strong>
      <span className="n-sr-only" aria-live="polite">
        {announced}
      </span>
    </div>
  )
}
