import { useEffect, useId, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AtlasPlate, OriginNote } from '../workbench/AtlasPlate'
import { type AtlasData, useAtlasData } from '../workbench/data'
import { LikeButton } from '../workbench/LikeButton'
import { simulateLikeLimit } from '../workbench/likes'
import { SampleButton } from '../workbench/SampleButton'
import { useDraft } from '../selection/store'
import { t } from '../proto/copy/t'
const states = [
  ['loading', 'Завантаження'],
  ['empty', 'Порожній список'],
  ['tiles', 'Підкладка недоступна'],
  ['api', 'Помилка сервера'],
  ['offline', 'Офлайн'],
  ['missing', 'Профіль не знайдено'],
  ['rate', 'Повтор лайків'],
  ['audio', 'Звук заблоковано'],
]
function RateExample() {
  const id = useId()
  useEffect(() => {
    simulateLikeLimit(id, true)
    return () => simulateLikeLimit(id, false)
  }, [id])
  return (
    <section>
      <p>
        Поставте кілька лайків поспіль. Перший запит у цій демонстрації отримує
        відповідь 429, а студія повторює його через дві секунди.
      </p>
      <LikeButton id={id} initial={0} />
    </section>
  )
}
function Example({ state, data }: { state: string; data: AtlasData }) {
  const [recovered, setRecovered] = useState(false)
  if (recovered)
    return (
      <section>
        <AtlasPlate data={data} />
        <OriginNote />
      </section>
    )
  if (state === 'loading')
    return (
      <section aria-busy="true">
        <p role="status">Завантажуємо карту…</p>
        <div className="state-skeleton" aria-hidden="true">
          <i />
          <i />
          <i />
        </div>
        <button onClick={() => setRecovered(true)}>
          Завершити завантаження
        </button>
      </section>
    )
  if (state === 'tiles')
    return (
      <section>
        <p>Показано локальні межі регіонів.</p>
        <AtlasPlate data={data} numerals />
        <OriginNote />
      </section>
    )
  if (state === 'audio') {
    const artist =
      data.pins.find((p) => p.slug === 'okean-elzy' && p.hasAudio) ??
      data.pins.find((p) => p.hasAudio)
    return (
      <section>
        <p>{t('map.soundBlockedHint')}</p>
        {artist ? <SampleButton id={artist.id} /> : <p>{t('pin.noAudio')}</p>}
      </section>
    )
  }
  if (state === 'rate') return <RateExample />
  const messages: Record<string, [string, string]> = {
    empty: [t('list.empty'), 'Скинути фільтри'],
    api: [t('errors.generic'), 'Повторити'],
    offline: [t('errors.network'), 'Відновити з’єднання'],
    missing: [t('errors.notFound'), 'Перейти до карти'],
  }
  const [message, action] = messages[state] ?? messages.api
  return (
    <section>
      <p role="status">{message}</p>
      <button onClick={() => setRecovered(true)}>{action}</button>
    </section>
  )
}
export function StatesGallery() {
  const [query, setQuery] = useSearchParams()
  const current =
    states.find(([key]) => key === query.get('state')) || states[0]
  const { data, error, retry } = useAtlasData(current[0] === 'audio')
  const draft = useDraft()
  return (
    <main className="studio-page">
      <h1>Стани інтерфейсу</h1>
      <label className="state-picker">
        Стан{' '}
        <select
          value={current[0]}
          onChange={(e) => setQuery({ state: e.target.value })}
        >
          {states.map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
      </label>
      <div
        className="proto state-gallery"
        data-direction={draft.identity?.variant || 'atlas'}
        data-identity={draft.identity?.variant || 'atlas'}
      >
        <div className="proto-body">
          <h2>{current[1]}</h2>
          {data ? (
            <Example state={current[0]} data={data} key={current[0]} />
          ) : error ? (
            <button onClick={retry}>Спробувати ще раз</button>
          ) : (
            <p>Завантажуємо дані…</p>
          )}
        </div>
      </div>
    </main>
  )
}
