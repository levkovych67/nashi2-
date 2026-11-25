import type { ReactNode } from 'react'

/** Props that tie an input to its hint and error text (`<id>-hint`, `<id>-error`). */
export function describe(id: string, hint: boolean, error?: string) {
  return {
    id,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined,
  }
}

interface FieldProps {
  id: string
  label: string
  hint?: ReactNode
  error?: string
  children: ReactNode
}

export function Field({ id, label, hint, error, children }: FieldProps) {
  return (
    <div className="n-suggest__field">
      <label htmlFor={id}>{label}</label>
      {hint && <small id={`${id}-hint`}>{hint}</small>}
      {children}
      <FieldError id={id} error={error} />
    </div>
  )
}

export function FieldError({ id, error }: { id: string; error?: string }) {
  return error ? (
    <p className="n-suggest__error" id={`${id}-error`} role="alert">
      {error}
    </p>
  ) : null
}
