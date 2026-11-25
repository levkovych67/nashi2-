import '../map.css'

/** audio.desktop-hover = EQUALIZER_BADGE: three moving bars next to the pin that is playing. */
export function EqualizerBadge() {
  return (
    <span className="n-eq" data-testid="playing-badge" aria-hidden="true">
      <b />
      <b />
      <b />
    </span>
  )
}
