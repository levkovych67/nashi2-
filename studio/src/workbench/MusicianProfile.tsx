import { t } from '../proto/copy/t'
import { useMemo, useState, type ReactNode } from 'react'
import { OriginNote } from './AtlasPlate'
import { type AtlasData, useAtlasData } from './data'
import { LikeButton } from './LikeButton'
import { PlaybackButton } from './SampleButton'
import { useSound } from './sound'
import {
  formatLifeDates,
  groupLinks,
  useProfile,
  type Credit,
  type Profile,
} from './profile'
import './ArtistStudies.css'

export function PhotoCredit({ credit }: { credit: Credit }) {
  return (
    <span data-testid="photo-credit">
      Фото ·{' '}
      <a href={credit.sourceUrl}>{credit.author || 'Wikimedia Commons'}</a> ·{' '}
      {credit.licenseUrl ? (
        <a href={credit.licenseUrl}>{credit.license}</a>
      ) : (
        credit.license
      )}
    </span>
  )
}
export function TextSource({ source }: { source: Profile['textSource'] }) {
  return (
    source && (
      <small className="artist-source" data-testid="text-source">
        Текст зі статті <a href={source.url}>«{source.title}» у Вікіпедії</a> ·{' '}
        <a href={source.licenseUrl}>{source.license}</a>
      </small>
    )
  )
}
export function Portrait({ profile }: { profile: Profile }) {
  const [index, setIndex] = useState(0)
  const [broken, setBroken] = useState<Set<string>>(new Set())
  const photo = profile.images[index]
  return (
    <figure className="artist-portrait">
      {photo && !broken.has(photo.url) ? (
        <>
          <img
            data-testid="musician-images"
            src={photo.url}
            alt={photo.alt}
            onError={() => setBroken((prev) => new Set([...prev, photo.url]))}
          />
          {photo.credit && (
            <figcaption>
              <PhotoCredit credit={photo.credit} />
            </figcaption>
          )}
        </>
      ) : (
        <div
          className="artist-monogram"
          data-testid="musician-image-fallback"
          aria-label={'Ініціали — ' + profile.name}
        >
          {profile.name
            .split(/\s+/)
            .slice(0, 2)
            .map((word) => word[0])
            .join('')}
        </div>
      )}
      {profile.images.length > 1 && (
        <button
          onClick={() => setIndex((i) => (i + 1) % profile.images.length)}
        >
          Наступне фото
        </button>
      )}
    </figure>
  )
}
const basisLabels: Record<string, string> = {
  FORMATION: 'Місце заснування',
  BIRTH: 'Місце народження',
  ORIGIN: 'Місце походження',
}
export function OriginMap({
  data,
  profile,
}: {
  data: AtlasData
  profile: Profile
}) {
  const paths = useMemo(
    () =>
      data.shapes.features.map((f) => {
        const polygons =
          f.geometry.type === 'MultiPolygon'
            ? f.geometry.coordinates
            : [f.geometry.coordinates]
        return {
          region: f.properties?.code,
          d: polygons
            .flatMap((poly) =>
              poly.map(
                (ring) =>
                  'M' +
                  ring
                    .map(
                      ([lng, lat]) => lng.toFixed(3) + ',' + (-lat).toFixed(3),
                    )
                    .join('L') +
                  'Z',
              ),
            )
            .join(''),
        }
      }),
    [data.shapes],
  )
  return (
    <div className="artist-origin-plate">
      <svg
        viewBox="21.5 0 20 11.7"
        role="img"
        aria-label={
          'Місце походження на карті України — ' + profile.origin.placeName
        }
      >
        <g transform="scale(1 1.52) translate(0 52.8)">
          {paths.map((p, i) => (
            <path
              key={i}
              d={p.d}
              fillRule="evenodd"
              fill={
                p.region === profile.origin.region
                  ? 'var(--point)'
                  : 'var(--land)'
              }
              stroke="var(--line)"
              strokeWidth=".035"
            />
          ))}
          <ellipse
            cx={profile.origin.lon}
            cy={-profile.origin.lat}
            rx=".28"
            ry=".184"
            fill="var(--mark)"
            stroke="var(--white)"
            strokeWidth=".07"
          />
        </g>
      </svg>
      <small>
        <a href="https://www.geoboundaries.org/">geoBoundaries</a>
      </small>
    </div>
  )
}
function ProfileSection({
  compact,
  title,
  children,
  testId,
}: {
  compact: boolean
  title: string
  children: ReactNode
  testId?: string
}) {
  return compact ? (
    <details className="artist-section" data-testid={testId}>
      <summary>{title}</summary>
      {children}
    </details>
  ) : (
    <section className="artist-section" data-testid={testId}>
      <h3>{title}</h3>
      {children}
    </section>
  )
}
function ProfileBody({
  profile,
  slug,
  variant,
  likeVariant,
  data,
}: {
  profile: Profile
  slug: string
  variant: string
  likeVariant: string
  data?: AtlasData
}) {
  const [expanded, setExpanded] = useState(false)
  const pins = useMemo(
    () => [
      {
        id: profile.id,
        slug: profile.partial ? undefined : slug,
        hasAudio: profile.hasAudio,
      },
    ],
    [profile, slug],
  )
  const deck = useSound(pins)
  const available = deck.lists[profile.id]
  const { data: fallback } = useAtlasData()
  const geography = data || fallback
  const compact = variant === 'COMPACT'
  const dates = formatLifeDates(profile.dates, profile.kind)
  const links = groupLinks(profile.links)
  const types: Record<string, string> = {
    MUSIC: t('musician.listenOn'),
    VIDEO: 'Відео',
    REFERENCE: 'Джерела',
    SOCIAL: 'Соцмережі',
  }
  const name = (
    <div className="artist-title">
      <small>
        {profile.kind === 'BAND' ? 'Гурт' : 'Музикант'}
        {dates && ' · ' + dates}
      </small>
      <h2 data-testid="musician-name">{profile.name}</h2>
      {profile.genres.length > 0 && <p>{profile.genres.join(' · ')}</p>}
    </div>
  )
  const bio = (
    <div data-testid="musician-summary">
      <p>
        {expanded
          ? profile.description
          : profile.summary || profile.tagline || 'Біографія готується.'}
      </p>
      {profile.description && profile.description !== profile.summary && (
        <button aria-expanded={expanded} onClick={() => setExpanded((v) => !v)}>
          {expanded ? 'Згорнути опис' : 'Читати далі'}
        </button>
      )}
      <TextSource source={profile.textSource} />
    </div>
  )
  return (
    <article className={'artist-profile ' + variant}>
      <header className="artist-cover">
        {variant === 'EDITORIAL' ? (
          <>
            <div>
              {name}
              {bio}
            </div>
            <Portrait profile={profile} />
          </>
        ) : (
          <>
            <Portrait profile={profile} />
            {name}
          </>
        )}
      </header>
      {profile.partial && <small>Ваші лайки у студії</small>}
      <LikeButton
        id={profile.id}
        initial={profile.likesCount}
        variant={likeVariant}
      />
      <div className="artist-profile-grid">
        {variant !== 'EDITORIAL' && (
          <ProfileSection title="Про виконавця" compact={compact}>
            {bio}
          </ProfileSection>
        )}
        <ProfileSection
          title={basisLabels[profile.origin.basis] || 'Місце походження'}
          compact={compact}
          testId="musician-origin"
        >
          <strong>{profile.origin.placeName}</strong>
          <p>{profile.origin.regionName}</p>
          {geography && <OriginMap profile={profile} data={geography} />}
          {profile.origin.precision === 'CITY' && (
            <small>{t('musician.precisionNote.CITY')}</small>
          )}
          <OriginNote />
        </ProfileSection>
        <ProfileSection
          title="Музичні матеріали"
          compact={compact}
          testId="musician-materials"
        >
          {available?.length ? (
            <div className="artist-tracks">
              {available.map((clip) => (
                <div key={clip.clipId}>
                  <strong>{clip.title}</strong>
                  <PlaybackButton deck={deck} pin={pins[0]} clip={clip} />
                </div>
              ))}
              <small>{available[0].attribution}</small>
            </div>
          ) : (
            <PlaybackButton deck={deck} pin={pins[0]} />
          )}
          {profile.materials.works.length > 0 && (
            <ul>
              {profile.materials.works.map((w, i) => (
                <li key={i}>
                  {w.url ? <a href={w.url}>{w.title}</a> : w.title}
                  {w.year && ` (${w.year})`}
                </li>
              ))}
            </ul>
          )}
          {profile.materials.videos.map((v, i) => (
            <p key={i}>
              <a href={v.url}>{v.title}</a>
            </p>
          ))}
        </ProfileSection>
        <ProfileSection
          title="Посилання"
          compact={compact}
          testId="musician-music-links"
        >
          {profile.youtubeChannelUrl && (
            <p className="artist-channel" data-testid="musician-youtube">
              <a href={profile.youtubeChannelUrl}>
                {t('musician.youtubeChannel')}
              </a>
            </p>
          )}
          {links.length ? (
            links.map((group) => (
              <div className="artist-link-group" key={group.group}>
                <h4>{types[group.group]}</h4>
                {group.links
                  .filter((l) => l.url !== profile.youtubeChannelUrl)
                  .map((l) => (
                    <a href={l.url} key={l.url}>
                      {l.label}
                    </a>
                  ))}
              </div>
            ))
          ) : (
            <p>Посилання ще не додані.</p>
          )}
        </ProfileSection>
      </div>
    </article>
  )
}
export function MusicianProfile({
  slug,
  back,
  variant = 'EDITORIAL',
  likeVariant = 'HEART_BURST',
  data,
  fallback,
}: {
  slug: string
  back: () => void
  variant?: string
  likeVariant?: string
  data?: AtlasData
  fallback?: Profile
}) {
  const profile = useProfile(slug, fallback)
  return (
    <section className="artist-profile-shell">
      <button type="button" className="study-back" onClick={back}>
        Повернутися на карту
      </button>
      {profile ? (
        <ProfileBody
          key={slug}
          slug={slug}
          profile={profile}
          variant={variant}
          likeVariant={likeVariant}
          data={data}
        />
      ) : (
        <p role="status">
          {profile === null
            ? 'Не вдалося відкрити профіль.'
            : 'Завантажуємо профіль…'}
        </p>
      )}
    </section>
  )
}
