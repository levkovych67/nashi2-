import type { components } from '../../api/types.gen'
import { useCopy } from '../../core/i18n/copy'
import { groupLinks, isSafeHttpUrl } from '../../core/musician/view'
import { ExternalLink, hostOf } from './ExternalLink'
import { MusicianAudio } from './MusicianAudio'
import { YouTubeEmbed } from './YouTubeEmbed'
import './musician.css'

type Musician = components['schemas']['MusicianDetails']

/** Fragment player, clips, works, and the video block (embeds load on click; the YouTube channel and other video links). */
export function MaterialsSection({ musician }: { musician: Musician }) {
  const t = useCopy()
  const { clips, works, videos } = musician.materials
  const channel = isSafeHttpUrl(musician.youtubeChannelUrl) ? musician.youtubeChannelUrl : null
  const videoLinks = groupLinks(musician.links).VIDEO.filter((l) => l.url !== channel)
  const hasVideoBlock = videos.length > 0 || channel !== null || videoLinks.some((l) => isSafeHttpUrl(l.url))
  return (
    <section className="n-musician__section" data-testid="musician-materials" aria-labelledby="musician-materials-title">
      <h2 id="musician-materials-title">{t('musician.materials')}</h2>
      <MusicianAudio musicianId={musician.id} hasAudio={musician.hasAudio} />
      {clips.length > 0 && (
        <ul className="n-musician__list">
          {clips.map((c) => (
            <li key={c.clipId}>
              <strong>{c.title}</strong> · {t('musician.clipDuration', { sec: c.durationSec })}
            </li>
          ))}
        </ul>
      )}
      {works.length > 0 && (
        <>
          <h3>{t('musician.works')}</h3>
          <ul className="n-musician__list">
            {works.map((w, i) => (
              <li key={`${w.title}-${i}`}>
                {isSafeHttpUrl(w.url) ? <ExternalLink href={w.url}>{w.title}</ExternalLink> : w.title}
                {w.year ? ` · ${w.year}` : ''}
              </li>
            ))}
          </ul>
        </>
      )}
      {hasVideoBlock && (
        <div data-testid="musician-youtube">
          <h3>{t('musician.videos')}</h3>
          {videos.map((v) => (
            <YouTubeEmbed key={v.youtubeId} youtubeId={v.youtubeId} title={v.title} />
          ))}
          <ul className="n-musician__links">
            {channel && (
              <li>
                <ExternalLink href={channel}>{t('musician.youtubeChannel')}</ExternalLink>
              </li>
            )}
            {videoLinks.map((l) => (
              <li key={l.url}>
                <ExternalLink href={l.url}>{l.platform === 'OTHER' ? hostOf(l.url) : t(`musician.platform.${l.platform}`)}</ExternalLink>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
