import { useCopy } from '../../core/i18n/copy'

/** "Крок 2 із 6" and one bar per step; filled up to the current step. */
export function StepIndicator({ step, total }: { step: number; total: number }) {
  const t = useCopy()
  return (
    <div className="n-suggest__steps" data-testid="suggest-steps">
      <p>{t('submit.step.progress', { n: step + 1, total })}</p>
      <div aria-hidden="true">
        {Array.from({ length: total }, (_, i) => (
          <span key={i} data-filled={i <= step} />
        ))}
      </div>
    </div>
  )
}
