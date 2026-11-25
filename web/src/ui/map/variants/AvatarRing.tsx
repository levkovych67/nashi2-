import { useState } from 'react'
import '../map.css'

export interface PinSkinProps { name: string; thumbUrl?: string; hasAudio: boolean; playing: boolean; selected: boolean }

export const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0])
    .join('')

/** map.pin = AVATAR_RING: portrait (or initials) in a round marker; an outer ring and badge mark a fragment. */
export function AvatarRing({ name, thumbUrl, hasAudio, playing, selected }: PinSkinProps) {
  const [broken, setBroken] = useState(false)
  const classes = ['n-avatar', hasAudio && 'n-avatar--audio', playing && 'n-avatar--playing', selected && 'n-avatar--selected']
  return (
    <span className={classes.filter(Boolean).join(' ')} aria-hidden="true">
      {/* initials come from CSS so the pin's visible text does not have to be part of its accessible name */}
      <span className="n-avatar__face" data-initials={initials(name)}>
        {thumbUrl && !broken && (
          <img src={thumbUrl} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={() => setBroken(true)} />
        )}
      </span>
      {hasAudio && <i className="n-avatar__audio" />}
      {selected && <small className="n-map-label">{name}</small>}
    </span>
  )
}
