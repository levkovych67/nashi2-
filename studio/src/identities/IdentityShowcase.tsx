import { useState } from 'react'
import { contrastRatio } from '../tokens/tokens'
import { IDENTITIES } from './index'
const assets = import.meta.glob<string>('./*/assets/logo.svg', {
  eager: true,
  query: '?url',
  import: 'default',
})
const boards = import.meta.glob<string>('./*/moodboard/*.webp', {
  eager: true,
  query: '?url',
  import: 'default',
})
const trees = import.meta.glob<{ color: Record<string, { $value: string }> }>(
  './*/tokens.json',
  { eager: true, import: 'default' },
)

export function IdentityShowcase({ id }: { id: string }) {
  const [liked, setLiked] = useState(false)
  const identity = IDENTITIES.find((entry) => entry.id === id)
  if (!identity) return null
  const colors = trees['./' + id + '/tokens.json'].color
  return (
    <section className="identity-sheet">
      <h2>{identity.title}</h2>
      <img
        className="identity-wordmark"
        src={assets['./' + id + '/assets/logo.svg']}
        alt={'Логотип «Наші» — ' + identity.title}
      />
      <p>{identity.concept}</p>
      <figure className="identity-moodboard">
        <img
          src={boards['./' + id + '/moodboard/study.webp']}
          alt={identity.moodboardAlt}
        />
        <figcaption>{identity.moodboardCredits.join(' ')}</figcaption>
      </figure>
      <div className="swatches">
        {[
          ['bg', 'Тло'],
          ['text', 'Текст'],
          ['accent', 'Акцент'],
          ['like', 'Лайк'],
        ].map(([key, title]) => (
          <div key={key}>
            <i style={{ backgroundColor: colors[key].$value }} />
            {title}
            <small>{colors[key].$value}</small>
          </div>
        ))}
      </div>
      <p>
        Контраст тексту на тлі —{' '}
        {contrastRatio(colors.text.$value, colors.bg.$value).toFixed(1)}:1.
      </p>
      <h3>Зразок шрифту</h3>
      <blockquote>
        Чуєш їх, доцю, га? Кумедна ж ти, прощайся без ґольфів!
      </blockquote>
      <p>
        {identity.fonts
          .map((font) => font.family)
          .filter((name, index, list) => list.indexOf(name) === index)
          .join(' · ')}
      </p>
      <h3>Голос проєкту</h3>
      <p>{identity.voice.examples[0]}</p>
      <div className="identity-markers">
        <span aria-label="Позначка музиканта">●</span>
        <span aria-label="Кількість музикантів у місті">24</span>
      </div>
      <label>
        Пошук музиканта <input placeholder="Ім’я або назва гурту" />
      </label>
      <button
        type="button"
        aria-pressed={liked}
        onClick={() => setLiked(!liked)}
      >
        {liked ? '♥' : '♡'} Лайк
      </button>
    </section>
  )
}
