import {
  browserStorage,
  markOnboardingSeen,
  shouldShowOnboarding,
} from '../proto/onboarding'
import { useState } from 'react'
import { AtlasPlate, OriginNote } from './AtlasPlate'
import { MusicianProfile } from './MusicianProfile'
import { SubmissionStudy } from './SubmissionStudy'
import { useDraft } from '../selection/store'
import { recordedDecisions } from '../selection/selection'
import { num, type AtlasData, type Pin } from './data'
import './NavigationStudy.css'

const screens = {
  map: 'Карта',
  list: 'Список',
  suggest: 'Запропонувати',
  about: 'Про проєкт',
}
type Screen = keyof typeof screens
export function NavigationStudy({
  id,
  variant,
  data,
  onSuggest,
}: {
  id: string
  variant: string
  data: AtlasData
  onSuggest?: () => void
}) {
  const draft = useDraft()
  const choice = (key: string, fallback: string) =>
    draft[key]?.variant ?? recordedDecisions[key]?.variant ?? fallback
  const nav =
    id === 'nav.structure'
      ? variant
      : choice('nav.structure', 'MAP_ONLY_SHEETS')
  const guided = id === 'onboarding' && variant === 'FIRST_VISIT_TIPS'
  const [tip, setTip] = useState<number | null>(() =>
    guided && shouldShowOnboarding(browserStorage()) ? 0 : null,
  )
  const [screen, setScreen] = useState<Screen>('map')
  const [menu, setMenu] = useState(false)
  const [region, setRegion] = useState('')
  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(12)
  const [selected, setSelected] = useState<Pin | null>(null)
  const [profile, setProfile] = useState(false)
  const norm = (s: string) =>
    s
      .normalize('NFKD')
      .toLocaleLowerCase('uk')
      .replace(/[’'ʼ]|\u0301/gu, '')
  const results = data.pins.filter(
    (p) =>
      (!region || p.region === region) && norm(p.name).includes(norm(query)),
  )
  function open(v: Screen) {
    if (v === 'suggest' && onSuggest) {
      onSuggest()
      return
    }
    setScreen(v)
    setMenu(false)
    setSelected(null)
    setProfile(false)
  }
  const actions = (Object.keys(screens) as Screen[]).map((key) => (
    <button key={key} aria-pressed={screen === key} onClick={() => open(key)}>
      {screens[key]}
    </button>
  ))
  function dismiss() {
    setTip(null)
    markOnboardingSeen(browserStorage())
  }
  const tips = [
    [
      'Що показує карта',
      'Для людини позначено місце народження, для гурту — місце заснування.',
    ],
    [
      'Як слухати',
      choice('audio.mobile-trigger', 'TAP_PREVIEW_AND_PLAY') ===
      'LONG_PRESS_PLAY'
        ? 'Утримуйте позначку музиканта, щоб прослухати фрагмент.'
        : choice('audio.mobile-trigger', 'TAP_PREVIEW_AND_PLAY') ===
            'TAP_PREVIEW_PLAY_BUTTON'
          ? 'Відкрийте картку та натисніть кнопку відтворення.'
          : 'Торкніться позначки зі звуком, щоб послухати фрагмент.',
    ],
    [
      'Як додати музиканта',
      'У розділі «Запропонувати» вкажіть дані музиканта, джерело й додайте фото.',
    ],
  ]
  if (profile && selected)
    return (
      <MusicianProfile
        key={selected.slug}
        slug={selected.slug}
        back={() => setProfile(false)}
      />
    )
  return (
    <section className={'navigation-study nav-' + nav} data-screen={screen}>
      <header className="proto-head">
        <b>Наші</b>
        {nav === 'TOP_BAR_MENU' ? (
          <button onClick={() => setMenu(!menu)} aria-expanded={menu}>
            Меню
          </button>
        ) : (
          <span>Музична карта</span>
        )}
      </header>
      {nav === 'TOP_BAR_MENU' && menu && (
        <nav className="nav-drop" aria-label="Меню розділів">
          {actions}
        </nav>
      )}
      {(screen === 'map' || nav === 'MAP_ONLY_SHEETS') && (
        <div className="nav-map">
          <AtlasPlate
            data={data}
            numerals
            regionCode={region}
            onRegion={(r) => {
              setRegion(r.code)
              open('list')
            }}
          />
          <OriginNote />
          {nav === 'MAP_ONLY_SHEETS' && screen === 'map' && (
            <nav className="sheet-opener" aria-label="Дії на карті">
              <button onClick={() => open('list')}>Пошук і фільтри</button>
              <button onClick={() => open('suggest')}>Запропонувати</button>
              <button onClick={() => open('about')}>Про проєкт</button>
            </nav>
          )}
        </div>
      )}
      {screen !== 'map' && (
        <section className="nav-panel" aria-label={screens[screen]}>
          <header>
            <h2>{screens[screen]}</h2>
            <button onClick={() => open('map')}>
              {nav === 'MAP_ONLY_SHEETS' ? 'Закрити шторку' : 'До карти'}
            </button>
          </header>
          {screen === 'list' && (
            <>
              <label>
                Пошук музиканта
                <input
                  type="search"
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value)
                    setLimit(12)
                    setSelected(null)
                  }}
                />
              </label>
              <label>
                Регіон
                <select
                  value={region}
                  onChange={(e) => {
                    setRegion(e.target.value)
                    setLimit(12)
                    setSelected(null)
                  }}
                >
                  <option value="">Уся Україна</option>
                  {data.regions.map((r) => (
                    <option key={r.code} value={r.code}>
                      {r.name}
                    </option>
                  ))}
                </select>
              </label>
              <p role="status">Знайдено музикантів: {num(results.length)}</p>
              {selected ? (
                <div className="nav-artist">
                  <h3>{selected.name}</h3>
                  <p>
                    {
                      data.places.find((p) => p.placeId === selected.placeId)
                        ?.name
                    }
                  </p>
                  <OriginNote />
                  {data.profiles.has(selected.slug) && (
                    <button onClick={() => setProfile(true)}>
                      Відкрити профіль
                    </button>
                  )}
                  <button onClick={() => setSelected(null)}>
                    Назад до списку
                  </button>
                </div>
              ) : (
                <>
                  <ul>
                    {results.slice(0, limit).map((p) => (
                      <li key={p.id}>
                        <button onClick={() => setSelected(p)}>{p.name}</button>
                      </li>
                    ))}
                  </ul>
                  {limit < results.length && (
                    <button onClick={() => setLimit((n) => n + 12)}>
                      Показати ще
                    </button>
                  )}
                </>
              )}
            </>
          )}
          {screen === 'suggest' && (
            <SubmissionStudy
              data={data}
              variant={choice('submission.form', 'SINGLE_PAGE')}
            />
          )}
          {screen === 'about' && (
            <>
              <p>
                «Наші» — карта походження українських музикантів. Відкрийте
                область, знайдіть місто та познайомтеся з виконавцями.
              </p>
              <p>
                Біографії з Вікіпедії та фотографії з Wikimedia Commons мають
                посилання на джерело у профілі. У студії звучать синтетичні
                демонстраційні фрагменти.
              </p>
            </>
          )}
        </section>
      )}
      {nav === 'TAB_BAR' && (
        <nav className="bottom-tabs" aria-label="Вкладки">
          {actions}
        </nav>
      )}
      {guided && tip !== null && (
        <section className="first-tips" aria-label="Перше знайомство">
          <small>Підказка {tip + 1} з 3</small>
          <h2>{tips[tip][0]}</h2>
          <p>{tips[tip][1]}</p>
          <button onClick={() => (tip === 2 ? dismiss() : setTip(tip + 1))}>
            {tip === 2 ? 'Відкрити карту' : 'Далі'}
          </button>
          <button onClick={dismiss}>Пропустити</button>
        </section>
      )}
      {guided && tip === null && (
        <button className="tip-repeat" onClick={() => setTip(0)}>
          Показати підказки ще раз
        </button>
      )}
    </section>
  )
}
