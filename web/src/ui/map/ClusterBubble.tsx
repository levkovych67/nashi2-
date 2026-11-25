import { useDesign } from '../../core/design/config'
import { useCopy } from '../../core/i18n/copy'
import type { MapItem } from '../../core/map/aggregation'
import { resolveVariant } from '../variants'
import type { BubbleSkinProps } from './variants/BubbleCount'

export type ClusterItem = Extract<MapItem, { kind: 'placeCluster' | 'pinCluster' }>

/** Merged places or pins. `name` is the place the cluster is named after; `showName` labels it on the map. */
export function ClusterBubble({ item, name, showName, onSelect }: {
  item: ClusterItem
  name: string
  showName: boolean
  onSelect(item: ClusterItem): void
}) {
  const t = useCopy()
  const Skin = resolveVariant<BubbleSkinProps>('map.cluster', useDesign())
  return (
    <button
      type="button"
      className="n-marker"
      data-testid="cluster-bubble"
      aria-label={t('cluster.ariaLabel', { name, count: item.count })}
      onClick={() => onSelect(item)}
    >
      {Skin && <Skin count={item.count} tone="cluster" label={showName ? name : undefined} />}
    </button>
  )
}
