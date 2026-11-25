import { useEffect, type ReactNode } from 'react'
import './layout.css'

/** Non-modal sheet anchored to the bottom of its positioned parent; Escape closes it. */
export function BottomSheet({ label, testId, onClose, children }: {
  label: string
  testId?: string
  onClose(): void
  children: ReactNode
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <section className="n-sheet" role="dialog" aria-label={label} data-testid={testId}>
      <span className="n-sheet__handle" aria-hidden="true" />
      {children}
    </section>
  )
}
