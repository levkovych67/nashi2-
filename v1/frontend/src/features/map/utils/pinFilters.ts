// Pure, framework-free pin filtering: the map loads every pin once and filters in the browser.
export type PinKind = 'MUSICIAN' | 'BAND' | 'VISUAL_ARTIST' | 'WRITER' | 'EVENT';
export type PeriodKey = 'before-1900' | '1900-1945' | '1946-1990' | 'from-1991';

export const PIN_KINDS: PinKind[] = ['MUSICIAN', 'BAND', 'VISUAL_ARTIST', 'WRITER', 'EVENT'];
export const PERIOD_KEYS: PeriodKey[] = ['before-1900', '1900-1945', '1946-1990', 'from-1991'];

export interface PinFilters {
  q: string;
  kinds: PinKind[];
  regions: string[];
  periods: PeriodKey[];
  genres: string[];
}

export const EMPTY_FILTERS: PinFilters = { q: '', kinds: [], regions: [], periods: [], genres: [] };

export interface FilterablePin {
  type?: string;
  name?: string;
  categories?: string[];
  region?: string;
  year?: number;
  genres?: string[];
}

export interface FacetCounts {
  kinds: Record<PinKind, number>;
  regions: Record<string, number>;
  periods: Record<PeriodKey, number>;
  genres: Record<string, number>;
}

export function pinKinds(pin: FilterablePin): PinKind[] {
  if (pin.type === 'EVENT') return ['EVENT'];
  return pin.categories && pin.categories.length > 0 ? (pin.categories as PinKind[]) : ['MUSICIAN'];
}

export function periodOf(year?: number | null): PeriodKey | null {
  if (year == null || Number.isNaN(year)) return null;
  if (year <= 1899) return 'before-1900';
  if (year <= 1945) return '1900-1945';
  if (year <= 1990) return '1946-1990';
  return 'from-1991';
}

export function normalizeName(s: string): string {
  return s
    .toLowerCase()
    .normalize('NFD')
    .replace(/\u0301/g, '') // the combining acute accent: a stress mark
    .normalize('NFC')
    .replace(/['ʼ’`]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

type Skip = 'kinds' | 'regions' | 'periods' | 'genres' | null;

function matches(pin: FilterablePin, f: PinFilters, q: string, skip: Skip): boolean {
  const isEvent = pin.type === 'EVENT';
  if (q && !normalizeName(pin.name ?? '').includes(q)) return false;
  if (skip !== 'kinds' && f.kinds.length > 0 && !pinKinds(pin).some((k) => f.kinds.includes(k))) return false;
  if (skip !== 'regions' && f.regions.length > 0 && !(pin.region && f.regions.includes(pin.region))) return false;
  if (skip !== 'periods' && f.periods.length > 0 && !isEvent) {
    const p = periodOf(pin.year);
    if (!p || !f.periods.includes(p)) return false;
  }
  if (skip !== 'genres' && f.genres.length > 0 && !isEvent) {
    if (!pin.genres || !pin.genres.some((g) => f.genres.includes(g))) return false;
  }
  return true;
}

export function filterPins<T extends FilterablePin>(pins: T[], f: PinFilters): T[] {
  const q = normalizeName(f.q);
  return pins.filter((pin) => matches(pin, f, q, null));
}

export function facetCounts<T extends FilterablePin>(pins: T[], f: PinFilters): FacetCounts {
  const q = normalizeName(f.q);
  const kinds = { MUSICIAN: 0, BAND: 0, VISUAL_ARTIST: 0, WRITER: 0, EVENT: 0 } as Record<PinKind, number>;
  const periods = { 'before-1900': 0, '1900-1945': 0, '1946-1990': 0, 'from-1991': 0 } as Record<PeriodKey, number>;
  const regions: Record<string, number> = {};
  const genres: Record<string, number> = {};

  for (const pin of pins) {
    if (matches(pin, f, q, 'kinds')) {
      for (const k of new Set(pinKinds(pin))) if (k in kinds) kinds[k]++;
    }
    if (matches(pin, f, q, 'regions') && pin.region) {
      regions[pin.region] = (regions[pin.region] ?? 0) + 1;
    }
    if (pin.type !== 'EVENT') {
      if (matches(pin, f, q, 'periods')) {
        const p = periodOf(pin.year);
        if (p) periods[p]++;
      }
      if (matches(pin, f, q, 'genres') && pin.genres) {
        for (const g of new Set(pin.genres)) genres[g] = (genres[g] ?? 0) + 1;
      }
    }
  }
  return { kinds, regions, periods, genres };
}

export function topGenres(counts: Record<string, number>, selected: string[], n = 12): string[] {
  const byCount = (a: string, b: string) => (counts[b] ?? 0) - (counts[a] ?? 0) || a.localeCompare(b, 'uk');
  const top = Object.keys(counts).sort(byCount).slice(0, n);
  const out = new Set(top);
  for (const g of selected) out.add(g);
  return [...out].sort(byCount);
}

export function activeCount(f: PinFilters): number {
  return (f.q.trim() ? 1 : 0) + f.kinds.length + f.regions.length + f.periods.length + f.genres.length;
}
