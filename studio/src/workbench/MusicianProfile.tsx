import { useState } from 'react'
import { SampleButton } from './SampleButton'
import { OriginNote } from './AtlasPlate'
import { num } from './data'
import { useProfile, type Credit } from './profile'

export function PhotoCredit({ credit }: { credit: Credit }) {
  return (
    <span>
      <a href={credit.sourceUrl}>{credit.author || 'Wikimedia Commons'}</a> ·{' '}
      {credit.licenseUrl ? (
        <a href={credit.licenseUrl}>{credit.license}</a>
      ) : (
        credit.license
      )}
    </span>
  )
}
export function MusicianProfile({
  slug,
  back,
}: {
  slug: string
  back: () => void
}) {
  const profile = useProfile(slug)
  const [extraLikes, setExtraLikes] = useState(0)
  const [broken, setBroken] = useState(false)
  const photo = profile?.images[0]
  return (
    <section className="musician-study">
      <button className="study-back" onClick={back}>
        ← Повернутися на карту
      </button>
      {!profile ? (
        <p role="status">
          {profile === null
            ? 'Не вдалося відкрити профіль.'
            : 'Завантажуємо профіль…'}
        </p>
      ) : (
        <>
          <header>
            <small>{profile.kind === 'BAND' ? 'Гурт' : 'Музикант'}</small>
            <h2>{profile.name}</h2>
          </header>
          {photo && !broken && (
            <figure>
              <img
                src={photo.url}
                alt={photo.alt}
                onError={() => setBroken(true)}
              />
              {photo.credit && (
                <figcaption>
                  <PhotoCredit credit={photo.credit} />
                </figcaption>
              )}
            </figure>
          )}
          <h3>
            {profile.origin.basis === 'FORMATION'
              ? 'Місце заснування'
              : 'Місце народження'}
          </h3>
          <p>
            {profile.origin.placeName} · {profile.origin.regionName}
          </p>
          <OriginNote />
          <p>{profile.summary || profile.tagline}</p>
          {profile.textSource && (
            <p className="source-note">
              Текст із <a href={profile.textSource.url}>Вікіпедії</a> ·{' '}
              <a href={profile.textSource.licenseUrl}>
                {profile.textSource.license}
              </a>
            </p>
          )}
          {profile.materials.works.length > 0 && (
            <>
              <h3>Музичні матеріали</h3>
              <ul>
                {profile.materials.works.slice(0, 6).map((w, i) => (
                  <li key={i}>
                    {w.url ? <a href={w.url}>{w.title}</a> : w.title}
                    {w.year ? ' (' + w.year + ')' : ''}
                  </li>
                ))}
              </ul>
            </>
          )}
          {profile.links.some((l) => l.group === 'MUSIC') && (
            <>
              <h3>Слухати на платформах</h3>
              <div className="profile-links">
                {profile.links
                  .filter((l) => l.group === 'MUSIC')
                  .map((l) => (
                    <a href={l.url} key={l.url}>
                      {l.platform.replaceAll('_', ' ')}
                    </a>
                  ))}
              </div>
            </>
          )}
          {profile.youtubeChannelUrl && (
            <p>
              <a href={profile.youtubeChannelUrl}>YouTube-канал ↗</a>
            </p>
          )}
          {profile.hasAudio ? (
            <SampleButton key={profile.id} id={profile.id} />
          ) : (
            <p>Фрагмента немає</p>
          )}
          <button
            className="like-proto"
            onClick={() => setExtraLikes((n) => n + 1)}
            aria-label={
              'Додати лайк. Зараз ' + num(profile.likesCount + extraLikes)
            }
          >
            ♡ <span>{num(profile.likesCount + extraLikes)}</span>
          </button>
        </>
      )}
    </section>
  )
}
