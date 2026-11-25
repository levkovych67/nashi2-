import { useMemo, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { DECISIONS } from '../decisions/catalog'
import { defaultVariant } from '../handoff/review'
import { scenarios, type ScenarioId, type MapTarget } from '../flows/scenarios'
import { useDraft } from '../selection/store'
import { recordedDecisions } from '../selection/selection'
import { DeviceFrame } from '../shell/DeviceFrame'
import type { DeviceKind } from '../shell/devices'
import { MapStudies } from '../workbench/MapStudies'
import {
  ArtistJourneyCard,
  type JourneyStage,
} from '../workbench/ArtistStudies'
import { NavigationStudy } from '../workbench/NavigationStudy'
import {
  SubmissionStudy,
  type SubmissionEvent,
} from '../workbench/SubmissionStudy'
import { SoundMuted } from '../workbench/sound'
import { useAtlasData, type AtlasData, type Pin } from '../workbench/data'
import './FlowPage.css'

const journeySteps: Record<JourneyStage, number> = {
  gesture: 6,
  preview: 7,
  profile: 8,
  like: 9,
}
const stages: JourneyStage[] = ['gesture', 'preview', 'profile', 'like']
function FlowRun({
  scenarioId,
  device,
  data,
  restart,
  setDevice,
}: {
  scenarioId: ScenarioId
  device: DeviceKind
  data: AtlasData
  restart: () => void
  setDevice: (device: DeviceKind) => void
}) {
  const draft = useDraft()
  const choices = useMemo(
    () =>
      Object.fromEntries(
        DECISIONS.map((d) => [
          d.id,
          draft[d.id]?.variant ??
            recordedDecisions[d.id]?.variant ??
            defaultVariant(d.id),
        ]),
      ),
    [draft],
  )
  const scenario = scenarios[scenarioId]
  const map = scenarioId === 'map-to-musician'
  const [step, setStep] = useState(1)
  const [pin, setPin] = useState<Pin | null>(null)
  const [muted, setMuted] = useState(false)
  const [correction, setCorrection] = useState(false)
  const mapRef = useRef<((target?: MapTarget) => void) | null>(null)
  const journeyRef = useRef<(() => void) | null>(null)
  const formRef = useRef<HTMLDivElement>(null)
  const reviewRef = useRef<((phase?: 2 | 3 | 4 | 5) => void) | null>(null)
  const form = () => formRef.current?.querySelector('form')
  const originChoice =
    draft['map.origin-notice'] ?? recordedDecisions['map.origin-notice']
  const showOriginLabel =
    choices['map.origin-notice'] === 'PIN_CARD_LABEL' ||
    originChoice?.params?.includePinCardLabel === true
  const theme = choices.identity
  const [title, guide] = scenario.steps[step - 1]
  function explored(event: 'region' | 'place' | 'pin') {
    setStep(event === 'region' ? 2 : event === 'place' ? 4 : 6)
  }
  function progress(event: SubmissionEvent) {
    if (event === 'invalid') {
      setCorrection(true)
      return
    }
    if (event === 'submitted') {
      setCorrection(false)
      setStep(6)
      return
    }
    if (event === 'fix') setCorrection(false)
    const to =
      event === 'origin'
        ? 3
        : event === 'photo'
          ? 4
          : event === 'review'
            ? 5
            : 2
    setStep((s) => Math.max(s, to))
  }
  function next() {
    if (map) {
      if (step === 1) mapRef.current?.('region')
      else if (step === 3) mapRef.current?.('place')
      else if (step === 5) mapRef.current?.('pin')
      else if (step >= 6 && step <= 9) journeyRef.current?.()
      else setStep((s) => Math.min(10, s + 1))
    } else if (step === 1) setStep(2)
    else if (choices['submission.form'] === 'WIZARD' || step === 5)
      form()?.requestSubmit()
    else {
      const selector =
        step === 2
          ? '[data-field="originPlaceId"]'
          : step === 3
            ? '[data-field="images"]'
            : '[data-field="consent"]'
      const field = form()?.querySelector<HTMLElement>(selector)
      field?.focus()
      setStep((s) => Math.min(5, s + 1))
    }
  }
  function back() {
    if (!map) {
      const previous = Math.max(1, step - 1)
      if (previous > 1) reviewRef.current?.(previous as 2 | 3 | 4 | 5)
      setStep(previous)
      return
    }
    const previous = step - 1
    if (previous === 1 || previous === 3)
      mapRef.current?.(previous === 1 ? 'overview' : 'region')
    if (previous <= 5) setPin(null)
    setStep(previous)
  }
  return (
    <section className="flow-run">
      <h2>{scenario.title}</h2>
      <div className="flow-toolbar">
        <label>
          Екран{' '}
          <select
            value={device}
            onChange={(e) => setDevice(e.target.value as DeviceKind)}
          >
            <option value="mobile">Телефон</option>
            <option value="desktop">Комп’ютер</option>
          </select>
        </label>
        {map && (
          <button
            type="button"
            aria-pressed={muted}
            onClick={() => setMuted((v) => !v)}
          >
            {muted ? 'Увімкнути звук' : 'Вимкнути звук'}
          </button>
        )}
        <button onClick={restart}>Почати спочатку</button>
      </div>
      <div className="flow-instructions">
        <div aria-live="polite" data-testid="flow-progress" data-step={step}>
          <small>
            Крок {step} із {scenario.steps.length}
          </small>
          <h3>{title}</h3>
          <p>
            {correction
              ? 'Виправте позначені поля й повторіть надсилання.'
              : guide}
          </p>
        </div>
        <div className="flow-actions">
          <button data-testid="flow-back" disabled={step === 1} onClick={back}>
            Назад
          </button>
          <button
            data-testid="flow-next"
            disabled={step === scenario.steps.length}
            onClick={next}
          >
            Далі
          </button>
        </div>
        <progress
          max={scenario.steps.length}
          value={step}
          aria-label="Прогрес сценарію"
        />
      </div>
      <div className={'scenario-frame ' + device}>
        <DeviceFrame device={device} identity={theme}>
          <SoundMuted.Provider value={muted}>
            <div className="proto" data-direction={theme} data-identity={theme}>
              {map ? (
                <>
                  <div hidden={step >= 6 && step <= 9}>
                    <MapStudies
                      id="map.aggregation"
                      variant={choices['map.aggregation']}
                      choices={choices}
                      includePinCardLabel={showOriginLabel}
                      data={data}
                      theme={theme}
                      advanceRef={mapRef}
                      onExplore={explored}
                      onMusician={(p) => {
                        setPin(p)
                        setStep(6)
                      }}
                    />
                  </div>
                  {pin && step >= 6 && step <= 9 && (
                    <ArtistJourneyCard
                      key={pin.id}
                      pin={pin}
                      theme={theme}
                      data={data}
                      stage={stages[step - 6]}
                      advanceRef={journeyRef}
                      onStage={(s) => setStep(journeySteps[s])}
                      triggerVariant={
                        choices[
                          device === 'mobile'
                            ? 'audio.mobile-trigger'
                            : 'audio.desktop-hover'
                        ]
                      }
                      playerVariant={choices['audio.player']}
                      includePinCardLabel={showOriginLabel}
                      previewVariant={choices['musician.preview']}
                      pageVariant={choices['musician.page']}
                      likeVariant={choices['like.button']}
                      onBack={() => setStep(10)}
                    />
                  )}
                </>
              ) : (
                <>
                  <div hidden={step !== 1}>
                    <NavigationStudy
                      id="nav.structure"
                      variant={choices['nav.structure']}
                      data={data}
                      onSuggest={() => setStep(2)}
                    />
                  </div>
                  <div ref={formRef} hidden={step === 1}>
                    <SubmissionStudy
                      data={data}
                      variant={choices['submission.form']}
                      onProgress={progress}
                      onReviewRef={reviewRef}
                    />
                  </div>
                </>
              )}
            </div>
          </SoundMuted.Provider>
        </DeviceFrame>
      </div>
    </section>
  )
}
export function FlowPage() {
  const { scenarioId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const device: DeviceKind =
    params.get('device') === 'desktop' ? 'desktop' : 'mobile'
  const [run, setRun] = useState(0)
  const { data, error, retry } = useAtlasData(true)
  return (
    <main className="studio-page flow-workbench">
      <h1>Сценарії</h1>
      <nav className="studio-tabs" aria-label="Обрати сценарій">
        {Object.entries(scenarios).map(([id, item]) => (
          <Link
            key={id}
            to={'/flow/' + id + '?device=' + device}
            aria-current={id === scenarioId ? 'page' : undefined}
          >
            {item.title}
          </Link>
        ))}
      </nav>
      {Object.hasOwn(scenarios, scenarioId) ? (
        data ? (
          <FlowRun
            key={scenarioId + run}
            scenarioId={scenarioId as ScenarioId}
            data={data}
            device={device}
            restart={() => setRun((v) => v + 1)}
            setDevice={(value) => {
              const next = new URLSearchParams(params)
              next.set('device', value)
              setParams(next)
            }}
          />
        ) : (
          <p role="status">
            {error ? (
              <button onClick={retry}>Повторити завантаження даних</button>
            ) : (
              'Завантажуємо сценарій…'
            )}
          </p>
        )
      ) : (
        <p>Сценарій не знайдено. Оберіть один зі списку.</p>
      )}
    </main>
  )
}
