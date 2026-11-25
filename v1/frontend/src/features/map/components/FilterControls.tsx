import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Check, ChevronDown, RotateCcw, Search, X } from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import type { components } from '@/lib/api/generated/types';
import {
  PERIOD_KEYS,
  PIN_KINDS,
  type FacetCounts,
  type PeriodKey,
  type PinFilters,
  type PinKind,
} from '../utils/pinFilters';
import { KIND_CHIP_LABEL, PERIOD_LABEL, genreChips } from '../utils/pinLabels';

type MapPinDTO = components['schemas']['MapPinDTO'];
type RegionDTO = components['schemas']['RegionDTO'];

/** Everything the desktop panel and the mobile sheet need; MapPage owns the state (the URL). */
export interface MapFilterUiProps {
  filters: PinFilters;
  counts: FacetCounts;
  regions: RegionDTO[];
  /** Pins that pass the filters, sorted by name; `null` while the pins load or when they failed to. */
  results: MapPinDTO[] | null;
  /** The pins failed to load: the results say so instead of «Завантаження…». */
  resultsFailed: boolean;
  /** Changes whenever the filters do, so the results list starts again from the first 50. */
  resultsKey: string;
  /** Changes on «Скинути», so a search field drops text it has not sent yet. */
  searchKey: number;
  onChange: (next: PinFilters) => void;
  onSearch: (q: string) => void;
  onReset: () => void;
  onSelect: (pin: MapPinDTO) => void;
}

const formatCount = (count: number) => count.toLocaleString('uk-UA');

function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

/**
 * The name search. It keeps its own text so typing stays smooth and spaces survive, and sends it 250 ms after the last
 * keystroke (Enter sends at once). A new `value` that this field did not send (back button, «Скинути») replaces the text.
 */
export function MapSearchField({
  value,
  onSearch,
  className,
}: {
  value: string;
  onSearch: (q: string) => void;
  className?: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [text, setText] = useState(value);
  const [seenValue, setSeenValue] = useState(value);
  const [sentValue, setSentValue] = useState(value);
  if (value !== seenValue) {
    setSeenValue(value);
    if (value !== sentValue) {
      setText(value);
      setSentValue(value);
    }
  }

  const send = (next: string) => {
    setSentValue(next.trim());
    onSearch(next);
  };

  useEffect(() => {
    if (text.trim() === sentValue) return;
    const timer = setTimeout(() => {
      setSentValue(text.trim());
      onSearch(text);
    }, 250);
    return () => clearTimeout(timer);
  }, [text, sentValue, onSearch]);

  return (
    <div className={cn('aero-field relative flex min-h-[44px] items-center', className)}>
      <Search className="pointer-events-none absolute left-2.5 h-4 w-4 text-[var(--aero-text-2)]" aria-hidden="true" />
      <input
        ref={inputRef}
        type="search"
        enterKeyHint="search"
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            send(text);
            event.currentTarget.blur();
          }
        }}
        placeholder="Пошук за ім'ям"
        aria-label="Пошук за ім'ям"
        className={cn(
          'h-11 w-full min-w-0 text-ellipsis bg-transparent pl-8 text-sm outline-none placeholder:text-[#6d7a88] [&::-webkit-search-cancel-button]:appearance-none',
          text ? 'pr-11' : 'pr-2',
        )}
      />
      {text && (
        <button
          type="button"
          onClick={() => {
            setText('');
            send('');
            inputRef.current?.focus();
          }}
          className="absolute right-0 flex h-11 w-11 items-center justify-center text-[var(--aero-text-2)] hover:text-[var(--aero-text)]"
          aria-label="Очистити пошук"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}

/** «Скинути»: clears every filter. */
export function ResetFiltersButton({ onReset, className }: { onReset: () => void; className?: string }) {
  return (
    <Button variant="ghost" size="sm" onClick={onReset} className={cn('gap-1.5 px-3 text-sm', className)}>
      <RotateCcw aria-hidden="true" />
      Скинути
    </Button>
  );
}

/** A toggle chip: a 36 px command button inside a 44 px tap target, with its count; empty ones are disabled. */
function Chip({
  label,
  count,
  selected,
  onToggle,
}: {
  label: string;
  count: number;
  selected: boolean;
  onToggle: () => void;
}) {
  const disabled = count === 0 && !selected;
  return (
    <button
      type="button"
      aria-pressed={selected}
      disabled={disabled}
      onClick={onToggle}
      className="group flex min-h-[44px] items-center focus-visible:outline-none disabled:pointer-events-none"
    >
      <span
        data-state={selected ? 'active' : undefined}
        className="aero-cmd inline-flex h-9 items-center gap-1.5 px-3 text-sm group-focus-visible:outline group-focus-visible:outline-2 group-focus-visible:outline-offset-2 group-focus-visible:outline-[var(--aero-focus)] group-disabled:opacity-45"
      >
        {selected && <Check className="h-3.5 w-3.5 text-[var(--aero-instruction)]" strokeWidth={3} aria-hidden="true" />}
        <span>{label}</span>
        <span className="text-xs tabular-nums text-[var(--aero-text-2)]">{formatCount(count)}</span>
      </span>
    </button>
  );
}

function FilterGroup({ title, children }: { title: string; children: ReactNode }) {
  const id = useId();
  return (
    <div role="group" aria-labelledby={id}>
      <h3 id={id} className="aero-heading mb-0.5 text-[15px]">
        {title}
      </h3>
      {children}
    </div>
  );
}

function RegionSelect({
  regions,
  selected,
  counts,
  onChange,
}: {
  regions: RegionDTO[];
  selected: string[];
  counts: Record<string, number>;
  onChange: (next: string[]) => void;
}) {
  const labelOf = (key: string) => regions.find((region) => region.key === key)?.label ?? key;
  const summary =
    selected.length === 0 ? 'Усі області' : selected.length === 1 ? labelOf(selected[0]) : `Обрано: ${selected.length}`;

  return (
    <div className="space-y-1 pt-1">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            aria-label={`Область: ${summary}`}
            className="aero-field flex min-h-[44px] w-full items-center justify-between gap-2 px-3 text-left text-sm"
          >
            <span className="truncate">{summary}</span>
            <ChevronDown className="h-4 w-4 shrink-0 text-[var(--aero-text-2)]" aria-hidden="true" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          align="start"
          className="z-[10000] max-h-[min(22rem,var(--radix-dropdown-menu-content-available-height))] w-[var(--radix-dropdown-menu-trigger-width)]"
        >
          {regions.map((region) => {
            const key = region.key ?? '';
            const count = counts[key] ?? 0;
            const checked = selected.includes(key);
            return (
              <DropdownMenuCheckboxItem
                key={key}
                checked={checked}
                disabled={count === 0 && !checked}
                // Keep the menu open: several regions are usually picked in a row.
                onSelect={(event) => event.preventDefault()}
                onCheckedChange={() => onChange(toggle(selected, key))}
                className="min-h-[44px]"
              >
                <span className="min-w-0 flex-1 truncate">{region.label}</span>
                <span className="ml-3 text-xs tabular-nums text-[var(--aero-text-2)]">{formatCount(count)}</span>
              </DropdownMenuCheckboxItem>
            );
          })}
        </DropdownMenuContent>
      </DropdownMenu>

      {selected.length > 0 && (
        <ul className="flex flex-wrap gap-x-1.5" aria-label="Обрані області">
          {selected.map((key) => (
            <li key={key}>
              <button
                type="button"
                onClick={() => onChange(selected.filter((item) => item !== key))}
                aria-label={`Прибрати: ${labelOf(key)}`}
                className="group flex min-h-[44px] items-center focus-visible:outline-none"
              >
                <span className="aero-tag gap-1 py-1 text-[13px] group-hover:border-[#3c7fb1] group-focus-visible:outline group-focus-visible:outline-2 group-focus-visible:outline-offset-2 group-focus-visible:outline-[var(--aero-focus)]">
                  {labelOf(key)}
                  <X className="h-3.5 w-3.5" aria-hidden="true" />
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** The four filter groups: «Тип», «Область», «Період», «Жанр». Each value shows how many pins it would add. */
export function FilterControls({
  filters,
  counts,
  regions,
  onChange,
}: Pick<MapFilterUiProps, 'filters' | 'counts' | 'regions' | 'onChange'>) {
  const genres = genreChips(counts.genres, filters.genres);

  return (
    <div className="space-y-3">
      <FilterGroup title="Тип">
        <div className="flex flex-wrap gap-x-1.5">
          {PIN_KINDS.map((kind: PinKind) => (
            <Chip
              key={kind}
              label={KIND_CHIP_LABEL[kind]}
              count={counts.kinds[kind]}
              selected={filters.kinds.includes(kind)}
              onToggle={() => onChange({ ...filters, kinds: toggle(filters.kinds, kind) })}
            />
          ))}
        </div>
      </FilterGroup>

      <FilterGroup title="Область">
        <RegionSelect
          regions={regions}
          selected={filters.regions}
          counts={counts.regions}
          onChange={(next) => onChange({ ...filters, regions: next })}
        />
      </FilterGroup>

      <FilterGroup title="Період">
        <div className="flex flex-wrap gap-x-1.5">
          {PERIOD_KEYS.map((period: PeriodKey) => (
            <Chip
              key={period}
              label={PERIOD_LABEL[period]}
              count={counts.periods[period]}
              selected={filters.periods.includes(period)}
              onToggle={() => onChange({ ...filters, periods: toggle(filters.periods, period) })}
            />
          ))}
        </div>
      </FilterGroup>

      {genres.length > 0 && (
        <FilterGroup title="Жанр">
          <div className="flex flex-wrap gap-x-1.5">
            {genres.map((genre) => (
              <Chip
                key={genre}
                label={genre}
                count={counts.genres[genre] ?? 0}
                selected={filters.genres.includes(genre)}
                onToggle={() => onChange({ ...filters, genres: toggle(filters.genres, genre) })}
              />
            ))}
          </div>
        </FilterGroup>
      )}
    </div>
  );
}
