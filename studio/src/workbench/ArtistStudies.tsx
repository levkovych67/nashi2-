import { useDraft } from '../selection/store'
import { recordedDecisions } from '../selection/selection'
import {
  useEffect,
  useImperativeHandle,
  type RefObject,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react'
import { OriginNote } from './AtlasPlate'
import { type AtlasData, type Pin } from './data'
import { LikeButton, likesLabel, ReactionFeedback } from './LikeButton'
import { likesFor, simulateLikeLimit } from './likes'
import {
  MusicianProfile,
  OriginMap,
  Portrait,
  TextSource,
} from './MusicianProfile'
import { groupLinks, partialProfile, type Profile, useProfile } from './profile'
import { PlaybackButton } from './SampleButton'
import { StudyCityMap } from './StudyCityMap'
import { type SoundDeck, useSound } from './sound'
import './ArtistStudies.css'

function Player({ pin, deck }: { pin: Pin; deck: SoundDeck }) {
  const clips = deck.lists[pin.id]
  const active = deck.state.id === pin.id
  const clip = (active && deck.state.clip) || clips?.[0]
  return (
    <section className="artist-player" aria-label="Програвач фрагментів">
      <div className="artist-record" aria-hidden="true">
        <i />
      </div>
      <div className="artist-track-meta">
        <strong>{pin.name}</strong>
        {clip && (
          <>
            <span>
              {clip.title} · {clip.durationSec} с
            </span>
            <small>{clip.attribution}</small>
          </>
        )}
      </div>
      {clip && (
        <progress
          max={1}
          value={active ? deck.state.progress : 0}
          aria-label="Прослухано"
        />
      )}
      <div className="artist-player-actions">
        <PlaybackButton deck={deck} pin={pin} />
        {clips && clips.length > 1 && (
          <button onClick={() => deck.toggle(pin, true)}>Інший фрагмент</button>
        )}
      </div>
    </section>
  )
}
function ArtistCard({
  pin,
  variant,
  open,
  close,
  children,
  fallback,
  originLabel = false,
}: {
  pin: Pin
  variant: string
  open: () => void
  close: () => void
  children?: ReactNode
  fallback?: Profile
  originLabel?: boolean
}) {
  const profile = useProfile(pin.slug, fallback)
  const [expanded, setExpanded] = useState(false)
  const startY = useRef<number | undefined>(undefined)
  const dragged = useRef(false)
  const sheet = variant === 'BOTTOM_SHEET'
  return (
    <article
      className={'artist-card ' + variant + (expanded ? ' expanded' : '')}
      aria-label={'Картка — ' + pin.name}
    >
      {sheet && (
        <button
          className="artist-grab"
          aria-expanded={expanded}
          aria-label={expanded ? 'Згорнути картку' : 'Розгорнути картку'}
          onPointerDown={(e) => {
            e.currentTarget.setPointerCapture?.(e.pointerId)
            startY.current = e.clientY
            dragged.current = false
          }}
          onPointerUp={(e) => {
            if (startY.current === undefined) return
            const delta =
              (e.clientY - startY.current) /
              (e.currentTarget.getBoundingClientRect().width /
                e.currentTarget.offsetWidth || 1)
            startY.current = undefined
            if (Math.abs(delta) < 24) return
            dragged.current = true
            if (delta > 0) close()
            else setExpanded(true)
          }}
          onPointerCancel={() => {
            startY.current = undefined
          }}
          onClick={() => {
            if (!dragged.current) setExpanded((v) => !v)
            dragged.current = false
          }}
        >
          <i />
        </button>
      )}
      <button
        className="artist-close"
        onClick={close}
        aria-label="Закрити картку"
      >
        ×
      </button>
      <div className="artist-card-id">
        {profile && <Portrait key={pin.id} profile={profile} />}
        <div>
          <small>
            {pin.kind === 'BAND' ? 'Гурт' : 'Музикант'}
            {profile && ' · ' + profile.origin.placeName}
          </small>
          <h2>{pin.name}</h2>
          {profile && !profile.partial && (
            <span>{likesLabel(profile.likesCount)}</span>
          )}
          {originLabel && (
            <small>
              {profile?.origin.basis === 'ORIGIN'
                ? 'Місце походження'
                : pin.kind === 'BAND'
                  ? 'Місце заснування'
                  : 'Місце народження'}
            </small>
          )}
        </div>
      </div>
      {profile && (expanded || !sheet) && (
        <div className="artist-excerpt">
          <p>{profile.summary || profile.tagline}</p>
          <TextSource source={profile.textSource} />
          {groupLinks(profile.links)
            .filter((g) => g.group === 'MUSIC')
            .flatMap((g) => g.links.slice(0, 2))
            .map((l) => (
              <a key={l.url} href={l.url}>
                {l.label}
              </a>
            ))}
        </div>
      )}
      <div className="artist-card-actions">
        {children}
        <button type="button" onClick={open}>
          Детальніше
        </button>
      </div>
    </article>
  )
}
function PinControl({
  pin,
  variant,
  hasAudio,
  active,
  progress,
  selected,
  tap,
  hold,
  unlock,
  release,
  hover,
  leave,
}: {
  pin: Pin
  variant: string
  hasAudio: boolean
  active: boolean
  progress: number
  selected: boolean
  tap: () => void
  hold: () => void
  unlock: () => void
  release: () => void
  hover: () => void
  leave: () => void
}) {
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const pointer = useRef<{ x: number; y: number } | null>(null)
  const held = useRef(false)
  const primed = useRef(false)
  const suppress = useRef(false)
  const events = useRef({ hold, hover, leave })
  events.current = { hold, hover, leave }
  const hovered = useRef(false)
  const long = variant === 'LONG_PRESS_PLAY'
  const desktop = variant === 'RING_PROGRESS' || variant === 'EQUALIZER_BADGE'
  function clear() {
    clearTimeout(timer.current)
  }
  function enter() {
    if (!desktop) return
    clear()
    timer.current = setTimeout(() => {
      hovered.current = true
      events.current.hover()
    }, 250)
  }
  function exit() {
    clear()
    if (hovered.current) {
      hovered.current = false
      events.current.leave()
    }
  }
  useEffect(() => () => clearTimeout(timer.current), [])
  return (
    <button
      type="button"
      className={'artist-pin ' + variant}
      data-audio={hasAudio}
      data-active={active}
      aria-label={pin.name + (hasAudio ? ', є фрагмент' : ', фрагмента немає')}
      aria-pressed={selected}
      style={{ '--played': `${progress * 360}deg` } as CSSProperties}
      onPointerEnter={(e) => {
        if (e.pointerType !== 'touch') enter()
      }}
      onPointerLeave={exit}
      onFocus={(e) => {
        if (e.currentTarget.matches(':focus-visible')) enter()
      }}
      onBlur={exit}
      onContextMenu={(e) => {
        if (long) e.preventDefault()
      }}
      onPointerDown={(e) => {
        suppress.current = false
        if (!long || e.button !== 0) return
        e.stopPropagation()
        held.current = false
        if (!primed.current) {
          primed.current = true
          unlock()
        }
        e.currentTarget.setPointerCapture?.(e.pointerId)
        pointer.current = { x: e.clientX, y: e.clientY }
        clear()
        timer.current = setTimeout(() => {
          suppress.current = true
          pointer.current = null
          held.current = true
          events.current.hold()
        }, 400)
      }}
      onPointerMove={(e) => {
        const pos = pointer.current
        if (pos && Math.hypot(e.clientX - pos.x, e.clientY - pos.y) > 10) {
          clear()
          suppress.current = true
          pointer.current = null
        }
      }}
      onPointerUp={() => {
        if (long) {
          if (held.current) release()
          held.current = false
          clear()
          pointer.current = null
        }
      }}
      onPointerCancel={() => {
        clear()
        pointer.current = null
        suppress.current = true
      }}
      onClick={(e) => {
        e.stopPropagation()
        clear()
        if (!suppress.current) tap()
        suppress.current = false
      }}
    >
      <span className="artist-pin-disc">
        {pin.name
          .split(/\s+/)
          .slice(0, 2)
          .map((w) => w[0])
          .join('')}
      </span>
      {hasAudio && (
        <span className="artist-pin-sound" aria-hidden="true">
          {variant === 'EQUALIZER_BADGE' && active ? (
            <i className="artist-eq">
              <b />
              <b />
              <b />
            </i>
          ) : (
            '♪'
          )}
        </span>
      )}
      <span className="artist-pin-name">{pin.name}</span>
    </button>
  )
}
function pickPins(data: AtlasData, player: boolean) {
  const profiles = data.pins.filter(
    (p) => p.placeId === 'Q36036' && data.profiles.has(p.slug),
  )
  const playable = profiles.filter((p) => p.hasAudio)
  const silent = profiles.filter((p) => !p.hasAudio)
  const first =
    playable.find(
      (p) => p.slug === (player ? 'mertvyi-piven' : 'okean-elzy'),
    ) || playable[0]
  const second =
    playable.find((p) => p.slug === 'odyn-v-kanoe' && p !== first) ||
    playable.find((p) => p !== first)
  return [
    first,
    second,
    silent.find((p) => p.slug === 'formula-vody') || silent[0],
  ].filter((p): p is Pin => !!p)
}
const tips: Record<string, string> = {
  TAP_PREVIEW_AND_PLAY:
    'Торкніться позначки, щоб послухати. Повторний дотик — пауза.',
  TAP_PREVIEW_PLAY_BUTTON:
    'Торкніться позначки й натисніть «Слухати фрагмент» у картці.',
  LONG_PRESS_PLAY:
    'Утримуйте позначку 0,4 с для звуку. Короткий дотик відкриває картку.',
  RING_PROGRESS: 'Увімкніть звук і наведіть вказівник на позначку.',
  EQUALIZER_BADGE: 'Увімкніть звук і наведіть вказівник на позначку.',
}
function ArtistMapStudy({
  id,
  variant,
  data,
  theme,
  pins,
}: {
  id: string
  variant: string
  data: AtlasData
  theme: string
  pins: Pin[]
}) {
  const draft = useDraft()
  const pick = (id: string, fallback: string) =>
    draft[id]?.variant ?? recordedDecisions[id]?.variant ?? fallback
  const origin =
    draft['map.origin-notice'] ?? recordedDecisions['map.origin-notice']
  const [pin, setPin] = useState<Pin | null>(
    id === 'audio.player' || id === 'musician.preview' ? pins[0] : null,
  )
  const [page, setPage] = useState(false)
  const [all, setAll] = useState(false)
  const [ios, setIos] = useState(false)
  const [enabled, setEnabled] = useState(false)
  const [hint, setHint] = useState(false)
  const deck = useSound(pins, all, ios)
  const hoverId = useRef('')
  const firstGesture = useRef(false)
  function unlockOnGesture(target: EventTarget) {
    if (
      !desktop ||
      firstGesture.current ||
      !(target instanceof Element) ||
      target.closest('.artist-sound-toggle')
    )
      return
    firstGesture.current = true
    deck.unlock()
    setEnabled(true)
    setHint(false)
  }
  const closeRef = useRef(() => {})
  function close() {
    deck.stop()
    setPin(null)
    setHint(false)
  }
  closeRef.current = close
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (
        e.key === 'Escape' &&
        ((e.target instanceof Node && root.current?.contains(e.target)) ||
          document.querySelectorAll('.artist-map-study').length === 1)
      )
        closeRef.current()
    }
    document.addEventListener('keydown', key)
    return () => document.removeEventListener('keydown', key)
  }, [])
  const root = useRef<HTMLElement>(null)
  const desktop = id === 'audio.desktop-hover'
  const audio = id.startsWith('audio.')
  const mini = variant === 'MINI_BAR'
  function select(p: Pin) {
    if (variant === 'TAP_PREVIEW_AND_PLAY') deck.toggle(p)
    else if (pin?.id !== p.id || variant === 'LONG_PRESS_PLAY') deck.stop()
    setHint(false)
    setPin(p)
  }
  const current = pins.find((p) => p.id === deck.state.id)
  if (page && pin)
    return (
      <MusicianProfile
        slug={pin.slug}
        data={data}
        variant={pick('musician.page', 'HERO')}
        likeVariant={pick('like.button', 'HEART_BURST')}
        back={() => setPage(false)}
      />
    )
  const cardVariant =
    id === 'musician.preview'
      ? variant
      : pick('musician.preview', 'BOTTOM_SHEET')
  const renderPin = (p: Pin) => (
    <PinControl
      pin={p}
      variant={variant}
      hasAudio={all || p.hasAudio}
      active={current?.id === p.id && deck.state.status === 'playing'}
      progress={current?.id === p.id ? deck.state.progress : 0}
      selected={pin?.id === p.id}
      unlock={deck.unlock}
      release={() => {
        if (deck.state.id === p.id && deck.state.status === 'blocked')
          deck.toggle(p)
      }}
      tap={() => select(p)}
      hold={() => {
        setPin(null)
        deck.toggle(p)
      }}
      hover={() => {
        hoverId.current = p.id
        if (!enabled) {
          setHint(p.hasAudio || all)
          return
        }
        setHint(false)
        // Hover only plays, as in the flow: a card opening under the map would
        // push the hovered pin away from the pointer and end the fragment.
        // A click still opens the card.
        if (!p.hasAudio && !all) deck.stop()
        else if (deck.state.id !== p.id || deck.state.status !== 'playing')
          deck.toggle(p)
      }}
      leave={() => {
        if (hoverId.current === p.id) {
          deck.stop(true)
          hoverId.current = ''
        }
      }}
    />
  )
  return (
    <section
      ref={root}
      className="artist-map-study"
      onPointerDownCapture={(e) => unlockOnGesture(e.target)}
      onKeyDownCapture={(e) => unlockOnGesture(e.target)}
    >
      <header className="artist-mast">
        <b>Наші</b>
        <span>Музична карта Львова</span>
      </header>
      <div className={'artist-map-layout ' + cardVariant}>
        <StudyCityMap
          data={data}
          pins={pins}
          theme={theme}
          renderPin={renderPin}
          close={close}
          inset={pin && cardVariant === 'FLOATING_CARD' ? 340 : 0}
        />
        {desktop && (
          <button
            className="artist-sound-toggle"
            aria-pressed={enabled}
            onClick={() => {
              firstGesture.current = true
              if (enabled) deck.stop()
              else deck.unlock()
              setEnabled((v) => !v)
              setHint(false)
            }}
          >
            {enabled ? 'Вимкнути звук' : 'Увімкнути звук'}
          </button>
        )}
        {pin && (
          <ArtistCard
            key={pin.id}
            pin={pin}
            originLabel={
              origin?.variant === 'PIN_CARD_LABEL' ||
              origin?.params?.includePinCardLabel === true
            }
            variant={cardVariant}
            close={close}
            open={() => {
              deck.stop()
              setPage(true)
            }}
          >
            {!mini &&
              (id === 'audio.player' ? (
                <Player deck={deck} pin={pin} />
              ) : (
                <PlaybackButton deck={deck} pin={pin} />
              ))}
          </ArtistCard>
        )}
      </div>
      {mini && pin && (
        <div className="artist-minibar">
          <Player pin={pin} deck={deck} />
        </div>
      )}
      {deck.state.status === 'playing' && current && (
        <div data-testid="playing-indicator" className="artist-now">
          <span aria-hidden="true">♪</span> Зараз звучить · {current.name}
          {id !== 'audio.player' && (
            <small>{deck.state.clip?.attribution}</small>
          )}
          {/* On desktop the fragment plays only while the pointer rests on the pin. */}
          {!pin && !desktop && (
            <button onClick={() => deck.toggle(current)}>Пауза</button>
          )}
        </div>
      )}
      {(!pin || desktop) && audio && tips[variant] && (
        <p className="artist-tip">{tips[variant]}</p>
      )}
      {hint && (
        <p role="status" className="artist-hint">
          Щоб почути фрагмент при наведенні, натисніть «Увімкнути звук».
        </p>
      )}
      {!pin && deck.state.status === 'blocked' && current && (
        <p role="status">
          Браузер призупинив звук.{' '}
          <button onClick={() => deck.toggle(current)}>
            Спробувати ще раз
          </button>
        </p>
      )}
      <OriginNote />
      {audio && (
        <details className="artist-settings">
          <summary>Умови прослуховування</summary>
          <label>
            <input
              type="checkbox"
              checked={all}
              onChange={(e) => setAll(e.target.checked)}
            />
            Фрагменти на всіх позначках
          </label>
          <label>
            <input
              type="checkbox"
              checked={ios}
              onChange={(e) => {
                deck.stop()
                firstGesture.current = false
                setIos(e.target.checked)
                setEnabled(false)
              }}
            />
            Симулювати iPhone · без згасання
          </label>
        </details>
      )}
    </section>
  )
}

export type JourneyStage = 'gesture' | 'preview' | 'profile' | 'like'
export interface ArtistJourneyProps {
  includePinCardLabel?: boolean
  pin: Pin
  data: AtlasData
  theme: string
  previewVariant: string
  pageVariant: string
  likeVariant: string
  onBack: () => void
  stage?: JourneyStage
  onStage?: (stage: JourneyStage) => void
  advanceRef?: RefObject<(() => void) | null>
  triggerVariant?: string
  playerVariant?: string
}
export function ArtistJourneyCard({
  pin,
  data,
  theme,
  previewVariant,
  pageVariant,
  likeVariant,
  onBack,
  stage,
  includePinCardLabel,
  onStage,
  advanceRef,
  triggerVariant = 'TAP_PREVIEW_AND_PLAY',
  playerVariant = 'IN_CARD',
}: ArtistJourneyProps) {
  const [local, setLocal] = useState<JourneyStage>('preview')
  const current = stage ?? local
  const fallback = useMemo(() => partialProfile(pin, data), [pin, data])
  const profile = useProfile(pin.slug, fallback)
  const pins = useMemo(
    () => [{ ...pin, slug: fallback ? undefined : pin.slug }],
    [pin, fallback],
  )
  const deck = useSound(pins)
  const [unlocked, setUnlocked] = useState(false)
  function go(next: JourneyStage) {
    setLocal(next)
    onStage?.(next)
  }
  function close() {
    deck.stop()
    onBack()
  }
  function details() {
    deck.stop()
    go('profile')
  }
  function gesture(play: boolean) {
    if (play) deck.toggle(pin)
    go('preview')
  }
  function advance() {
    if (current === 'gesture')
      gesture(triggerVariant !== 'TAP_PREVIEW_PLAY_BUTTON')
    else if (current === 'preview') details()
    else if (current === 'profile' && profile) {
      likesFor(pin.id, profile.likesCount).tap()
      go('like')
    } else if (current === 'like') close()
  }
  useImperativeHandle(advanceRef, () => advance)
  const desktop = ['RING_PROGRESS', 'EQUALIZER_BADGE'].includes(triggerVariant)
  const playing = deck.state.status === 'playing'
  const mapPins = useMemo(() => [pin], [pin])
  const gesturePin = () => (
    <PinControl
      pin={pin}
      variant={triggerVariant}
      unlock={deck.unlock}
      release={() => {
        if (deck.state.status === 'blocked') deck.toggle(pin)
      }}
      hasAudio={pin.hasAudio}
      active={playing}
      progress={deck.state.progress}
      selected
      tap={() => gesture(triggerVariant === 'TAP_PREVIEW_AND_PLAY' || desktop)}
      hold={() => {
        deck.toggle(pin)
      }}
      hover={() => {
        if (unlocked && !playing) deck.toggle(pin)
      }}
      leave={() => deck.stop(true)}
    />
  )
  return (
    <section
      className="artist-journey"
      data-direction={theme}
      onKeyDown={(e) => {
        if (e.key === 'Escape') close()
      }}
    >
      {current === 'profile' || current === 'like' ? (
        <ReactionFeedback.Provider value={() => go('like')}>
          <MusicianProfile
            slug={pin.slug}
            fallback={fallback}
            variant={pageVariant}
            likeVariant={likeVariant}
            data={data}
            back={close}
          />
        </ReactionFeedback.Provider>
      ) : (
        <>
          <header className="artist-mast">
            <b>Наші</b>
            <button onClick={close}>Повернутися на карту</button>
          </header>
          {current === 'gesture' ? (
            <div className="journey-gesture" data-trigger={triggerVariant}>
              {pin.placeId === 'Q36036' ? (
                <StudyCityMap
                  data={data}
                  pins={mapPins}
                  theme={theme}
                  close={close}
                  renderPin={gesturePin}
                />
              ) : (
                <>
                  {profile && <OriginMap data={data} profile={profile} />}
                  <div className="journey-gesture-pin">{gesturePin()}</div>
                </>
              )}
              <strong>{pin.name}</strong>
              <p>{tips[triggerVariant]}</p>
              {desktop && (
                <button
                  aria-pressed={unlocked}
                  onClick={() => {
                    if (unlocked) deck.stop()
                    else deck.unlock()
                    setUnlocked((v) => !v)
                  }}
                >
                  {unlocked ? 'Вимкнути звук' : 'Увімкнути звук'}
                </button>
              )}
              <button onClick={() => go('preview')}>Відкрити картку</button>
            </div>
          ) : (
            <>
              <div
                className={'artist-map-layout journey-layout ' + previewVariant}
              >
                {pin.placeId === 'Q36036' ? (
                  <StudyCityMap
                    data={data}
                    pins={mapPins}
                    theme={theme}
                    close={close}
                    renderPin={gesturePin}
                    inset={previewVariant === 'FLOATING_CARD' ? 275 : 0}
                  />
                ) : (
                  <div className="artist-journey-map" onClick={close}>
                    {profile && <OriginMap data={data} profile={profile} />}
                  </div>
                )}
                <ArtistCard
                  pin={pin}
                  fallback={fallback}
                  originLabel={includePinCardLabel}
                  variant={previewVariant}
                  close={close}
                  open={details}
                >
                  {playerVariant !== 'MINI_BAR' && (
                    <PlaybackButton pin={pin} deck={deck} />
                  )}
                </ArtistCard>
              </div>
              {playerVariant === 'MINI_BAR' && (
                <div className="artist-minibar">
                  <Player pin={pin} deck={deck} />
                </div>
              )}
            </>
          )}
          {playing && (
            <div className="artist-now" data-testid="playing-indicator">
              Зараз звучить · {pin.name}
              {playerVariant !== 'MINI_BAR' && (
                <small>{deck.state.clip?.attribution}</small>
              )}
            </div>
          )}
          <OriginNote />
        </>
      )}
    </section>
  )
}
function ProfileStudy({
  id,
  variant,
  data,
  pins,
  theme,
}: {
  id: string
  variant: string
  data: AtlasData
  pins: Pin[]
  theme: string
}) {
  const [pin, select] = useState(pins[0])
  const [showPage, setShowPage] = useState(true)
  const [limit, setLimit] = useState(false)
  const profile = useProfile(pin.slug)
  const choices = useMemo(
    () =>
      data.pins
        .filter((p) => data.profiles.has(p.slug))
        .sort((a, b) => a.name.localeCompare(b.name, 'uk')),
    [data],
  )
  return (
    <section className="artist-profile-study">
      <label className="artist-selector">
        Виконавець
        <select
          value={pin.id}
          onChange={(e) => {
            const next = choices.find((p) => p.id === e.target.value)
            if (next) {
              select(next)
              setShowPage(true)
              setLimit(false)
            }
          }}
        >
          {choices.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </label>
      {id === 'musician.page' ? (
        showPage ? (
          <MusicianProfile
            key={pin.id}
            slug={pin.slug}
            variant={variant}
            data={data}
            back={() => setShowPage(false)}
          />
        ) : (
          <div className="artist-profile-map" data-direction={theme}>
            {profile && <OriginMap data={data} profile={profile} />}
            <button onClick={() => setShowPage(true)}>
              Відкрити профіль · {pin.name}
            </button>
            <OriginNote />
          </div>
        )
      ) : profile ? (
        <article className={'artist-like-study ' + variant}>
          <Portrait key={pin.id} profile={profile} />
          <small>
            {profile.origin.placeName} ·{' '}
            {profile.kind === 'BAND' ? 'Гурт' : 'Музикант'}
          </small>
          <h2>{profile.name}</h2>
          <LikeButton
            id={pin.id}
            initial={profile.likesCount}
            variant={variant}
          />
          <p>Кожен дотик додає один лайк.</p>
          <details className="artist-settings">
            <summary>Умови надсилання</summary>
            <label>
              <input
                type="checkbox"
                checked={limit}
                onChange={(e) => {
                  setLimit(e.target.checked)
                  simulateLikeLimit(pin.id, e.target.checked)
                }}
              />
              Імітувати обмеження першого пакета (429)
            </label>
          </details>
        </article>
      ) : (
        <p role="status">Завантажуємо профіль…</p>
      )}
    </section>
  )
}
export function ArtistStudies({
  id,
  variant,
  data,
  theme,
}: {
  id: string
  variant: string
  data: AtlasData
  theme: string
}) {
  const pins = useMemo(() => pickPins(data, id === 'audio.player'), [data, id])
  if (!pins.length) return <p>У наборі немає доступних профілів.</p>
  return id === 'musician.page' || id === 'like.button' ? (
    <ProfileStudy
      key={id + variant}
      id={id}
      variant={variant}
      data={data}
      pins={pins}
      theme={theme}
    />
  ) : (
    <ArtistMapStudy
      key={id + variant}
      id={id}
      variant={variant}
      data={data}
      theme={theme}
      pins={pins}
    />
  )
}
