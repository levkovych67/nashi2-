import { memo, type ComponentType } from 'react'
import { useCopy } from '../../core/i18n/copy'
import type { MapItem } from '../../core/map/aggregation'
import type { BubbleSkinProps } from './variants/BubbleCount'

export type RegionItem = Extract<MapItem, { kind: 'region' }>

export const RegionBubble = memo(function RegionBubble({ item, choropleth, skin: Skin, onSelect }: {
  item: RegionItem
  choropleth: boolean
  skin: ComponentType<BubbleSkinProps> | null
  onSelect(item: RegionItem): void
}) {
  const t = useCopy()
  return (
    <button
      type="button"
      className="n-marker"
      data-testid="region-bubble"
      aria-label={t('cluster.ariaLabel', { name: item.name, count: item.count })}
      onClick={() => onSelect(item)}
    >
      {Skin && <Skin count={item.count} tone={choropleth ? 'numeral' : 'count'} />}
    </button>
  )
})
