import '../map.css'

/** map.origin-notice = LEGEND_BAR: the full explanation in a bar under the map, always visible. */
export function LegendBar({ text }: { text: string }) {
  return <p className="n-legend-bar">{text}</p>
}
