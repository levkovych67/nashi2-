import { useCopy } from '../../core/i18n/copy'

/**
 * Key of the CHOROPLETH_PLACE_PIN shading (handoff map.aggregation): «Менше музикантів — Більше» around a scale from
 * the lightest to the darkest region fill (fill opacity 0.15 … 0.6, see fill.ts).
 */
export function ChoroplethLegend() {
  const t = useCopy()
  return (
    <p className="n-choropleth-legend" data-testid="choropleth-legend">
      <span>{t('map.choroplethLess')}</span>
      <i className="n-choropleth-legend__scale" aria-hidden="true" />
      <span>{t('map.choroplethMore')}</span>
    </p>
  )
}
