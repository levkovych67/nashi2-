import { num } from './data'

export interface FilterDefinition {
  key: string
  label: string
  type: string
  options?: { value: string; label: string; count?: number }[]
  min?: number
  max?: number
}

export function FilterFields({
  definitions,
  state,
  change,
}: {
  definitions: FilterDefinition[]
  state: Record<string, string[]>
  change: (key: string, values: string[]) => void
}) {
  return definitions.map((def) => {
    const values = state[def.key] || []
    const set = (v: string[]) => change(def.key, v)
    switch (def.type) {
      case 'MULTI_SELECT':
        return (
          <div
            key={def.key}
            className="ms-checks"
            role="group"
            aria-label={def.label}
          >
            {def.options?.map((o) => (
              <label key={o.value}>
                <input
                  type="checkbox"
                  checked={values.includes(o.value)}
                  onChange={(e) =>
                    set(
                      e.target.checked
                        ? [...values, o.value]
                        : values.filter((v) => v !== o.value),
                    )
                  }
                />
                <span>{o.label}</span>
                {o.count !== undefined && <small>{num(o.count)}</small>}
              </label>
            ))}
            {!def.options?.length && (
              <p role="status">За цією назвою областей не знайдено.</p>
            )}
          </div>
        )
      case 'SINGLE_SELECT':
        return (
          <label key={def.key}>
            {def.label}
            <select
              value={values[0] || ''}
              onChange={(e) => set(e.target.value ? [e.target.value] : [])}
            >
              <option value="">Усі</option>
              {def.options?.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        )
      case 'BOOLEAN':
        return (
          <label key={def.key}>
            <input
              type="checkbox"
              checked={values.includes('true')}
              onChange={(e) => set(e.target.checked ? ['true'] : [])}
            />
            {def.label}
          </label>
        )
      case 'RANGE':
        return (
          <fieldset key={def.key}>
            <legend>{def.label}</legend>
            {['Від', 'До'].map((label, i) => (
              <label key={label}>
                {label}
                <input
                  type="number"
                  min={def.min}
                  max={def.max}
                  value={values[i] || ''}
                  onChange={(e) => {
                    const next = [values[0] || '', values[1] || '']
                    next[i] = e.target.value
                    set(next)
                  }}
                />
              </label>
            ))}
          </fieldset>
        )
      default:
        console.warn('Невідомий тип фільтра', def.type)
        return null
    }
  })
}
