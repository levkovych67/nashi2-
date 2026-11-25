import { useDesign } from '../../core/design/config'
import { useCopy } from '../../core/i18n/copy'
import type { MapItem } from '../../core/map/aggregation'
import { resolveVariant } from '../variants'
import type { BubbleSkinProps } from './variants/BubbleCount'

type RegionItem = Extract<MapItem, { kind: 'region' }>

export function RegionBubble({ item, choropleth, onSelect }: { item: RegionItem; choropleth: boolean; onSelect(item: RegionItem): void }) {
  const t = useCopy()
  const Skin = resolveVariant<BubbleSkinProps>('map.cluster', useDesign())
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
}
