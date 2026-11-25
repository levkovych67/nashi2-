import { useEffect, useId, useState } from 'react'
import { usePlaceSearch } from '../../api/queries'
import type { PlaceChoice } from '../../core/submission/draft'
import { useCopy } from '../../core/i18n/copy'
import { describe, Field } from './Field'

const DEBOUNCE_MS = 250

interface Props {
  id: string
  field: string
  label: string
  hint: string
  value: PlaceChoice
  onChange: (value: PlaceChoice) => void
  error?: string
}

/** Combobox over GET /places/search: typing (debounced 250 ms) lists places; choosing fixes the `placeId`. */
export function PlaceAutocomplete({ id, field, label, hint, value, onChange, error }: Props) {
  const t = useCopy()
  const listId = useId()
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [query, setQuery] = useState('')
  const typing = value.id ? '' : value.text.trim()

  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(typing), typing ? DEBOUNCE_MS : 0)
    return () => window.clearTimeout(timer)
  }, [typing])

  const search = usePlaceSearch(query)
  const settled = query === typing && query.length >= 2
  const options = settled ? (search.data ?? []) : []
  const expanded = open && typing.length >= 2
  const label_ = (p: { name: string; regionName: string }) => `${p.name}, ${p.regionName}`

  function choose(i: number) {
    const place = options[i]
    if (!place) return
    onChange({ id: place.placeId, text: label_(place) })
    setOpen(false)
    setActive(-1)
  }

  return (
    <Field id={id} label={label} hint={hint} error={error}>
      <input
        {...describe(id, true, error)}
        data-field={field}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={expanded}
        aria-controls={expanded ? listId : undefined}
        aria-activedescendant={expanded && active >= 0 ? `${id}-opt-${active}` : undefined}
        autoComplete="off"
        value={value.text}
        onChange={(e) => {
          onChange({ id: '', text: e.target.value })
          setActive(-1)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') setOpen(false)
          if (!expanded || !options.length) return
          if (e.key === 'ArrowDown') {
            e.preventDefault()
            setActive((i) => (i + 1) % options.length)
          } else if (e.key === 'ArrowUp') {
            e.preventDefault()
            setActive((i) => (i <= 0 ? options.length - 1 : i - 1))
          } else if (e.key === 'Enter' && active >= 0) {
            e.preventDefault()
            choose(active)
          }
        }}
      />
      {expanded && (
        <div className="n-suggest__popup">
          {options.length > 0 && (
            <ul role="listbox" id={listId} aria-label={label} className="n-suggest__places">
              {options.map((p, i) => (
                <li
                  key={p.placeId}
                  id={`${id}-opt-${i}`}
                  role="option"
                  aria-selected={active === i}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => choose(i)}
                >
                  {label_(p)}
                </li>
              ))}
            </ul>
          )}
          <p role="status" className="n-suggest__status">
            {!settled || search.isFetching ? t('submit.place.searching') : options.length === 0 ? t('submit.place.empty') : ''}
          </p>
        </div>
      )}
    </Field>
  )
}
