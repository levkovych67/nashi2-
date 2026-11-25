import { memo, useMemo, type ComponentType } from 'react'
import type { components } from '../../api/types.gen'
import { useDesign } from '../../core/design/config'
import type { MapItem } from '../../core/map/aggregation'
import { resolveVariant } from '../variants'
import { ClusterBubble, type ClusterItem } from './ClusterBubble'
import { MapMarker } from './MapMarker'
import { itemKey } from './markers'
import type { PinInput } from './pinInput'
import { PinMarker } from './PinMarker'
import { PlaceBubble, type PlaceItem } from './PlaceBubble'
import { RegionBubble, type RegionItem } from './RegionBubble'
import type { PinSkinProps } from './variants/AvatarRing'
import type { BubbleSkinProps } from './variants/BubbleCount'

type MapPin = components['schemas']['MapPin']

/** Must keep their identity across renders, or every marker re-renders. */
export interface MarkerHandlers {
  onRegion(item: RegionItem): void
  onPlace(item: PlaceItem): void
  onCluster(item: ClusterItem): void
  onPin(pin: MapPin, input: PinInput): void
  onHoverStart(id: string): void
  onHoverEnd(id: string): void
}

interface Skins { pin: ComponentType<PinSkinProps> | null; bubble: ComponentType<BubbleSkinProps> | null }

/**
 * The map's markers. Each one is memoized on its own props (the item object, its name and its pin's
 * playing/selected flags), so an audio or preview change re-renders only the pins it concerns.
 */
export function MapMarkers({ items, names, placeName, numeralRegions, playingId, selectedId, handlers }: {
  items: MapItem[]
  names: ReadonlyMap<string, string>
  placeName: ReadonlyMap<string, string>
  numeralRegions: boolean
  playingId: string | null
  selectedId: string | null
  handlers: MarkerHandlers
}) {
  const design = useDesign()
  const skins = useMemo<Skins>(
    () => ({
      pin: resolveVariant<PinSkinProps>('map.pin', design),
      bubble: resolveVariant<BubbleSkinProps>('map.cluster', design),
    }),
    [design],
  )
  return items.map((item) => {
    const key = itemKey(item)
    const pinId = item.kind === 'pin' ? item.pin.id : null
    return (
      <ItemMarker
        key={key}
        item={item}
        name={names.get(key) ?? ''}
        placeName={item.kind === 'pin' ? (placeName.get(item.pin.placeId) ?? '') : ''}
        playing={pinId !== null && pinId === playingId}
        selected={pinId !== null && pinId === selectedId}
        numeralRegions={numeralRegions}
        skins={skins}
        handlers={handlers}
      />
    )
  })
}

const ItemMarker = memo(function ItemMarker({ item, name, placeName, playing, selected, numeralRegions, skins, handlers }: {
  item: MapItem
  name: string
  placeName: string
  playing: boolean
  selected: boolean
  numeralRegions: boolean
  skins: Skins
  handlers: MarkerHandlers
}) {
  const { lon, lat } = item.kind === 'pin' ? item.pin : item
  return (
    <MapMarker lon={lon} lat={lat} raised={selected}>
      {item.kind === 'region' ? (
        <RegionBubble item={item} choropleth={numeralRegions} skin={skins.bubble} onSelect={handlers.onRegion} />
      ) : item.kind === 'place' ? (
        <PlaceBubble item={item} skin={skins.bubble} onSelect={handlers.onPlace} />
      ) : item.kind === 'pin' ? (
        <PinMarker
          pin={item.pin}
          placeName={placeName}
          playing={playing}
          selected={selected}
          skin={skins.pin}
          onActivate={handlers.onPin}
          onHoverStart={handlers.onHoverStart}
          onHoverEnd={handlers.onHoverEnd}
        />
      ) : (
        <ClusterBubble
          item={item}
          name={name}
          showName={item.kind === 'placeCluster'}
          skin={skins.bubble}
          onSelect={handlers.onCluster}
        />
      )}
    </MapMarker>
  )
})
