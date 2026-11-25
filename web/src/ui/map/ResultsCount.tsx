import { useCopy } from '../../core/i18n/copy'

/** How many musicians match the filters. */
export function ResultsCount({ count }: { count: number }) {
  const t = useCopy()
  return (
    <p className="n-results-count" data-testid="results-count" data-count={count}>
      {t('map.resultsCount', { count })}
    </p>
  )
}
