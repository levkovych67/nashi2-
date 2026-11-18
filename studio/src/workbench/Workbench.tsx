import { lazy, Suspense, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { DECISIONS, byId } from '../decisions/catalog'
import { variants, type Variant } from '../decisions/variants'
import { choose, reset, useDraft } from '../selection/store'
import {
  decisionsFile,
  encodeSelection,
  recordedDecisions,
} from '../selection/selection'
import { DeviceFrame } from '../shell/DeviceFrame'
import { QrCode } from '../shell/QrCode'
import { variantUrl } from '../shell/urls'
import { MusicianProfile } from './MusicianProfile'
import { AtlasPlate, OriginNote } from './AtlasPlate'
import { type AtlasData, num, useAtlasData } from './data'
import './workbench.css'

const PinMap = lazy(() =>
  import('./PinMap').then((m) => ({ default: m.PinMap })),
)
const themes = variants.identity
const pretty = (id: string, variant: string) =>
  variants[id]?.find((v) => v.id === variant)?.name
function LoadState({ error, retry }: { error: boolean; retry: () => void }) {
  return (
    <div className="load-state" role="status">
      {error ? (
        <>
          <p>Не вдалося завантажити дані карти.</p>
          <button onClick={retry}>Спробувати ще раз</button>
        </>
      ) : (
        'Завантажуємо карту…'
      )}
    </div>
  )
}
function Tools() {
  const draft = useDraft()
  const [msg, setMsg] = useState('')
  const [manual, setManual] = useState('')
  const [confirm, setConfirm] = useState(false)
  const count = Object.keys(draft).length
  async function share() {
    const link = new URL('/', location.origin)
    link.searchParams.set('sel', encodeSelection(draft))
    try {
      await navigator.clipboard.writeText(link.href)
      setMsg('Посилання скопійовано')
      setManual('')
    } catch {
      setManual(link.href)
      setMsg('Скопіюйте посилання нижче')
    }
  }
  function download() {
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(decisionsFile(draft), null, 2) + '\n'], {
        type: 'application/json',
      }),
    )
    const a = document.createElement('a')
    a.href = url
    a.download = 'decisions.json'
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  return (
    <details className="draft-tools">
      <summary>
        Моя чернетка <b>{count}</b>
      </summary>
      <div className="tool-sheet">
        <p>
          {count
            ? 'Вибір збережено у цьому браузері.'
            : 'Оберіть варіант на сторінці порівняння.'}
        </p>
        {Object.entries(draft).map(([id, v]) => (
          <p key={id}>
            <Link to={'/d/' + id}>{byId(id)?.title}</Link> ·{' '}
            {pretty(id, v.variant)}
          </p>
        ))}
        <button disabled={!count} onClick={share}>
          Скопіювати посилання з вибором
        </button>
        <button disabled={!count} onClick={download}>
          Експортувати decisions.json
        </button>
        {confirm ? (
          <div>
            <button
              onClick={() => {
                reset()
                setConfirm(false)
                setManual('')
                setMsg('Чернетку скинуто')
              }}
            >
              Підтвердити скидання
            </button>
            <button onClick={() => setConfirm(false)}>Залишити</button>
          </div>
        ) : (
          <button disabled={!count} onClick={() => setConfirm(true)}>
            Скинути чернетку
          </button>
        )}
        {msg && <p role="status">{msg}</p>}
        {manual && (
          <input
            aria-label="Посилання на вибір"
            readOnly
            value={manual}
            onFocus={(e) => e.target.select()}
          />
        )}
      </div>
    </details>
  )
}
export function Overview() {
  const { data, error, retry } = useAtlasData()
  const [filter, setFilter] = useState('Усі рішення')
  const draft = useDraft()
  const groups = ['Усі рішення', ...new Set(DECISIONS.map((d) => d.group))]
  return (
    <main className="wb">
      <section className="wb-hero">
        <div className="hero-copy">
          <h1>Студія «Наші V2»</h1>
          <p className="display">
            Музична карта
            <br />
            України
          </p>
          <p className="hero-desc">
            Порівняйте, як карта виглядає у чотирьох візуальних напрямах.
            Вибрані варіанти можна зібрати в одну чернетку.
          </p>
          <Link className="ink-button" to="/d/identity">
            Порівняти напрями <span aria-hidden="true">↗</span>
          </Link>
        </div>
        <figure className="hero-plate">
          <figcaption>
            <span>Географія музикантів</span>
            <span>
              {data
                ? num(data.places.length) + ' населених пунктів'
                : 'Україна'}
            </span>
          </figcaption>
          {data ? (
            <AtlasPlate data={data} />
          ) : (
            <LoadState error={error} retry={retry} />
          )}
          <OriginNote />
        </figure>
      </section>
      <section className="catalog">
        <div className="catalog-head">
          <div>
            <h2>Каталог рішень</h2>
            <p>
              Затверджено{' '}
              {
                DECISIONS.filter(
                  (d) => recordedDecisions[d.id]?.status === 'APPROVED',
                ).length
              }{' '}
              з {DECISIONS.length}
            </p>
          </div>
          <Tools />
        </div>
        <nav className="group-filter" aria-label="Групи рішень">
          {groups.map((g) => (
            <button
              key={g}
              aria-pressed={filter === g}
              onClick={() => setFilter(g)}
            >
              {g}
            </button>
          ))}
        </nav>
        <div className="catalog-list">
          {DECISIONS.filter(
            (d) => filter === groups[0] || d.group === filter,
          ).map((d) => {
            const count = variants[d.id]?.length ?? 0
            const status =
              draft[d.id] &&
              draft[d.id].variant !== recordedDecisions[d.id]?.variant
                ? 'PROPOSED'
                : recordedDecisions[d.id]?.status ||
                  (draft[d.id] ? 'PROPOSED' : '')
            return (
              <Link to={'/d/' + d.id} key={d.id}>
                <div>
                  <small>{d.group}</small>
                  <strong>{d.title}</strong>
                </div>
                <span className="variant-count">
                  {count
                    ? count +
                      ' ' +
                      (count === 2 || count === 3 || count === 4
                        ? 'варіанти'
                        : 'варіантів')
                    : 'У плані'}
                </span>
                <span className={'status ' + (status ? 'picked' : '')}>
                  {status === 'APPROVED'
                    ? 'Затверджено'
                    : status === 'PROPOSED'
                      ? 'Запропоновано'
                      : 'Не обрано'}
                </span>
                <span aria-hidden="true">↗</span>
              </Link>
            )
          })}
        </div>
      </section>
    </main>
  )
}

function Prototype({
  data,
  id,
  variant,
  theme,
}: {
  data: AtlasData
  id: string
  variant: Variant
  theme: string
}) {
  const [selected, setSelected] = useState('')
  const [profileOpen, setProfileOpen] = useState(false)
  const region = data.regions.find((r) => r.code === selected)
  const identity = id === 'identity' ? variant.id : theme
  return (
    <div className="proto" data-direction={identity}>
      {profileOpen ? (
        <MusicianProfile slug="okean-elzy" back={() => setProfileOpen(false)} />
      ) : id === 'map.pin' ? (
        <Suspense fallback={<p>Завантажуємо позначки…</p>}>
          <PinMap data={data} theme={theme} variant={variant.id} />
        </Suspense>
      ) : (
        <>
          <div className="proto-head">
            <b>Наші</b>
            <span>Музична карта України</span>
          </div>
          <div className="proto-body">
            <h2>
              {id === 'identity' ? 'Музиканти на карті' : 'Області України'}
            </h2>
            <AtlasPlate
              data={data}
              regionCode={selected}
              numerals={id === 'map.cluster'}
              labels={variant.id === 'BUBBLE_COUNT_LABEL'}
              onRegion={(r) => setSelected(r.code)}
            />
            <label>
              Область або місто
              <select
                value={selected}
                onChange={(e) => setSelected(e.target.value)}
              >
                <option value="">Уся Україна</option>
                {data.regions.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.name}
                  </option>
                ))}
              </select>
            </label>
            <section className="region-detail" aria-live="polite">
              <h3>{region?.name || 'Уся Україна'}</h3>
              <p>
                Музикантів на карті{' '}
                <b>
                  {num(
                    region?.count ??
                      data.regions.reduce((sum, r) => sum + r.count, 0),
                  )}
                </b>
              </p>
              {region && (
                <ul>
                  {data.places
                    .filter((p) => p.region === selected)
                    .sort((a, b) => b.count - a.count)
                    .slice(0, 3)
                    .map((p) => (
                      <li key={p.placeId}>
                        {p.name} <b>{num(p.count)}</b>
                      </li>
                    ))}
                </ul>
              )}
            </section>
            {id === 'identity' && (
              <button
                className="study-profile"
                onClick={() => setProfileOpen(true)}
              >
                Профіль гурту «Океан Ельзи» ↗
              </button>
            )}
          </div>
          <OriginNote />
        </>
      )}
    </div>
  )
}

export function Comparison() {
  const { decisionId } = useParams()
  const decision = byId(decisionId)
  const draft = useDraft()
  const [device, setDevice] = useState<'mobile' | 'desktop'>('mobile')
  const [theme, setTheme] = useState(draft.identity?.variant || 'atlas')
  const [qr, setQr] = useState('')
  const { data, error, retry } = useAtlasData(decisionId === 'map.pin')
  if (!decision) return <Missing />
  const options = variants[decision.id] || []
  return (
    <main className="wb compare">
      <Link className="backlink" to="/">
        ← Каталог рішень
      </Link>
      <div className="compare-title">
        <div>
          <p>{decision.group}</p>
          <h1>{decision.title}</h1>
          <p>{decision.question}</p>
        </div>
        <Tools />
      </div>
      {options.length > 0 ? (
        <>
          <div className="compare-controls">
            <div aria-label="Розмір екрана">
              {(['mobile', 'desktop'] as const).map((d) => (
                <button
                  key={d}
                  aria-pressed={d === device}
                  onClick={() => setDevice(d)}
                >
                  {d === 'mobile' ? 'Телефон' : 'Комп’ютер'}
                </button>
              ))}
            </div>
            {decision.id !== 'identity' && (
              <label>
                Айдентика{' '}
                <select
                  value={theme}
                  onChange={(e) => setTheme(e.target.value)}
                >
                  {themes.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
          <div className="comparisons">
            {options.map((v) => {
              const direction = decision.id === 'identity' ? v.id : theme
              const url = variantUrl(
                location.origin,
                decision.id,
                v.id,
                direction,
              )
              const picked = draft[decision.id]?.variant === v.id
              return (
                <article className="option" key={v.id}>
                  <h2>{v.name}</h2>
                  <p>{v.description}</p>
                  <div className={'demo-box ' + device}>
                    <DeviceFrame device={device} identity={direction}>
                      {data ? (
                        <Prototype
                          data={data}
                          id={decision.id}
                          variant={v}
                          theme={direction}
                        />
                      ) : (
                        <LoadState error={error} retry={retry} />
                      )}
                    </DeviceFrame>
                  </div>
                  <dl>
                    <dt>Перевага</dt>
                    <dd>{v.plus}</dd>
                    <dt>Обмеження</dt>
                    <dd>{v.minus}</dd>
                  </dl>
                  <div className="choice-actions">
                    <button
                      className="ink-button"
                      aria-pressed={picked}
                      onClick={() => choose(decision.id, v.id)}
                    >
                      {picked ? '✓ Обрано' : 'Обрати'}
                    </button>
                    <a href={url}>На весь екран ↗</a>
                    <button
                      aria-expanded={qr === url}
                      onClick={() => setQr(qr === url ? '' : url)}
                    >
                      QR-код
                    </button>
                  </div>
                  {qr === url && (
                    <div className="qr-box">
                      <QrCode value={url} />
                      <p>
                        {['localhost', '127.0.0.1', '[::1]'].includes(
                          location.hostname,
                        )
                          ? 'Для телефону відкрийте студію за доступною з нього мережевою адресою.'
                          : 'Відскануйте камерою телефона'}
                      </p>
                    </div>
                  )}
                </article>
              )
            })}
          </div>
        </>
      ) : (
        <div className="planned">
          <h2>Прототипи в роботі</h2>
          <p>
            Заплановано варіантів для порівняння —{' '}
            {decision.techVariantIds.length}.
          </p>
          <Link to="/d/map.pin">Переглянути готові позначки ↗</Link>
        </div>
      )}
    </main>
  )
}

function Missing() {
  return (
    <main className="wb planned">
      <h1>Варіант не знайдено</h1>
      <Link to="/">До каталогу рішень</Link>
    </main>
  )
}
export function Fullscreen() {
  const { decisionId, variantId } = useParams()
  const [query] = useSearchParams()
  const option =
    decisionId && Object.hasOwn(variants, decisionId)
      ? variants[decisionId].find((v) => v.id === variantId)
      : undefined
  const theme =
    themes.find((t) => t.id === query.get('identity'))?.id || 'atlas'
  const { data, error, retry } = useAtlasData(decisionId === 'map.pin')
  if (!option || !decisionId) return <Missing />
  return (
    <main className="full-demo">
      {data ? (
        <Prototype data={data} id={decisionId} variant={option} theme={theme} />
      ) : (
        <LoadState error={error} retry={retry} />
      )}
    </main>
  )
}
