import { memo, type ComponentType } from 'react'
import { useCopy } from '../../core/i18n/copy'
import type { MapItem } from '../../core/map/aggregation'
import type { BubbleSkinProps } from './variants/BubbleCount'

export type PlaceItem = Extract<MapItem, { kind: 'place' }>

export const PlaceBubble = memo(function PlaceBubble({ item, skin: Skin, onSelect }: {
  item: PlaceItem
  skin: ComponentType<BubbleSkinProps> | null
  onSelect(item: PlaceItem): void
}) {
  const t = useCopy()
  return (
    <button
      type="button"
      className="n-marker"
      data-testid="place-bubble"
      aria-label={t('cluster.ariaLabel', { name: item.name, count: item.count })}
      onClick={() => onSelect(item)}
    >
      {Skin && <Skin count={item.count} tone="count" label={item.name} />}
    </button>
  )
})
