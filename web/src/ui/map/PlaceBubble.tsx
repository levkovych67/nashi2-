import { useDesign } from '../../core/design/config'
import { useCopy } from '../../core/i18n/copy'
import type { MapItem } from '../../core/map/aggregation'
import { resolveVariant } from '../variants'
import type { BubbleSkinProps } from './variants/BubbleCount'

type PlaceItem = Extract<MapItem, { kind: 'place' }>

export function PlaceBubble({ item, onSelect }: { item: PlaceItem; onSelect(item: PlaceItem): void }) {
  const t = useCopy()
  const Skin = resolveVariant<BubbleSkinProps>('map.cluster', useDesign())
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
}
