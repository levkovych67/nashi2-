// URL <-> filter state. Unrelated params (e.g. `artist`) are left alone.
import { EMPTY_FILTERS, PERIOD_KEYS, PIN_KINDS, activeCount, type PeriodKey, type PinFilters, type PinKind } from './pinFilters.ts';

function list(sp: URLSearchParams, key: string): string[] {
  const raw = sp.get(key);
  if (!raw) return [];
  return [...new Set(raw.split(',').map((s) => s.trim()).filter(Boolean))];
}

export function parseFilters(sp: URLSearchParams): PinFilters {
  return {
    ...EMPTY_FILTERS,
    q: (sp.get('q') ?? '').trim(),
    kinds: list(sp, 'type').filter((v): v is PinKind => (PIN_KINDS as string[]).includes(v)),
    regions: list(sp, 'region'),
    periods: list(sp, 'period').filter((v): v is PeriodKey => (PERIOD_KEYS as string[]).includes(v)),
    genres: list(sp, 'genre'),
  };
}

export function writeFilters(sp: URLSearchParams, f: PinFilters): URLSearchParams {
  const out = new URLSearchParams(sp);
  const set = (key: string, value: string) => (value ? out.set(key, value) : out.delete(key));
  set('q', f.q.trim());
  set('type', f.kinds.join(','));
  set('region', f.regions.join(','));
  set('period', f.periods.join(','));
  set('genre', f.genres.join(','));
  return out;
}

export function activeFilterCount(f: PinFilters): number {
  return activeCount(f);
}
