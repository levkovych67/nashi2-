import { useCallback, useEffect, useRef, useState } from 'react'
import './layout.css'

const TOAST_MS = 6000

/** Message state of a toast that clears itself; the text comes from the caller (a copy key). */
export function useToast() {
  const [message, setMessage] = useState<string | null>(null)
  const timer = useRef<number | undefined>(undefined)
  const show = useCallback((text: string) => {
    window.clearTimeout(timer.current)
    setMessage(text)
    timer.current = window.setTimeout(() => setMessage(null), TOAST_MS)
  }, [])
  useEffect(() => () => window.clearTimeout(timer.current), [])
  return { message, show }
}

/** Polite live region, always mounted so screen readers announce the message when it appears. */
export function Toast({ message }: { message: string | null }) {
  return (
    <div className="n-toast-region" role="status" aria-live="polite">
      {message && (
        <p className="n-toast" data-testid="toast">
          {message}
        </p>
      )}
    </div>
  )
}
