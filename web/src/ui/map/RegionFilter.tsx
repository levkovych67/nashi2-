import { useId, useMemo, useState } from 'react'
import { useCopy } from '../../core/i18n/copy'

export interface RegionOption { value: string; label: string; count?: number }

const norm = (s: string) => s.trim().toLocaleLowerCase('uk')

/**
 * filters.region = SHEET_LIST: a search field over a multi-select list of regions. The choice is a draft until
 * «Застосувати»; «Скинути» clears the filter at once. Mount it with a `key` of the applied value to restart the draft.
 */
export function RegionFilter({ options, selected, onApply, onReset }: {
  options: RegionOption[]
  selected: string[]
  onApply(regions: string[]): void
  onReset(): void
}) {
  const t = useCopy()
  const [draft, setDraft] = useState(selected)
  const [query, setQuery] = useState('')
  const listId = useId()
  const matches = useMemo(() => options.filter((o) => norm(o.label).includes(norm(query))), [options, query])
  const toggle = (value: string, on: boolean) =>
    setDraft((prev) => (on ? [...prev.filter((v) => v !== value), value] : prev.filter((v) => v !== value)))

  return (
    <div className="n-region-filter">
      <input
        type="search"
        className="n-region-filter__search"
        aria-label={t('filters.region')}
        placeholder={t('filters.region')}
        aria-controls={listId}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <div id={listId} className="n-region-filter__list" role="group" aria-label={t('filters.region')}>
        {matches.map((o) => (
          <label key={o.value} className="n-region-filter__option">
            <input type="checkbox" checked={draft.includes(o.value)} onChange={(e) => toggle(o.value, e.target.checked)} />
            <span>{o.label}</span>
            {o.count !== undefined && <small>{o.count}</small>}
          </label>
        ))}
        {matches.length === 0 && <p role="status">{t('list.empty')}</p>}
      </div>
      <div className="n-region-filter__actions">
        <button type="button" className="n-map-button n-map-button--plain" onClick={onReset}>
          {t('filters.reset')}
        </button>
        <button type="button" className="n-map-button n-map-button--primary" onClick={() => onApply(draft)}>
          {t('filters.apply')}
        </button>
      </div>
    </div>
  )
}
