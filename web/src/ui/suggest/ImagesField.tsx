import { useEffect, useState } from 'react'
import { useCopy, type CopyKey } from '../../core/i18n/copy'
import { compressImage } from '../../core/submission/compressImage'
import { IMAGE_TYPES } from '../../core/submission/schema'
import { describe, Field, FieldError } from './Field'

const MB = 1024 * 1024

interface Props {
  id: string
  label: string
  hint: string
  images: File[]
  onChange: (images: File[]) => void
  onBusy: (busy: boolean) => void
  max: number
  maxMb: number
  /** Message for the whole set. */
  error?: string
  /** Messages by photo index (client or server). */
  rowErrors: Record<number, string>
}

function Thumb({ file }: { file: File }) {
  const [src, setSrc] = useState('')
  useEffect(() => {
    const url = URL.createObjectURL(file)
    setSrc(url)
    return () => URL.revokeObjectURL(url)
  }, [file])
  return src ? <img src={src} alt="" width={64} height={64} /> : <span className="n-suggest__thumb-empty" />
}

/** 1..max photos: type and size are checked at pick time, then each is downscaled to 2048 px in the browser. */
export function ImagesField({ id, label, hint, images, onChange, onBusy, max, maxMb, error, rowErrors }: Props) {
  const t = useCopy()
  const [busy, setBusy] = useState(false)
  const [problems, setProblems] = useState<string[]>([])
  const size = (bytes: number) =>
    bytes >= MB
      ? t('submit.size.mb', { n: new Intl.NumberFormat('uk', { maximumFractionDigits: 1 }).format(bytes / MB) })
      : t('submit.size.kb', { n: Math.max(1, Math.round(bytes / 1024)) })

  async function add(files: File[]) {
    if (files.length === 0) return
    if (images.length + files.length > max) {
      setProblems([t('submit.photo.limit', { max })])
      return
    }
    setBusy(true)
    onBusy(true)
    const accepted: File[] = []
    const rejected: string[] = []
    for (const file of files) {
      const issue: CopyKey | null = !(IMAGE_TYPES as readonly string[]).includes(file.type)
        ? 'submit.error.imageType'
        : file.size > maxMb * MB
          ? 'submit.error.imageSize'
          : null
      if (issue) {
        rejected.push(`${file.name}: ${t(issue)}`)
        continue
      }
      try {
        accepted.push(await compressImage(file))
      } catch {
        rejected.push(`${file.name}: ${t('submit.error.imageUnreadable')}`)
      }
    }
    setProblems(rejected)
    if (accepted.length) onChange([...images, ...accepted])
    setBusy(false)
    onBusy(false)
  }

  return (
    <Field id={id} label={label} hint={hint} error={error}>
      <input
        {...describe(id, true, error)}
        data-field="images"
        type="file"
        accept={IMAGE_TYPES.join(',')}
        multiple
        disabled={busy || images.length >= max}
        onChange={(e) => {
          const files = Array.from(e.target.files ?? [])
          e.target.value = ''
          void add(files)
        }}
      />
      {busy && (
        <span role="status" className="n-suggest__status">
          {t('submit.photo.preparing')}
        </span>
      )}
      {problems.length > 0 && (
        <ul className="n-suggest__error-list" role="alert" data-testid="photo-problems">
          {problems.map((p) => (
            <li key={p} className="n-suggest__error">
              {p}
            </li>
          ))}
        </ul>
      )}
      {images.length > 0 && (
        <ol className="n-suggest__photos">
          {images.map((file, i) => (
            <li key={`${file.name}-${i}-${file.size}`}>
              <Thumb file={file} />
              <div className="n-suggest__photo-meta">
                <strong>
                  {i + 1}. {file.name}
                </strong>
                <small>{size(file.size)}</small>
                <FieldError id={`${id}-${i}`} error={rowErrors[i]} />
              </div>
              <button
                type="button"
                className="n-suggest__btn"
                disabled={busy}
                aria-label={t('submit.photo.remove', { name: file.name })}
                onClick={() => {
                  setProblems([])
                  onChange(images.filter((_, j) => j !== i))
                }}
              >
                ×
              </button>
            </li>
          ))}
        </ol>
      )}
    </Field>
  )
}
