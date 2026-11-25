import { useCopy } from '../../core/i18n/copy'
import { detectPlatform } from '../../core/submission/platforms'
import { describe, FieldError } from './Field'

const MAX_LINKS = 10

interface Props {
  id: string
  label: string
  hint: string
  links: string[]
  onChange: (links: string[]) => void
  /** Message for the whole list (e.g. "add a music link"). */
  error?: string
  /** Messages by row index. */
  rowErrors: Record<number, string>
}

/** 1..10 link rows with a platform badge, add and remove. The badge follows the typed address. */
export function LinksField({ id, label, hint, links, onChange, error, rowErrors }: Props) {
  const t = useCopy()
  return (
    <fieldset className="n-suggest__field n-suggest__group">
      <legend>{label}</legend>
      <small id={`${id}-hint`}>{hint}</small>
      {links.map((link, i) => {
        const rowId = `${id}-${i}`
        const platform = detectPlatform(link.trim())
        const rowError = rowErrors[i] ?? (i === 0 ? error : undefined)
        return (
          <div className="n-suggest__link-row" key={i}>
            <div className="n-suggest__inline">
              <input
                {...describe(rowId, i === 0, rowError)}
                data-field={`links[${i}]`}
                type="url"
                inputMode="url"
                autoComplete="off"
                placeholder="https://"
                aria-label={t('submit.link.n', { n: i + 1 })}
                value={link}
                onChange={(e) => onChange(links.map((v, j) => (j === i ? e.target.value : v)))}
              />
              {links.length > 1 && (
                <button
                  type="button"
                  className="n-suggest__btn"
                  aria-label={t('submit.link.remove', { n: i + 1 })}
                  onClick={() => onChange(links.filter((_, j) => j !== i))}
                >
                  ×
                </button>
              )}
            </div>
            {platform !== 'OTHER' && !rowErrors[i] && (
              <small className="n-suggest__badge" data-testid="platform-badge" data-platform={platform}>
                {t(`musician.platform.${platform}`)}
              </small>
            )}
            <FieldError id={rowId} error={rowError} />
          </div>
        )
      })}
      {links.length < MAX_LINKS && (
        <button type="button" className="n-suggest__btn n-suggest__add" onClick={() => onChange([...links, ''])}>
          {t('submit.link.add')}
        </button>
      )}
    </fieldset>
  )
}
