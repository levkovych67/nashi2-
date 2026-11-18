import { useEffect, useId, useRef, useState } from 'react'
import { t } from '../proto/copy/t'
import type { Place, Region } from './data'
import {
  downscaleImage,
  fileIssue,
  imageAccept,
  searchPlaces,
} from './submission'

export interface PlaceChoice {
  text: string
  id: string
}
interface PlaceFieldProps {
  id: string
  field: 'originPlaceId' | 'birthPlaceId'
  label: string
  choice: PlaceChoice
  change: (choice: PlaceChoice) => void
  places: Place[]
  regions: Region[]
  error?: string
}
export function PlaceField({
  id,
  field,
  label,
  choice,
  change,
  places,
  regions,
  error,
}: PlaceFieldProps) {
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const matches = open && !choice.id ? searchPlaces(choice.text, places) : []
  const expanded = open && !choice.id && choice.text.trim().length >= 2
  const listId = id + '-list'
  function choose(place: Place) {
    change({ text: place.name, id: place.placeId })
    setOpen(false)
    setActive(-1)
  }
  return (
    <div className="sub-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        data-field={field}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={expanded}
        aria-controls={expanded ? listId : undefined}
        aria-activedescendant={
          expanded && matches[active] ? id + '-opt-' + active : undefined
        }
        aria-invalid={!!error}
        aria-describedby={[id + '-hint', error && id + '-error']
          .filter(Boolean)
          .join(' ')}
        autoComplete="off"
        value={choice.text}
        onChange={(event) => {
          change({ text: event.target.value, id: '' })
          setActive(-1)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            setOpen(false)
            return
          }
          if (!expanded || !matches.length) return
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            setActive((index) =>
              event.key === 'ArrowDown'
                ? (index + 1) % matches.length
                : index <= 0
                  ? matches.length - 1
                  : index - 1,
            )
          }
          if (event.key === 'Enter') {
            event.preventDefault()
            choose(matches[active < 0 ? 0 : active])
          }
        }}
      />
      <small id={id + '-hint'}>
        {choice.id
          ? regions.find(
              (region) =>
                region.code ===
                places.find((place) => place.placeId === choice.id)?.region,
            )?.name
          : t('submit.field.' + field + '.hint')}
      </small>
      {expanded && (
        <>
          <ul
            role="listbox"
            aria-label={label}
            id={listId}
            className="sub-places"
          >
            {matches.map((place, index) => (
              <li
                role="option"
                id={id + '-opt-' + index}
                key={place.placeId}
                aria-selected={active === index}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => choose(place)}
              >
                {place.name}
                <small>
                  {regions.find((region) => region.code === place.region)?.name}
                </small>
              </li>
            ))}
          </ul>
          {!matches.length && (
            <span role="status">Місць із такою назвою не знайдено.</span>
          )}
        </>
      )}
      {error && (
        <span id={id + '-error'} className="sub-error">
          {error}
        </span>
      )}
    </div>
  )
}

export interface Photo {
  id: string
  name: string
  bytes: number
  file: File
}
const fileSize = (bytes: number) =>
  bytes >= 1024 ** 2
    ? new Intl.NumberFormat('uk', { maximumFractionDigits: 2 }).format(
        bytes / 1024 ** 2,
      ) + ' МБ'
    : Math.max(1, Math.round(bytes / 1024)) + ' КБ'

function Thumbnail({ file }: { file: File }) {
  const [src, setSrc] = useState('')
  useEffect(() => {
    const url = URL.createObjectURL(file)
    setSrc(url)
    return () => URL.revokeObjectURL(url)
  }, [file])
  return src ? <img src={src} alt="" width="60" height="60" /> : null
}

export function ImagePicker({
  id,
  photos,
  change,
  error,
  busy,
  onBusy,
}: {
  id: string
  photos: Photo[]
  change: (photos: Photo[]) => void
  error?: string
  busy: boolean
  onBusy: (busy: boolean) => void
}) {
  const [feedback, setFeedback] = useState('')
  const [dragged, setDragged] = useState('')
  const next = useRef(0)
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  const prefix = useId()
  async function add(files: File[]) {
    if (!files.length || busy) return
    if (files.length + photos.length > 5) {
      setFeedback('Можна додати щонайбільше 5 фото. Виберіть менше файлів.')
      return
    }
    setFeedback('')
    onBusy(true)
    const valid: Photo[] = []
    const errors: string[] = []
    for (const input of files) {
      const issue = fileIssue(input)
      if (issue) {
        errors.push(input.name + ' · ' + issue)
        continue
      }
      try {
        const blob = await downscaleImage(input)
        if (!alive.current) return
        valid.push({
          id: prefix + ++next.current,
          name: input.name,
          bytes: input.size,
          file: new File([blob], input.name.replace(/\.[^.]*$/, '') + '.jpg', {
            type: 'image/jpeg',
          }),
        })
      } catch {
        errors.push(
          input.name + ' · Не вдалося відкрити фото. Виберіть інший файл.',
        )
      }
    }
    if (alive.current) {
      change([...photos, ...valid])
      setFeedback(errors.join(' '))
      onBusy(false)
    }
  }
  function move(from: number, to: number) {
    if (busy || from < 0 || to < 0 || from === to || to >= photos.length) return
    const reordered = [...photos]
    reordered.splice(to, 0, reordered.splice(from, 1)[0])
    change(reordered)
  }
  return (
    <div className="sub-field">
      <label htmlFor={id}>{t('submit.field.images.label')}</label>
      <small id={id + '-hint'}>{t('submit.field.images.hint')}</small>
      <input
        data-field="images"
        id={id}
        type="file"
        accept={imageAccept}
        multiple
        disabled={busy}
        aria-invalid={!!error || !!feedback}
        aria-describedby={[
          id + '-hint',
          error && id + '-error',
          feedback && id + '-feedback',
        ]
          .filter(Boolean)
          .join(' ')}
        onChange={(event) => {
          const files = Array.from(event.target.files ?? [])
          event.target.value = ''
          void add(files)
        }}
      />
      {busy && <span role="status">Готуємо фото…</span>}
      {error && (
        <span className="sub-error" id={id + '-error'}>
          {error}
        </span>
      )}
      {feedback && (
        <span className="sub-error" id={id + '-feedback'} role="alert">
          {feedback}
        </span>
      )}
      {photos.length > 0 && (
        <ol className="sub-photos">
          {photos.map((photo, index) => (
            <li
              key={photo.id}
              draggable={!busy}
              onDragStart={(event) => {
                setDragged(photo.id)
                event.dataTransfer.setData('text/plain', photo.id)
              }}
              onDragEnd={() => setDragged('')}
              onDragOver={(event) => {
                if (dragged) event.preventDefault()
              }}
              onDrop={(event) => {
                event.preventDefault()
                move(
                  photos.findIndex((item) => item.id === dragged),
                  index,
                )
                setDragged('')
              }}
            >
              <Thumbnail file={photo.file} />
              <div>
                <strong>
                  {index + 1}. {photo.name}
                </strong>
                <small>
                  Було {fileSize(photo.bytes)}, підготовлено{' '}
                  {fileSize(photo.file.size)}
                </small>
              </div>
              <div className="sub-photo-actions">
                {photos.length > 1 && (
                  <>
                    <button
                      type="button"
                      disabled={!index || busy}
                      aria-label={'Перемістити раніше ' + photo.name}
                      onClick={() => move(index, index - 1)}
                    >
                      Раніше
                    </button>
                    <button
                      type="button"
                      disabled={index === photos.length - 1 || busy}
                      aria-label={'Перемістити пізніше ' + photo.name}
                      onClick={() => move(index, index + 1)}
                    >
                      Пізніше
                    </button>
                  </>
                )}
                <button
                  type="button"
                  disabled={busy}
                  aria-label={'Видалити фото ' + photo.name}
                  onClick={() => {
                    change(photos.filter((item) => item !== photo))
                    setFeedback('')
                  }}
                >
                  Видалити
                </button>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
