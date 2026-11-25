// Ukrainian labels for the map filters, the results list and the pin card. Plain TS: node:test runs it.
import { CATEGORY_LABEL } from '../../artists/utils/lifeDates.ts';
import { pinKinds, topGenres, type FilterablePin, type PeriodKey, type PinKind } from './pinFilters.ts';

/** Chip labels of the «Тип» group. */
export const KIND_CHIP_LABEL: Record<PinKind, string> = {
  MUSICIAN: 'Музиканти',
  BAND: 'Гурти',
  VISUAL_ARTIST: 'Художники',
  WRITER: 'Письменники',
  EVENT: 'Події',
};

/** Chip labels of the «Період» group. */
export const PERIOD_LABEL: Record<PeriodKey, string> = {
  'before-1900': 'До 1900',
  '1900-1945': '1900–1945',
  '1946-1990': '1946–1990',
  'from-1991': 'Від 1991',
};

/** One label per kind: «Письменник», «Музикант»; an event reads «Подія». Unknown categories are left out. */
export function pinKindLabels(pin: FilterablePin): string[] {
  return pinKinds(pin)
    .map((kind) => (kind === 'EVENT' ? 'Подія' : CATEGORY_LABEL[kind]))
    .filter((label): label is string => Boolean(label));
}

/** A band's `year` is its foundation («з 1988»), a person's is the birth («нар. 1974»). Events have none. */
export function pinYearLabel(pin: FilterablePin): string | null {
  if (pin.type === 'EVENT' || pin.year == null) return null;
  return pinKinds(pin).includes('BAND') ? `з ${pin.year}` : `нар. ${pin.year}`;
}

/** The second line of a result row: «Письменник, Музикант · Харків · нар. 1974». */
export function pinSummary(pin: FilterablePin & { city?: string }): string {
  return [pinKindLabels(pin).join(', '), pin.city, pinYearLabel(pin)].filter(Boolean).join(' · ');
}

/** Genre chips: the chosen ones first, in the order they were chosen, then the n most common of the rest. */
export function genreChips(counts: Record<string, number>, selected: string[], n = 12): string[] {
  const unchosen = Object.fromEntries(Object.entries(counts).filter(([genre]) => !selected.includes(genre)));
  return [...selected, ...topGenres(unchosen, [], n)];
}

// Same order as name.localeCompare(other, 'uk'); one collator is much faster over thousands of pins.
const ukCollator = new Intl.Collator('uk');

/** A copy sorted by name in Ukrainian alphabetical order. */
export function sortByName<T extends { name?: string }>(pins: T[]): T[] {
  return [...pins].sort((a, b) => ukCollator.compare(a.name ?? '', b.name ?? ''));
}
