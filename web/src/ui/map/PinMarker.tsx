import type { components } from '../../api/types.gen'
import { useDesign } from '../../core/design/config'
import { useCopy } from '../../core/i18n/copy'
import { resolveVariant } from '../variants'
import type { PinSkinProps } from './variants/AvatarRing'

type MapPin = components['schemas']['MapPin']

export function PinMarker({ pin, placeName, playing, selected, onActivate, onPointerEnter, onPointerLeave }: {
  pin: MapPin
  placeName: string
  playing: boolean
  selected: boolean
  onActivate(pin: MapPin): void
  onPointerEnter(id: string): void
  onPointerLeave(id: string): void
}) {
  const t = useCopy()
  const Skin = resolveVariant<PinSkinProps>('map.pin', useDesign())
  return (
    <button
      type="button"
      className="n-marker"
      data-testid="pin"
      data-slug={pin.slug}
      data-has-audio={String(pin.hasAudio)}
      aria-label={t('pin.ariaLabel', { name: pin.name, place: placeName })}
      aria-pressed={selected}
      onClick={() => onActivate(pin)}
      onPointerEnter={() => onPointerEnter(pin.id)}
      onPointerLeave={() => onPointerLeave(pin.id)}
    >
      {Skin && <Skin name={pin.name} thumbUrl={pin.thumbUrl} hasAudio={pin.hasAudio} playing={playing} selected={selected} />}
    </button>
  )
}
