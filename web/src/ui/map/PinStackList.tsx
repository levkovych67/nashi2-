import type { components } from '../../api/types.gen'
import { useDesign } from '../../core/design/config'
import { resolveVariant } from '../variants'
import { usePinInput, type PinInput } from './pinInput'
import type { PinSkinProps } from './variants/AvatarRing'

type MapPin = components['schemas']['MapPin']

const byName = (a: MapPin, b: MapPin) => a.name.localeCompare(b.name, 'uk')

/** Musicians of a stack that no zoom can split (identical coordinates at zoom 17), shown as a list in a sheet. */
export function PinStackList({ title, pins, onSelect }: {
  title: string
  pins: MapPin[]
  onSelect(pin: MapPin, input: PinInput): void
}) {
  const Skin = resolveVariant<PinSkinProps>('map.pin', useDesign())
  const input = usePinInput()
  return (
    <div className="n-stack">
      <h2 className="n-sheet__title">{title}</h2>
      <ul className="n-stack__list">
        {[...pins].sort(byName).map((pin) => (
          <li key={pin.id}>
            <button
              type="button"
              className="n-stack__item"
              data-slug={pin.slug}
              onPointerDown={input.onPointerDown}
              onClick={() => onSelect(pin, input.take())}
            >
              {Skin && <Skin name={pin.name} thumbUrl={pin.thumbUrl} hasAudio={pin.hasAudio} playing={false} selected={false} />}
              <span>{pin.name}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
