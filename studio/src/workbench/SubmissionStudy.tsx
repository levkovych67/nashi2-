import {
  useEffect,
  useId,
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
  type RefObject,
} from 'react'
import { t } from '../proto/copy/t'
import type { AtlasData } from './data'
import {
  ImagePicker,
  PlaceField,
  type Photo,
  type PlaceChoice,
} from './SubmissionFields'
import {
  detectPlatform,
  fixtureSubmit,
  messages,
  platformNames,
  scenarioNames,
  validateDraft,
  type FieldErrors,
  type ErrorScenario,
  type FieldName,
  type SubmissionDraft,
  type SubmitScenario,
} from './submission'
import './SubmissionStudy.css'

const sections = [
  { title: 'Хто', fields: ['name', 'kind'] },
  { title: 'Звідки', fields: ['originPlaceId', 'birthPlaceId'] },
  { title: 'Про музику', fields: ['summary', 'genres'] },
  {
    title: 'Посилання',
    fields: ['links', 'youtubeChannelUrl', 'wikipediaUrl'],
  },
  { title: 'Фото', fields: ['images'] },
  { title: 'Перевірка і згода', fields: ['contact', 'consent'] },
] satisfies { title: string; fields: FieldName[] }[]
const emptyPlace = { id: '', text: '' }
const blank: SubmissionDraft = {
  name: '',
  kind: 'PERSON',
  originPlaceId: '',
  birthPlaceId: null,
  summary: '',
  genres: [],
  links: [''],
  youtubeChannelUrl: null,
  wikipediaUrl: null,
  contact: '',
  consent: false,
  faxNumber: '',
  images: [],
}
interface FieldProps {
  id: string
  label: string
  error?: string
  hint?: ReactNode
  children: ReactNode
}
function Field({ id, label, error, hint, children }: FieldProps) {
  return (
    <div className="sub-field">
      <label htmlFor={id}>{label}</label>
      {hint && <small id={id + '-hint'}>{hint}</small>}
      {children}
      {error && (
        <span className="sub-error" id={id + '-error'}>
          {error}
        </span>
      )}
    </div>
  )
}

export type SubmissionEvent =
  | 'edit'
  | 'invalid'
  | 'fix'
  | 'origin'
  | 'photo'
  | 'review'
  | 'submitted'
export type ReviewPhase = 2 | 3 | 4 | 5
export function SubmissionStudy({
  variant,
  data,
  onProgress,
  onReviewRef,
}: {
  variant: string
  data: AtlasData
  onProgress?: (event: SubmissionEvent) => void
  onReviewRef?: RefObject<((phase?: ReviewPhase) => void) | null>
}) {
  const correcting = useRef(false)
  const prefix = useId()
  const id = (key: string) => prefix + '-' + key
  const wizard = variant === 'WIZARD'
  const [step, setStep] = useState(0)
  const [values, setValues] = useState<SubmissionDraft>(blank)
  const [origin, setOrigin] = useState<PlaceChoice>(emptyPlace)
  const [birth, setBirth] = useState<PlaceChoice>(emptyPlace)
  const [photos, setPhotos] = useState<Photo[]>([])
  const [processing, setProcessing] = useState(false)
  const [genre, setGenre] = useState('')
  const [touched, setTouched] = useState<Set<FieldName>>(new Set())
  const [slowConnection, setSlowConnection] = useState(false)
  const [scenario, setScenario] = useState<SubmitScenario>('success')
  const [serverError, setServerError] = useState<ErrorScenario>()
  const [serverFields, setServerFields] = useState<FieldErrors>({})
  const [retryWait, setRetryWait] = useState(0)
  const [progress, setProgress] = useState<number | null>(null)
  const [success, setSuccess] = useState(false)
  const submission = useRef<AbortController | null>(null)
  const pendingFocus = useRef<string | null>(null)
  const placeIds = useMemo(
    () => new Set(data.places.map((place) => place.placeId)),
    [data.places],
  )
  const candidateGenre = genre.trim()
  const genres = [
    ...new Set([...values.genres, ...(candidateGenre ? [candidateGenre] : [])]),
  ]
  const draft: SubmissionDraft = {
    ...values,
    genres,
    originPlaceId: origin.id,
    birthPlaceId: birth.id || null,
    links: values.links.map((link) => link.trim()).filter(Boolean),
    images: photos.map((photo) => photo.file),
  }
  const errors = validateDraft(draft, placeIds)
  if (birth.text.trim() && !birth.id)
    errors.birthPlaceId = 'Оберіть місце зі списку'
  function change<K extends FieldName>(field: K, value: SubmissionDraft[K]) {
    setValues((current) => ({ ...current, [field]: value }))
  }
  function mark(fields: FieldName[]) {
    setTouched((previous) => new Set([...previous, ...fields]))
  }
  function errorOf(field: FieldName) {
    return (
      serverFields[field] ?? (touched.has(field) ? errors[field] : undefined)
    )
  }
  function inputProps(field: FieldName, hasHint = false) {
    const key = id(field)
    return {
      id: key,
      'data-field': field,
      'aria-invalid': !!errorOf(field),
      'aria-describedby':
        [hasHint && key + '-hint', errorOf(field) && key + '-error']
          .filter(Boolean)
          .join(' ') || undefined,
    }
  }
  function focus(key: string) {
    document.getElementById(id(key))?.focus()
  }
  function go(next: number, target = 'section-' + next) {
    pendingFocus.current = target
    if (step === next) {
      focus(target)
      pendingFocus.current = null
    } else setStep(next)
  }
  useImperativeHandle(onReviewRef, () => (phase: ReviewPhase = 5) => {
    if (submission.current || processing) return
    const target = { 2: 0, 3: 1, 4: 4, 5: 5 }[phase]
    if (success) {
      pendingFocus.current = 'section-' + target
      setStep(target)
      setSuccess(false)
    } else go(target)
  })
  useLayoutEffect(() => {
    const target = pendingFocus.current
    if (target) {
      document.getElementById(prefix + '-' + target)?.focus()
      pendingFocus.current = null
    }
  }, [prefix, step, success])
  useEffect(() => () => submission.current?.abort(), [])
  useEffect(() => {
    if (!retryWait) return
    const timer = window.setTimeout(
      () => setRetryWait((current) => Math.max(0, current - 1)),
      1000,
    )
    return () => window.clearTimeout(timer)
  }, [retryWait])
  function storeGenre() {
    if (
      candidateGenre.length >= 2 &&
      candidateGenre.length <= 40 &&
      genres.length <= 5
    ) {
      change('genres', genres)
      setGenre('')
    }
  }
  function reset() {
    correcting.current = false
    setValues(blank)
    setOrigin(emptyPlace)
    setBirth(emptyPlace)
    setPhotos([])
    setGenre('')
    setTouched(new Set())
    setServerError(undefined)
    setStep(0)
    setSuccess(false)
    pendingFocus.current = 'name'
  }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (processing || submission.current || retryWait) return
    const advance = wizard && step < sections.length - 1
    const fields = advance
      ? sections[step].fields
      : sections.flatMap((section) => section.fields)
    mark(fields)
    const invalid = fields.find((field) => errors[field])
    if (invalid) {
      correcting.current = true
      onProgress?.('invalid')
      const location = sections.findIndex((section) =>
        (section.fields as FieldName[]).includes(invalid),
      )
      if (wizard && location !== step) go(location, invalid)
      else focus(invalid)
      return
    }
    if (advance) {
      storeGenre()
      go(step + 1)
      if (step === 0) onProgress?.('origin')
      if (step === 3) onProgress?.('photo')
      if (step === 4) onProgress?.('review')
      return
    }
    onProgress?.('review')
    const controller = new AbortController()
    submission.current = controller
    setServerError(undefined)
    setServerFields({})
    setProgress(0)
    try {
      const response = await fixtureSubmit(
        draft,
        scenario,
        setProgress,
        controller.signal,
        slowConnection,
      )
      if (!('code' in response)) {
        pendingFocus.current = 'success'
        setSuccess(true)
        onProgress?.('submitted')
      } else {
        setServerError(response.code)
        setRetryWait(response.retryAfter ?? 0)
        const fields: FieldErrors = {}
        response.errors?.forEach(({ field, message }) => {
          fields[field.split('[')[0] as FieldName] = message
        })
        setServerFields(fields)
        if (!wizard && response.errors?.[0])
          focus(response.errors[0].field.split('[')[0])
        correcting.current = true
        onProgress?.('invalid')
        setScenario('success')
      }
    } finally {
      submission.current = null
      if (!controller.signal.aborted) setProgress(null)
    }
  }
  function show(index: number, body: ReactNode) {
    if (wizard && index !== step) return null
    return (
      <section className="sub-section" aria-labelledby={id('section-' + index)}>
        <h3 tabIndex={-1} id={id('section-' + index)}>
          {sections[index].title}
        </h3>
        {body}
      </section>
    )
  }
  if (success)
    return (
      <section className="submission-study sub-success">
        <h2 id={id('success')} tabIndex={-1}>
          {t('submit.success')}
        </h2>
        <p>
          Демонстраційний сценарій для «{draft.name.trim()}» завершено. Анкета й
          фото залишилися на вашому пристрої.
        </p>
        <button className="sub-primary" onClick={reset}>
          Запропонувати ще одного
        </button>
      </section>
    )

  return (
    <div className="submission-study" data-form-variant={variant}>
      <header>
        <h2>{t('submit.title')}</h2>
        <p>{t('submit.intro')} Це демонстраційна форма з локальними фото.</p>
      </header>
      <details className="sub-scenarios">
        <summary>Сценарій надсилання</summary>
        <label htmlFor={id('scenario')}>Наступна відповідь демо</label>
        <select
          id={id('scenario')}
          value={scenario}
          disabled={progress !== null}
          onChange={(event) =>
            setScenario(event.target.value as SubmitScenario)
          }
        >
          {Object.entries(scenarioNames).map(([key, name]) => (
            <option key={key} value={key}>
              {name}
            </option>
          ))}
        </select>
        <label htmlFor={id('speed')}>З’єднання під час демо</label>
        <select
          id={id('speed')}
          value={slowConnection ? 'slow' : 'normal'}
          disabled={progress !== null}
          onChange={(event) => setSlowConnection(event.target.value === 'slow')}
        >
          <option value="normal">Звичайне</option>
          <option value="slow">Повільне</option>
        </select>
      </details>
      {wizard && (
        <div className="sub-steps" aria-label={'Крок ' + (step + 1) + ' із 6'}>
          <p>Крок {step + 1} із 6</p>
          <div aria-hidden="true">
            {sections.map((section, index) => (
              <span key={section.title} data-filled={index <= step} />
            ))}
          </div>
        </div>
      )}
      <form
        noValidate
        onChange={(event) => {
          setServerFields({})
          const target = event.target
          if (
            target instanceof HTMLInputElement &&
            target.dataset.field === 'consent' &&
            target.checked
          )
            onProgress?.('review')
          else onProgress?.(correcting.current ? 'fix' : 'edit')
          correcting.current = false
        }}
        onSubmit={(event) => {
          void submit(event).catch((error) => {
            if (error?.name !== 'AbortError') setServerError('INTERNAL')
          })
        }}
        onBlur={(event) => {
          const field = (event.target as HTMLElement).dataset.field as
            | FieldName
            | undefined
          if (field) mark([field])
        }}
      >
        <fieldset disabled={progress !== null} className="sub-main-fields">
          <legend className="sub-visually-hidden">Анкета виконавця</legend>
          {show(
            0,
            <>
              <Field
                id={id('name')}
                label={t('submit.field.name.label')}
                error={errorOf('name')}
              >
                <input
                  {...inputProps('name')}
                  autoComplete="off"
                  value={values.name}
                  onChange={(event) => change('name', event.target.value)}
                />
              </Field>
              <fieldset className="sub-kind">
                <legend>{t('submit.field.kind.label')}</legend>
                {(['PERSON', 'BAND'] as const).map((kind, index) => (
                  <label key={kind} data-selected={values.kind === kind}>
                    <input
                      type="radio"
                      id={index === 0 ? id('kind') : undefined}
                      name={id('kind')}
                      value={kind}
                      checked={values.kind === kind}
                      onChange={() => {
                        change('kind', kind)
                        if (kind === 'BAND') setBirth(emptyPlace)
                      }}
                    />
                    {kind === 'PERSON' ? 'Людина' : 'Гурт'}
                  </label>
                ))}
              </fieldset>
            </>,
          )}
          {show(
            1,
            <>
              <PlaceField
                id={id('originPlaceId')}
                field="originPlaceId"
                label={t('submit.field.originPlaceId.label')}
                choice={origin}
                change={(choice) => {
                  setOrigin(choice)
                  if (choice.id) onProgress?.('origin')
                }}
                places={data.places}
                regions={data.regions}
                error={errorOf('originPlaceId')}
              />
              {values.kind === 'PERSON' && (
                <PlaceField
                  id={id('birthPlaceId')}
                  field="birthPlaceId"
                  label={t('submit.field.birthPlaceId.label')}
                  choice={birth}
                  change={setBirth}
                  places={data.places}
                  regions={data.regions}
                  error={errorOf('birthPlaceId')}
                />
              )}
            </>,
          )}
          {show(
            2,
            <>
              <Field
                id={id('summary')}
                label={t('submit.field.summary.label')}
                hint={
                  t('submit.field.summary.hint') +
                  ' Введено ' +
                  values.summary.trim().length +
                  '.'
                }
                error={errorOf('summary')}
              >
                <textarea
                  {...inputProps('summary', true)}
                  rows={4}
                  value={values.summary}
                  onChange={(event) => change('summary', event.target.value)}
                />
              </Field>
              <Field
                id={id('genres')}
                label={t('submit.field.genres.label')}
                hint={t('submit.field.genres.hint')}
                error={errorOf('genres')}
              >
                {values.genres.length > 0 && (
                  <ul className="sub-genres">
                    {values.genres.map((tag) => (
                      <li key={tag}>
                        {tag}
                        <button
                          type="button"
                          aria-label={'Видалити жанр ' + tag}
                          onClick={() =>
                            change(
                              'genres',
                              values.genres.filter((value) => value !== tag),
                            )
                          }
                        >
                          ×
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                <div className="sub-inline">
                  <input
                    {...inputProps('genres', true)}
                    value={genre}
                    disabled={values.genres.length >= 5}
                    onChange={(event) => setGenre(event.target.value)}
                    onBlur={storeGenre}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ',') {
                        event.preventDefault()
                        mark(['genres'])
                        storeGenre()
                      }
                    }}
                  />
                  <button
                    type="button"
                    disabled={!candidateGenre}
                    onClick={() => {
                      mark(['genres'])
                      storeGenre()
                    }}
                  >
                    Додати жанр
                  </button>
                </div>
              </Field>
            </>,
          )}
          {show(
            3,
            <>
              <Field
                id={id('links')}
                label={t('submit.field.links.label')}
                hint={t('submit.field.links.hint')}
                error={errorOf('links')}
              >
                {values.links.map((link, index) => {
                  const platform = detectPlatform(link.trim())
                  const inputId = index ? id('link-' + index) : id('links')
                  return (
                    <div className="sub-link-row" key={index}>
                      <div className="sub-inline">
                        <input
                          {...inputProps('links', true)}
                          id={inputId}
                          type="url"
                          value={link}
                          placeholder="https://"
                          aria-label={
                            index ? 'Посилання ' + (index + 1) : undefined
                          }
                          onChange={(event) =>
                            change(
                              'links',
                              values.links.map((value, i) =>
                                i === index ? event.target.value : value,
                              ),
                            )
                          }
                        />
                        {values.links.length > 1 && (
                          <button
                            type="button"
                            aria-label={'Видалити посилання ' + (index + 1)}
                            onClick={() =>
                              change(
                                'links',
                                values.links.filter((_, i) => i !== index),
                              )
                            }
                          >
                            Видалити
                          </button>
                        )}
                      </div>
                      {platform && (
                        <small>
                          <span aria-hidden="true">
                            {platform.group === 'MUSIC'
                              ? '♫'
                              : platform.group === 'VIDEO'
                                ? '▷'
                                : '◎'}{' '}
                          </span>
                          {platformNames[platform.platform]}
                        </small>
                      )}
                    </div>
                  )
                })}
                {values.links.length < 10 && (
                  <button
                    type="button"
                    onClick={() => change('links', [...values.links, ''])}
                  >
                    Додати посилання
                  </button>
                )}
              </Field>
              <Field
                id={id('youtubeChannelUrl')}
                label={t('submit.field.youtubeChannelUrl.label')}
                error={errorOf('youtubeChannelUrl')}
              >
                <input
                  {...inputProps('youtubeChannelUrl')}
                  type="url"
                  placeholder="https://www.youtube.com/@…"
                  value={values.youtubeChannelUrl ?? ''}
                  onChange={(event) =>
                    change(
                      'youtubeChannelUrl',
                      event.target.value.trim() || null,
                    )
                  }
                />
              </Field>
              <Field
                id={id('wikipediaUrl')}
                label={t('submit.field.wikipediaUrl.label')}
                error={errorOf('wikipediaUrl')}
              >
                <input
                  {...inputProps('wikipediaUrl')}
                  type="url"
                  placeholder="https://uk.wikipedia.org/wiki/…"
                  value={values.wikipediaUrl ?? ''}
                  onChange={(event) =>
                    change('wikipediaUrl', event.target.value.trim() || null)
                  }
                />
              </Field>
            </>,
          )}
          {show(
            4,
            <ImagePicker
              id={id('images')}
              photos={photos}
              change={(nextPhotos) => {
                setPhotos(nextPhotos)
                if (nextPhotos.length > photos.length) onProgress?.('photo')
              }}
              busy={processing}
              onBusy={setProcessing}
              error={errorOf('images')}
            />,
          )}
          {show(
            5,
            <>
              {wizard && (
                <dl className="sub-review">
                  <dt>Виконавець</dt>
                  <dd>
                    {values.name} · {values.kind === 'BAND' ? 'Гурт' : 'Людина'}
                  </dd>
                  <dt>Походження</dt>
                  <dd>{origin.text}</dd>
                  {birth.id && (
                    <>
                      <dt>Місце народження</dt>
                      <dd>{birth.text}</dd>
                    </>
                  )}
                  <dt>Опис</dt>
                  <dd>{values.summary}</dd>
                  {genres.length > 0 && (
                    <>
                      <dt>Жанри</dt>
                      <dd>{genres.join(', ')}</dd>
                    </>
                  )}
                  <dt>Посилання</dt>
                  <dd>
                    {[
                      ...draft.links,
                      values.youtubeChannelUrl,
                      values.wikipediaUrl,
                    ]
                      .filter(Boolean)
                      .map((url, index) => (
                        <span key={index}>{url}</span>
                      ))}
                  </dd>
                  <dt>Фото</dt>
                  <dd>{photos.map((photo) => photo.name).join(', ')}</dd>
                </dl>
              )}
              <Field
                id={id('contact')}
                label={t('submit.field.contact.label')}
                error={errorOf('contact')}
                hint={t('submit.field.contact.hint')}
              >
                <input
                  {...inputProps('contact', true)}
                  value={values.contact}
                  onChange={(event) => change('contact', event.target.value)}
                />
              </Field>
              <div className="sub-field">
                <label className="sub-consent">
                  <input
                    {...inputProps('consent')}
                    type="checkbox"
                    checked={values.consent}
                    onChange={(event) =>
                      change('consent', event.target.checked)
                    }
                  />
                  {t('submit.consent')}
                </label>
                {errorOf('consent') && (
                  <span id={id('consent') + '-error'} className="sub-error">
                    {errorOf('consent')}
                  </span>
                )}
              </div>
            </>,
          )}
          <input
            type="text"
            name="faxNumber"
            aria-hidden="true"
            aria-label="Факс"
            tabIndex={-1}
            autoComplete="off"
            className="sub-visually-hidden"
            value={values.faxNumber}
            onChange={(event) => change('faxNumber', event.target.value)}
          />
          {serverError && (
            <div role="alert" className="sub-server-error">
              <strong>{scenarioNames[serverError]}</strong>
              <p>{messages[serverError]}</p>
              {wizard &&
                [
                  'PAYLOAD_TOO_LARGE',
                  'UNSUPPORTED_MEDIA_TYPE',
                  'VALIDATION_FAILED',
                ].includes(serverError) && (
                  <button
                    type="button"
                    onClick={() => {
                      go(serverError === 'VALIDATION_FAILED' ? 3 : 4)
                      setServerError(undefined)
                    }}
                  >
                    {serverError === 'VALIDATION_FAILED'
                      ? 'Виправити посилання'
                      : 'Змінити фото'}
                  </button>
                )}
            </div>
          )}
          <div className="sub-actions">
            {wizard && step > 0 && (
              <button
                type="button"
                disabled={processing}
                onClick={() => go(step - 1)}
              >
                Назад
              </button>
            )}
            <button
              className="sub-primary"
              type="submit"
              disabled={processing || progress !== null || retryWait > 0}
            >
              {wizard && step < 5
                ? 'Далі'
                : retryWait
                  ? 'Повтор через ' + retryWait + ' с'
                  : progress !== null
                    ? t('submit.sending')
                    : serverError
                      ? 'Повторити надсилання'
                      : t('submit.send')}
            </button>
          </div>
        </fieldset>
        {progress !== null && (
          <div className="sub-progress" role="status">
            <label htmlFor={id('progress')}>Надсилання · {progress}%</label>
            <progress id={id('progress')} max={100} value={progress} />
          </div>
        )}
      </form>
    </div>
  )
}
