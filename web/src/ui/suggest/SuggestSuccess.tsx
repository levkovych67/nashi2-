import { forwardRef } from 'react'
import { Link } from 'react-router-dom'
import { useCopy } from '../../core/i18n/copy'

export const SuggestSuccess = forwardRef<HTMLHeadingElement, { name: string; onAnother: () => void }>(function SuggestSuccess(
  { name, onAnother },
  ref,
) {
  const t = useCopy()
  return (
    <section className="n-suggest__success" data-testid="suggest-success">
      <h2 ref={ref} tabIndex={-1}>
        {t('submit.success')}
      </h2>
      <p>{t('submit.successText', { name })}</p>
      <div className="n-suggest__actions">
        <Link className="n-suggest__btn" to="/">
          {t('errors.backToMap')}
        </Link>
        <button type="button" className="n-suggest__btn n-suggest__btn--primary" onClick={onAnother}>
          {t('submit.another')}
        </button>
      </div>
    </section>
  )
})
