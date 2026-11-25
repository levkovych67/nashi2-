import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { ApiProblem } from '../../api/client'
import { usePublicConfig } from '../../api/queries'
import { useCopy, type CopyKey } from '../../core/i18n/copy'
import {
  allGenres, clearDraft, emptyState, formErrors, loadDraft, saveDraft, STEPS, stepErrors, stepOfField, toValues,
  type FormState,
} from '../../core/submission/draft'
import { baseField, problemMessageKey, problemToFieldErrors } from '../../core/submission/problemErrors'
import { createSubmissionSchema, DEFAULT_LIMITS } from '../../core/submission/schema'
import { uploadSubmission } from '../../core/submission/upload'
import { describe, Field, FieldError } from './Field'
import { ImagesField } from './ImagesField'
import { LinksField } from './LinksField'
import { PlaceAutocomplete } from './PlaceAutocomplete'
import { StepIndicator } from './StepIndicator'
import { SubmitProgress } from './SubmitProgress'
import { SuggestSuccess } from './SuggestSuccess'
import './suggest.css'

const LAST = STEPS.length - 1
const MAX_GENRES = 5

/** Six-step wizard (submission.form = WIZARD): each step checks its own fields before «Далі»; the last one reviews, takes consent and sends. */
export function SuggestForm() {
  const t = useCopy()
  const config = usePublicConfig().data?.submission
  const limits = useMemo(() => ({ ...DEFAULT_LIMITS, ...config }), [config])
  const schema = useMemo(() => createSubmissionSchema(limits), [limits])

  const [state, setState] = useState<FormState>(() => loadDraft() ?? emptyState())
  const [step, setStep] = useState(0)
  const [touched, setTouched] = useState<ReadonlySet<string>>(new Set())
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const [banner, setBanner] = useState<CopyKey | null>(null)
  const [sending, setSending] = useState(false)
  const [progress, setProgress] = useState(0)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [focusRequest, setFocusRequest] = useState<{ key: string; n: number } | null>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const successRef = useRef<HTMLHeadingElement>(null)
  const inFlight = useRef(false)
  const controller = useRef<AbortController | null>(null)

  const errors = useMemo(() => formErrors(state, schema), [state, schema])
  const failed = banner !== null && Object.keys(serverErrors).length === 0

  useEffect(() => () => controller.current?.abort(), [])
  useEffect(() => {
    if (!done) saveDraft(state)
  }, [state, done])

  // Focus after the step that holds the target has rendered.
  useEffect(() => {
    if (!focusRequest) return
    const root = formRef.current
    const { key } = focusRequest
    const el =
      key === 'heading'
        ? headingRef.current
        : key === 'success'
          ? successRef.current
          : (root?.querySelector<HTMLElement>(`[data-field="${key}"]`) ?? root?.querySelector<HTMLElement>(`[data-field^="${baseField(key)}"]`))
    el?.focus()
  }, [focusRequest, step, done])

  const goTo = (next: number, key = 'heading') => {
    setStep(next)
    setFocusRequest((r) => ({ key, n: (r?.n ?? 0) + 1 }))
  }

  /** What the user sees: the server's message, else the schema's once the field has been touched. */
  const visible = useMemo(() => {
    const out: Record<string, string> = {}
    for (const [key, message] of Object.entries(errors)) if (touched.has(baseField(key))) out[key] = t(message as CopyKey)
    return { ...out, ...serverErrors }
  }, [errors, touched, serverErrors, t])
  const rows = (base: string) => {
    const out: Record<number, string> = {}
    for (const [key, message] of Object.entries(visible)) {
      const m = key.match(/^(\w+)\[(\d+)\]$/)
      if (m && m[1] === base) out[Number(m[2])] = message
    }
    return out
  }
  const err = (field: string) => visible[field]

  const mark = (fields: readonly string[]) => setTouched((prev) => new Set([...prev, ...fields]))
  const update = (patch: Partial<FormState>) => {
    setState((s) => ({ ...s, ...patch }))
    setServerErrors({})
    setBanner(null)
  }
  const first = (keys: string[]) =>
    [...keys].sort((a, b) => stepOfField(a) - stepOfField(b))[0]

  function commitGenre() {
    if (state.genreInput.trim()) setState((s) => ({ ...s, genres: allGenres(s), genreInput: '' }))
  }

  function next() {
    const current = stepErrors(errors, step)
    mark(STEPS[step].fields)
    const bad = Object.keys(current)
    if (bad.length) return setFocusRequest((r) => ({ key: first(bad), n: (r?.n ?? 0) + 1 }))
    if (step === 2) commitGenre()
    goTo(step + 1)
  }

  async function send() {
    if (inFlight.current) return
    mark(STEPS.flatMap((s) => s.fields))
    const bad = Object.keys(errors)
    if (bad.length) {
      const key = first(bad)
      return goTo(stepOfField(key), key)
    }
    inFlight.current = true
    controller.current = new AbortController()
    setSending(true)
    setProgress(0)
    setBanner(null)
    setServerErrors({})
    try {
      await uploadSubmission({ ...toValues(state), faxNumber: state.faxNumber }, setProgress, controller.current.signal)
      clearDraft()
      setDone(true)
      setFocusRequest((r) => ({ key: 'success', n: (r?.n ?? 0) + 1 }))
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      const problem = e instanceof ApiProblem ? e : new ApiProblem({ status: 0, code: 'NETWORK' })
      const fields = problemToFieldErrors(problem)
      const keys = Object.keys(fields)
      setServerErrors(fields)
      if (keys.length) {
        setBanner('submit.error.checkFields')
        const key = first(keys)
        const target = stepOfField(key)
        goTo(target < 0 ? LAST : target, key)
      } else {
        setBanner(problemMessageKey(problem))
        if (problem.status === 413 || problem.status === 415) goTo(4)
      }
    } finally {
      inFlight.current = false
      setSending(false)
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (sending) return
    if (step < LAST) next()
    else void send()
  }

  function again() {
    setState(emptyState())
    setTouched(new Set())
    setServerErrors({})
    setBanner(null)
    setDone(false)
    setStep(0)
    setFocusRequest((r) => ({ key: 'heading', n: (r?.n ?? 0) + 1 }))
  }

  if (done) return <SuggestSuccess ref={successRef} name={state.name.trim()} onAnother={again} />

  const stepId = STEPS[step].id
  const f = (name: string) => `suggest-${name}`
  const submitLabel = sending ? t('submit.sending') : step < LAST ? t('submit.next') : failed ? t('submit.retry') : t('submit.send')

  return (
    <form
      ref={formRef}
      className="n-suggest__form"
      noValidate
      data-testid="suggest-form"
      onSubmit={onSubmit}
      onBlur={(e) => {
        const field = (e.target as HTMLElement).dataset?.field
        if (field) mark([baseField(field)])
      }}
    >
      <StepIndicator step={step} total={STEPS.length} />
      <fieldset className="n-suggest__step" disabled={sending}>
        <h2 ref={headingRef} tabIndex={-1} data-testid="step-title">
          {t(`submit.step.${stepId}`)}
        </h2>

        {stepId === 'who' && (
          <>
            <Field id={f('name')} label={t('submit.field.name.label')} hint={t('submit.field.name.hint')} error={err('name')}>
              <input
                {...describe(f('name'), true, err('name'))}
                data-field="name"
                autoComplete="off"
                maxLength={100}
                value={state.name}
                onChange={(e) => update({ name: e.target.value })}
              />
            </Field>
            <fieldset className="n-suggest__kind">
              <legend>{t('submit.field.kind.label')}</legend>
              {(['PERSON', 'BAND'] as const).map((kind) => (
                <label key={kind} data-selected={state.kind === kind}>
                  <input
                    type="radio"
                    name={f('kind')}
                    data-field={kind === 'PERSON' ? 'kind' : undefined}
                    value={kind}
                    checked={state.kind === kind}
                    onChange={() => update({ kind })}
                  />
                  {t(`submit.kind.${kind}`)}
                </label>
              ))}
              <FieldError id={f('kind')} error={err('kind')} />
            </fieldset>
          </>
        )}

        {stepId === 'where' && (
          <>
            <PlaceAutocomplete
              id={f('origin')}
              field="originPlaceId"
              label={t('submit.field.originPlaceId.label')}
              hint={t('submit.field.originPlaceId.hint')}
              value={state.origin}
              onChange={(origin) => update({ origin })}
              error={err('originPlaceId')}
            />
            {state.kind === 'PERSON' && (
              <PlaceAutocomplete
                id={f('birth')}
                field="birthPlaceId"
                label={t('submit.field.birthPlaceId.label')}
                hint={t('submit.field.birthPlaceId.hint')}
                value={state.birth}
                onChange={(birth) => update({ birth })}
                error={err('birthPlaceId')}
              />
            )}
          </>
        )}

        {stepId === 'about' && (
          <>
            <Field
              id={f('summary')}
              label={t('submit.field.summary.label')}
              hint={`${t('submit.field.summary.hint')} ${t('submit.summary.count', { count: state.summary.length, max: limits.summaryMax })}`}
              error={err('summary')}
            >
              <textarea
                {...describe(f('summary'), true, err('summary'))}
                data-field="summary"
                rows={5}
                value={state.summary}
                onChange={(e) => update({ summary: e.target.value })}
              />
            </Field>
            <Field
              id={f('genre')}
              label={t('submit.field.genres.label')}
              hint={t('submit.field.genres.hint')}
              error={err('genres') ?? Object.entries(visible).find(([k]) => baseField(k) === 'genres')?.[1]}
            >
              {state.genres.length > 0 && (
                <ul className="n-suggest__chips">
                  {state.genres.map((g) => (
                    <li key={g}>
                      {g}
                      <button
                        type="button"
                        aria-label={t('submit.genre.remove', { name: g })}
                        onClick={() => update({ genres: state.genres.filter((x) => x !== g) })}
                      >
                        ×
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <div className="n-suggest__inline">
                <input
                  {...describe(f('genre'), true)}
                  data-field="genres"
                  autoComplete="off"
                  maxLength={40}
                  disabled={state.genres.length >= MAX_GENRES}
                  value={state.genreInput}
                  onChange={(e) => update({ genreInput: e.target.value })}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ',') {
                      e.preventDefault()
                      mark(['genres'])
                      if (state.genreInput.trim().length >= 2) update({ genres: allGenres(state), genreInput: '' })
                    }
                  }}
                />
                <button
                  type="button"
                  className="n-suggest__btn"
                  disabled={state.genres.length >= MAX_GENRES || state.genreInput.trim().length < 2}
                  onClick={() => update({ genres: allGenres(state), genreInput: '' })}
                >
                  {t('submit.genre.add')}
                </button>
              </div>
            </Field>
          </>
        )}

        {stepId === 'links' && (
          <>
            <LinksField
              id={f('link')}
              label={t('submit.field.links.label')}
              hint={t('submit.field.links.hint')}
              links={state.links}
              onChange={(links) => update({ links })}
              error={err('links')}
              rowErrors={rows('links')}
            />
            <Field id={f('youtube')} label={t('submit.field.youtubeChannelUrl.label')} hint={t('submit.field.youtubeChannelUrl.hint')} error={err('youtubeChannelUrl')}>
              <input
                {...describe(f('youtube'), true, err('youtubeChannelUrl'))}
                data-field="youtubeChannelUrl"
                type="url"
                inputMode="url"
                autoComplete="off"
                placeholder="https://"
                value={state.youtubeChannelUrl}
                onChange={(e) => update({ youtubeChannelUrl: e.target.value })}
              />
            </Field>
            <Field id={f('wikipedia')} label={t('submit.field.wikipediaUrl.label')} hint={t('submit.field.wikipediaUrl.hint')} error={err('wikipediaUrl')}>
              <input
                {...describe(f('wikipedia'), true, err('wikipediaUrl'))}
                data-field="wikipediaUrl"
                type="url"
                inputMode="url"
                autoComplete="off"
                placeholder="https://"
                value={state.wikipediaUrl}
                onChange={(e) => update({ wikipediaUrl: e.target.value })}
              />
            </Field>
          </>
        )}

        {stepId === 'photos' && (
          <ImagesField
            id={f('images')}
            label={t('submit.field.images.label')}
            hint={t('submit.field.images.hint')}
            images={state.images}
            onChange={(images) => update({ images })}
            onBusy={setBusy}
            max={limits.maxImages}
            maxMb={limits.maxImageMb}
            maxTotalMb={limits.maxTotalMb}
            error={err('images')}
            rowErrors={rows('images')}
          />
        )}

        {stepId === 'review' && (
          <>
            <dl className="n-suggest__review" data-testid="review">
              <dt>{t('submit.review.artist')}</dt>
              <dd>
                {state.name.trim()} · {t(`submit.kind.${state.kind}`)}
              </dd>
              <dt>{t('submit.review.origin')}</dt>
              <dd>{state.origin.text}</dd>
              {state.kind === 'PERSON' && state.birth.id && (
                <>
                  <dt>{t('submit.review.birth')}</dt>
                  <dd>{state.birth.text}</dd>
                </>
              )}
              <dt>{t('submit.review.summary')}</dt>
              <dd>{state.summary.trim()}</dd>
              {allGenres(state).length > 0 && (
                <>
                  <dt>{t('submit.review.genres')}</dt>
                  <dd>{allGenres(state).join(', ')}</dd>
                </>
              )}
              <dt>{t('submit.review.links')}</dt>
              <dd>
                {toValues(state).links.concat(toValues(state).youtubeChannelUrl ?? [], toValues(state).wikipediaUrl ?? []).map((url) => (
                  <span key={url}>{url}</span>
                ))}
              </dd>
              <dt>{t('submit.review.photos')}</dt>
              <dd>{state.images.map((i) => i.name).join(', ')}</dd>
            </dl>
            <Field id={f('contact')} label={t('submit.field.contact.label')} hint={t('submit.field.contact.hint')} error={err('contact')}>
              <input
                {...describe(f('contact'), true, err('contact'))}
                data-field="contact"
                autoComplete="off"
                value={state.contact}
                onChange={(e) => update({ contact: e.target.value })}
              />
            </Field>
            <div className="n-suggest__field">
              <label className="n-suggest__consent">
                <input
                  {...describe(f('consent'), false, err('consent'))}
                  data-field="consent"
                  type="checkbox"
                  checked={state.consent}
                  onChange={(e) => update({ consent: e.target.checked })}
                />
                {t('submit.consent')}
              </label>
              <FieldError id={f('consent')} error={err('consent')} />
            </div>
          </>
        )}

        <input
          className="n-suggest__trap"
          type="text"
          name="faxNumber"
          aria-hidden="true"
          aria-label={t('submit.fax')}
          tabIndex={-1}
          autoComplete="off"
          value={state.faxNumber}
          onChange={(e) => setState((s) => ({ ...s, faxNumber: e.target.value }))}
        />

        {banner && (
          <p className="n-suggest__banner" role="alert" data-testid="submit-error">
            {t(banner)}
          </p>
        )}
        {sending && <SubmitProgress ratio={progress} />}
      </fieldset>

      {/* mousedown must not blur the focused field: its error line would appear and shift the button before the click lands */}
      <div className="n-suggest__actions" onMouseDown={(e) => e.preventDefault()}>
        {step > 0 && (
          <button type="button" className="n-suggest__btn" aria-disabled={sending || busy} onClick={() => !(sending || busy) && goTo(step - 1)}>
            {t('submit.back')}
          </button>
        )}
        <button type="submit" className="n-suggest__btn n-suggest__btn--primary" aria-disabled={sending || busy} onClick={(e) => (sending || busy) && e.preventDefault()}>
          {submitLabel}
        </button>
      </div>
    </form>
  )
}
