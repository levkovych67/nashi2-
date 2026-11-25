import { useId } from 'react'
import { useCopy } from '../../core/i18n/copy'

/** Real upload progress (0..1). */
export function SubmitProgress({ ratio }: { ratio: number }) {
  const t = useCopy()
  const id = useId()
  const percent = Math.round(ratio * 100)
  return (
    <div className="n-suggest__progress" role="status" data-testid="submit-progress">
      <label htmlFor={id}>{t('submit.progress', { percent })}</label>
      <progress id={id} max={100} value={percent} />
    </div>
  )
}
