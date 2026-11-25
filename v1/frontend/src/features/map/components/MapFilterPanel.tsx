import { activeFilterCount } from '../utils/filterParams';
import { FilterControls, MapSearchField, ResetFiltersButton, type MapFilterUiProps } from './FilterControls';
import { ResultsList } from './ResultsList';

/** From 1024 px: a glass panel along the left edge of the map with the search, the filters and the results. */
export function MapFilterPanel(props: MapFilterUiProps) {
  const { filters, results, resultsFailed, resultsKey, searchKey, onSearch, onReset, onSelect } = props;

  return (
    <aside
      aria-label="Фільтри карти"
      className="aero-glass absolute bottom-3 left-3 top-3 z-10 flex w-[360px] flex-col overflow-y-auto overscroll-contain"
    >
      <div className="flex min-h-[52px] items-center justify-between gap-2 pl-4 pr-2 pt-2">
        <h2 className="aero-heading text-xl">Фільтри</h2>
        {activeFilterCount(filters) > 0 && <ResetFiltersButton onReset={onReset} />}
      </div>

      <div className="space-y-3 px-4 pb-4">
        <MapSearchField key={searchKey} value={filters.q} onSearch={onSearch} />
        <FilterControls {...props} />
      </div>

      <section aria-labelledby="map-results-heading" className="border-t border-[var(--aero-glass-edge)]">
        <h2 id="map-results-heading" className="px-4 pb-1 pt-3 text-sm font-semibold text-[var(--aero-text)]" aria-live="polite">
          {results
            ? `Знайдено: ${results.length.toLocaleString('uk-UA')}`
            : resultsFailed
              ? 'Не вдалося завантажити митців'
              : 'Завантаження…'}
        </h2>
        {results && <ResultsList key={resultsKey} pins={results} onSelect={onSelect} />}
      </section>
    </aside>
  );
}
