import { useState } from 'react'
import { type Region, type Pin, num } from './data'
import { FilterFields } from './FilterFields'
import futureFilters from './filters.extended.json'
import { parseRegions } from './mapGeometry'

export type Filters = {
  regions: string[]
  kind: string
  hasAudio: boolean
  from: string
  to: string
}
export const emptyFilters: Filters = {
  regions: [],
  kind: '',
  hasAudio: false,
  from: '',
  to: '',
}
export const hasExtra = (f: Filters) =>
  Boolean(f.kind || f.hasAudio || f.from || f.to)
type Dates = { birth?: string; activeFrom?: string }
const dates = import.meta.glob<Dates>(
  [
    '../../../contracts/fixtures/musicians/*.json',
    '!../../../contracts/fixtures/musicians/index.json',
  ],
  { eager: true, import: 'dates' },
)
const years = new Map(
  Object.entries(dates).map(([path, d]) => [
    path
      .split('/')
      .pop()!
      .replace(/\.json$/, ''),
    Number.parseInt(d.birth || d.activeFrom || '', 10),
  ]),
)
export function readFilters(query: string, regions: Region[]): Filters {
  const p = new URLSearchParams(query)
  const [from, to] = (p.get('period') || '').split('..')
  const year = (v?: string) => (/^\d{3,4}$/.test(v || '') ? v! : '')
  return {
    regions: parseRegions(query, regions),
    kind: ['PERSON', 'BAND'].includes(p.get('kind') || '')
      ? p.get('kind')!
      : '',
    hasAudio: p.get('hasAudio') === 'true',
    from: year(from),
    to: year(to),
  }
}
export function persistFilters(f: Filters) {
  const url = new URL(location.href)
  const pairs: Record<string, string> = {
    region: f.regions.join(','),
    kind: f.kind,
    hasAudio: f.hasAudio ? 'true' : '',
    period: f.from || f.to ? f.from + '..' + f.to : '',
  }
  for (const [key, value] of Object.entries(pairs)) {
    if (value) url.searchParams.set(key, value)
    else url.searchParams.delete(key)
  }
  history.replaceState(
    history.state,
    '',
    url.pathname + url.search.replace(/%2C/g, ',') + url.hash,
  )
}
export function extraMatch(pin: Pin, f: Filters) {
  if ((f.kind && f.kind !== pin.kind) || (f.hasAudio && !pin.hasAudio))
    return false
  if (!f.from && !f.to) return true
  const year = years.get(pin.slug)
  return (
    typeof year === 'number' &&
    Number.isFinite(year) &&
    (!f.from || year >= Number(f.from)) &&
    (!f.to || year <= Number(f.to))
  )
}
export function toggleRegion(codes: string[], id: string) {
  return codes.includes(id) ? codes.filter((r) => r !== id) : [...codes, id]
}
const shortName = (name: string) =>
  name.replace(' область', '').replace('Автономна Республіка ', '')

export function FilterPanel({
  value,
  regions,
  count,
  onApply,
  onClose,
}: {
  value: Filters
  regions: Region[]
  count: (f: Filters) => number
  onApply: (f: Filters) => void
  onClose: () => void
}) {
  const [draft, setDraft] = useState(value)
  const [query, setQuery] = useState('')
  const [extended, setExtended] = useState(hasExtra(value))
  const matches = regions.filter((r) =>
    r.name
      .toLocaleLowerCase('uk')
      .includes(query.trim().toLocaleLowerCase('uk')),
  )
  const fields = {
    region: draft.regions,
    kind: [draft.kind],
    hasAudio: draft.hasAudio ? ['true'] : [],
    period: [draft.from, draft.to],
  }
  function changeField(key: string, values: string[]) {
    if (key === 'region') setDraft({ ...draft, regions: values })
    if (key === 'kind') setDraft({ ...draft, kind: values[0] || '' })
    if (key === 'hasAudio')
      setDraft({ ...draft, hasAudio: values.includes('true') })
    if (key === 'period')
      setDraft({ ...draft, from: values[0] || '', to: values[1] || '' })
  }
  const invalidRange = Boolean(
    draft.from && draft.to && Number(draft.from) > Number(draft.to),
  )
  return (
    <section
      className="ms-sheet"
      aria-label="Фільтри регіонів"
      onKeyDown={(e) => {
        if (e.key === 'Escape') onClose()
      }}
    >
      <div className="ms-sheet-head">
        <h3>Фільтри</h3>
        <button onClick={onClose} aria-label="Закрити фільтри">
          ×
        </button>
      </div>
      <label className="ms-search">
        Пошук області
        <input
          autoFocus
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </label>
      <FilterFields
        definitions={[
          {
            key: 'region',
            label: 'Область',
            type: 'MULTI_SELECT',
            options: matches.map((r) => ({
              value: r.code,
              label: r.name,
              count: r.count,
            })),
          },
        ]}
        state={fields}
        change={changeField}
      />
      <label className="ms-extended">
        <input
          type="checkbox"
          checked={extended}
          onChange={(e) => {
            setExtended(e.target.checked)
            if (!e.target.checked)
              setDraft({ ...emptyFilters, regions: draft.regions })
          }}
        />
        Показати майбутні фільтри
      </label>
      {extended && (
        <div className="ms-extra">
          <FilterFields
            definitions={futureFilters}
            state={fields}
            change={changeField}
          />
          {(draft.from || draft.to) && (
            <small>
              {invalidRange
                ? 'Початковий рік має передувати кінцевому.'
                : 'Вибірка враховує профілі з відомим роком.'}
            </small>
          )}
        </div>
      )}
      <div className="ms-sheet-actions">
        <button onClick={() => setDraft(emptyFilters)}>Скинути</button>
        <button
          className="ms-primary"
          disabled={invalidRange}
          onClick={() => onApply(draft)}
        >
          Показати {num(count(draft))}
        </button>
      </div>
    </section>
  )
}
export function FilterControls({
  value,
  regions,
  variant,
  onApply,
  open,
}: {
  value: Filters
  regions: Region[]
  variant: string
  onApply: (f: Filters) => void
  open: () => void
}) {
  const selected = value.regions
  if (variant === 'CHIPS_SCROLL') {
    const top = [...regions].sort((a, b) => b.count - a.count).slice(0, 8)
    const chips = [
      ...regions.filter((r) => selected.includes(r.code)),
      ...top.filter((r) => !selected.includes(r.code)),
    ]
    return (
      <div className="ms-chips" aria-label="Області України">
        {chips.map((r) => (
          <button
            key={r.code}
            onClick={() =>
              onApply({ ...value, regions: toggleRegion(selected, r.code) })
            }
            aria-label={r.name}
            aria-pressed={selected.includes(r.code)}
          >
            {shortName(r.name)}
          </button>
        ))}
        <button onClick={open}>Усі області</button>
      </div>
    )
  }
  return (
    <div className="ms-filters-top">
      <button onClick={open}>
        {variant === 'MAP_TAP_REGION' ? 'Список областей' : 'Фільтри'}
        {selected.length > 0 && ' (' + selected.length + ')'}
      </button>
      {variant === 'MAP_TAP_REGION' && (
        <small>Торкніться областей на карті</small>
      )}
    </div>
  )
}
