import { useEffect, useRef, type ReactNode } from 'react'
import { useCopy } from '../../core/i18n/copy'
import './layout.css'

/**
 * Non-modal sheet anchored to the bottom of its positioned parent. Focus moves into it on open and returns to the
 * element that had it (the trigger) on close; Escape and the visible close button close it.
 */
export function BottomSheet({ label, title, testId, onClose, children }: {
  label: string
  /** Heading kept in the fixed header next to the close button */
  title?: string
  testId?: string
  onClose(): void
  children: ReactNode
}) {
  const t = useCopy()
  const sheet = useRef<HTMLElement>(null)
  useEffect(() => {
    const trigger = document.activeElement as HTMLElement | null
    sheet.current?.focus({ preventScroll: true })
    return () => {
      if (trigger?.isConnected && trigger !== document.body) trigger.focus({ preventScroll: true })
    }
  }, [])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <section ref={sheet} tabIndex={-1} className="n-sheet" role="dialog" aria-label={label} data-testid={testId}>
      <span className="n-sheet__handle" aria-hidden="true" />
      <div className="n-sheet__head">
        {title && <h2 className="n-sheet__title">{title}</h2>}
        <button type="button" className="n-sheet__close" onClick={onClose}>
          {t('common.close')}
        </button>
      </div>
      <div className="n-sheet__body">{children}</div>
    </section>
  )
}
