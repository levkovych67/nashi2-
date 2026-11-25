import { useState } from 'react';
import { List, SlidersHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { activeFilterCount } from '../utils/filterParams';
import { FilterControls, MapSearchField, ResetFiltersButton, type MapFilterUiProps } from './FilterControls';
import { ResultsList } from './ResultsList';

/**
 * Below 1024 px: a glass bar along the bottom of the map with the search, «Фільтри (n)» and «Список». The filters and the
 * results each open in a bottom sheet.
 */
export function MapFilterSheet(props: MapFilterUiProps) {
  const { filters, results, resultsFailed, resultsKey, searchKey, onSearch, onReset, onSelect } = props;
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [listOpen, setListOpen] = useState(false);

  // The search sits next to the button, so «Фільтри (n)» counts only what the sheet holds.
  const sheetFilterCount = activeFilterCount({ ...filters, q: '' });
  const total = results ? results.length.toLocaleString('uk-UA') : '…';
  // Without pins there is nothing to count: the list says why, the button just closes the sheet.
  const listTitle = resultsFailed ? 'Не вдалося завантажити митців' : `Знайдено: ${total}`;
  const showLabel = resultsFailed ? 'Показати' : `Показати ${total}`;

  return (
    <>
      {/* 24 px above the map's bottom edge: the taskbar's «Додати» orb rises 20 px above the taskbar into the map. */}
      <div className="aero-glass absolute inset-x-2 bottom-6 z-10 flex items-center gap-1 p-1.5 sm:inset-x-3 sm:gap-1.5">
        <MapSearchField key={searchKey} value={filters.q} onSearch={onSearch} className="min-w-0 flex-1" />
        <Button
          variant="outline"
          onClick={() => setFiltersOpen(true)}
          data-state={sheetFilterCount > 0 ? 'active' : undefined}
          className="flex-none gap-1 px-2 text-[13px] sm:gap-1.5 sm:px-3 sm:text-sm"
        >
          <SlidersHorizontal aria-hidden="true" />
          {sheetFilterCount > 0 ? `Фільтри (${sheetFilterCount})` : 'Фільтри'}
        </Button>
        <Button variant="outline" onClick={() => setListOpen(true)} className="flex-none gap-1 px-2 text-[13px] sm:gap-1.5 sm:px-3 sm:text-sm">
          <List aria-hidden="true" />
          Список
        </Button>
      </div>

      <Sheet open={filtersOpen} onOpenChange={setFiltersOpen}>
        <SheetContent side="bottom" className="h-[85vh]">
          <SheetHeader className="flex-row items-center justify-between gap-2 space-y-0 text-left">
            <SheetTitle className="text-2xl">Фільтри</SheetTitle>
            <SheetDescription className="sr-only">Тип, область, період і жанр</SheetDescription>
            {activeFilterCount(filters) > 0 && <ResetFiltersButton onReset={onReset} className="-my-2" />}
          </SheetHeader>
          <div className="-mx-6 min-h-0 flex-1 overflow-y-auto px-6">
            <FilterControls {...props} />
          </div>
          <Button className="w-full flex-none" size="lg" onClick={() => setFiltersOpen(false)}>
            {showLabel}
          </Button>
        </SheetContent>
      </Sheet>

      <Sheet open={listOpen} onOpenChange={setListOpen}>
        <SheetContent side="bottom" className="h-[85vh]">
          <SheetHeader className="text-left">
            <SheetTitle className="text-2xl">{listTitle}</SheetTitle>
            <SheetDescription className="sr-only">Митці й події, що відповідають фільтрам</SheetDescription>
          </SheetHeader>
          <div className="-mx-6 -mb-6 min-h-0 flex-1 overflow-y-auto px-2">
            {results && (
              <ResultsList
                key={resultsKey}
                pins={results}
                onSelect={(pin) => {
                  setListOpen(false);
                  onSelect(pin);
                }}
              />
            )}
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}
