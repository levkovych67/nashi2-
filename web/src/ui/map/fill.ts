import type { ExpressionSpecification } from 'maplibre-gl'

// The studio used 0.75; that drowns the amber place bubbles on top of the selected region.
const SELECTED = 0.35
const CHOROPLETH_MIN = 0.15
const CHOROPLETH_RANGE = 0.45

/**
 * Fill opacity per region code. Selected regions stand out; CHOROPLETH_PLACE_PIN additionally shades every region
 * with musicians by the rank of its count (fewest 0.15 … most 0.6), as in the studio prototype.
 */
export function regionFillOpacity(counts: Record<string, number>, selected: string[], choropleth: boolean): Record<string, number> {
  const out: Record<string, number> = {}
  if (choropleth) {
    const withMusicians = Object.entries(counts).filter(([, n]) => n > 0)
    const steps = Math.max(1, withMusicians.length - 1)
    for (const [code, n] of withMusicians) {
      const fewer = withMusicians.filter(([, other]) => other < n).length
      out[code] = CHOROPLETH_MIN + (CHOROPLETH_RANGE * fewer) / steps
    }
  }
  for (const code of selected) out[code] = SELECTED
  return out
}

/** `fill-opacity` of the region layer: the opacity of the feature's `code`, 0 when it has none. */
export function fillOpacityExpression(opacity: Record<string, number>): ExpressionSpecification {
  return ['to-number', ['get', ['to-string', ['get', 'code']], ['literal', opacity]], 0]
}
