import { useState } from 'react'
import { useCopy } from '../../core/i18n/copy'
import { youtubeEmbedUrl } from '../../core/musician/view'
import './musician.css'

/** Nothing is requested from YouTube until the visitor presses play; then the privacy-friendly player loads. */
export function YouTubeEmbed({ youtubeId, title }: { youtubeId: string; title: string }) {
  const t = useCopy()
  const [open, setOpen] = useState(false)
  return (
    <div className="n-musician__video">
      {open ? (
        <iframe
          src={youtubeEmbedUrl(youtubeId)}
          title={title}
          allow="encrypted-media; picture-in-picture; fullscreen"
          allowFullScreen
          referrerPolicy="strict-origin-when-cross-origin"
        />
      ) : (
        <button type="button" onClick={() => setOpen(true)} aria-label={t('musician.playVideo', { title })}>
          <span aria-hidden="true">▶</span>
          <span className="n-musician__video-title">{title}</span>
        </button>
      )}
    </div>
  )
}
