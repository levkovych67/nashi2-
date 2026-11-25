import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMusician } from '../../../api/queries'
import type { components } from '../../../api/types.gen'
import { useCopy } from '../../../core/i18n/copy'
import { originLabel } from '../../../core/musician/view'
import { BottomSheet } from '../../layout/BottomSheet'
import { initials } from '../../map/variants/AvatarRing'
import { LikeButton } from '../LikeButton'
import { MusicianAudio } from '../MusicianAudio'
import '../musician.css'

type MapPin = components['schemas']['MapPin']

export interface PreviewSkinProps {
  pin: MapPin
  onClose(): void
}

/** musician.preview = BOTTOM_SHEET: the card rises from the bottom of the map and keeps the map in view. */
export function BottomSheetPreview({ pin, onClose }: PreviewSkinProps) {
  const t = useCopy()
  const musician = useMusician(pin.slug).data
  const [broken, setBroken] = useState<string | null>(null)
  const photo = musician?.images[0]?.thumbUrl ?? pin.thumbUrl
  const showPhoto = photo && broken !== photo
  const place = musician?.origin ? originLabel(musician.origin, t) : null
  return (
    <BottomSheet testId="musician-preview" label={pin.name} title={pin.name} onClose={onClose}>
      <div className="n-preview">
        <div className="n-preview__head">
          <figure className="n-preview__photo" data-testid="preview-photo">
            {showPhoto ? (
              <img src={photo} alt="" decoding="async" referrerPolicy="no-referrer" onError={() => setBroken(photo)} />
            ) : (
              <span aria-hidden="true">{initials(pin.name)}</span>
            )}
          </figure>
          <div className="n-preview__info">
            <p className="n-preview__eyebrow">{t(`musician.kind.${pin.kind}`)}</p>
            {place && <p className="n-preview__place">{place}</p>}
            {musician && <LikeButton musicianId={musician.id} serverCount={musician.likesCount} />}
          </div>
        </div>
        <MusicianAudio musicianId={pin.id} hasAudio={pin.hasAudio} />
        <Link className="n-musician__button" to={`/m/${encodeURIComponent(pin.slug)}`}>
          {t('preview.openProfile')}
        </Link>
        <p className="n-preview__notice" data-testid="preview-origin-notice">
          {t('map.originNotice')}
        </p>
      </div>
    </BottomSheet>
  )
}
