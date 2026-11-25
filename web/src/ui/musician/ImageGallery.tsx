import { useState } from 'react'
import type { components } from '../../api/types.gen'
import { useCopy } from '../../core/i18n/copy'
import { initials } from '../map/variants/AvatarRing'
import { PhotoCredit } from './PhotoCredit'
import './musician.css'

type Image = components['schemas']['Image']

/** Portrait of the musician with the photo credit; several photos get a strip of thumbnails; none → initials. */
export function ImageGallery({ images, name }: { images: Image[]; name: string }) {
  const t = useCopy()
  const [index, setIndex] = useState(0)
  const [broken, setBroken] = useState<Set<string>>(() => new Set())
  const current = images[Math.min(index, images.length - 1)]
  const showImage = current && !broken.has(current.url)
  return (
    <div className="n-musician__gallery">
      <figure className="n-musician__portrait" data-testid="musician-image">
        {showImage ? (
          <img
            src={current.url}
            alt={current.alt || name}
            decoding="async"
            referrerPolicy="no-referrer"
            onError={() => setBroken((prev) => new Set(prev).add(current.url))}
          />
        ) : (
          <span className="n-musician__monogram" aria-hidden="true">
            {initials(name)}
          </span>
        )}
      </figure>
      {showImage && <PhotoCredit credit={current.credit} />}
      {images.length > 1 && (
        <ul className="n-musician__thumbs">
          {images.map((image, i) => (
            <li key={image.url}>
              <button type="button" aria-pressed={i === index} aria-label={t('musician.photoNumber', { n: i + 1 })} onClick={() => setIndex(i)}>
                <img src={image.thumbUrl} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
