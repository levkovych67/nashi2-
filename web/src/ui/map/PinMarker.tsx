import { memo, type ComponentType } from 'react'
import type { components } from '../../api/types.gen'
import { useCopy } from '../../core/i18n/copy'
import { usePinInput, type PinInput } from './pinInput'
import type { PinSkinProps } from './variants/AvatarRing'

type MapPin = components['schemas']['MapPin']

/** A musician's pin. Hover is reported only for a mouse pointer; a click says whether a mouse or a tap made it. */
export const PinMarker = memo(function PinMarker({ pin, placeName, playing, selected, skin: Skin, badge: Badge = null, onActivate, onHoverStart, onHoverEnd }: {
  pin: MapPin
  placeName: string
  playing: boolean
  selected: boolean
  skin: ComponentType<PinSkinProps> | null
  /** audio.desktop-hover skin, drawn next to the pin while it plays */
  badge?: ComponentType | null
  onActivate(pin: MapPin, input: PinInput): void
  onHoverStart(id: string): void
  onHoverEnd(id: string): void
}) {
  const t = useCopy()
  const input = usePinInput()
  return (
    <button
      type="button"
      className="n-marker"
      data-testid="pin"
      data-slug={pin.slug}
      data-has-audio={String(pin.hasAudio)}
      aria-label={t('pin.ariaLabel', { name: pin.name, place: placeName })}
      aria-pressed={selected}
      onPointerDown={input.onPointerDown}
      onClick={() => onActivate(pin, input.take())}
      onPointerEnter={(e) => {
        if (e.pointerType === 'mouse') onHoverStart(pin.id)
      }}
      onPointerLeave={(e) => {
        if (e.pointerType === 'mouse') onHoverEnd(pin.id)
      }}
    >
      {Skin && <Skin name={pin.name} thumbUrl={pin.thumbUrl} hasAudio={pin.hasAudio} playing={playing} selected={selected} />}
      {playing && Badge && <Badge />}
    </button>
  )
})
