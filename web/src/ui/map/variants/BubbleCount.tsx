import { formatCount } from '../../../core/map/aggregation'
import '../map.css'

/** `count` — a place or region; `cluster` — merged places or pins (double ring); `numeral` — region on a choropleth. */
export type BubbleTone = 'count' | 'cluster' | 'numeral'
export interface BubbleSkinProps { count: number; tone: BubbleTone; label?: string }

/** map.cluster = BUBBLE_COUNT: a compact circle with the number; places keep their name underneath. */
export function BubbleCount({ count, tone, label }: BubbleSkinProps) {
  return (
    <span className={`n-bubble n-bubble--${tone}`} aria-hidden="true">
      <b>{formatCount(count)}</b>
      {label && <small className="n-map-label">{label}</small>}
    </span>
  )
}
