import type { components } from '../../../api/types.gen'
import { ImageGallery } from '../ImageGallery'
import { LikeButton } from '../LikeButton'
import { LinksSection } from '../LinksSection'
import { MaterialsSection } from '../MaterialsSection'
import { MusicianHeader } from '../MusicianHeader'
import { OriginSection } from '../OriginSection'
import { TextSourceNote } from '../TextSourceNote'
import '../musician.css'

export interface MusicianPageSkinProps {
  musician: components['schemas']['MusicianDetails']
}

/** musician.page = EDITORIAL: the name and the biography open the page, the portrait stands beside them. */
export function EditorialPage({ musician }: MusicianPageSkinProps) {
  const text = musician.description ?? musician.summary
  return (
    <article className="n-musician__page">
      <div className="n-musician__lead">
        <div className="n-musician__text">
          <MusicianHeader musician={musician} />
          {text && (
            <p className="n-musician__summary" data-testid="musician-summary">
              {text}
            </p>
          )}
          <TextSourceNote source={musician.textSource} />
        </div>
        <ImageGallery images={musician.images} name={musician.name} />
      </div>
      <LikeButton musicianId={musician.id} serverCount={musician.likesCount} />
      <div className="n-musician__columns">
        {musician.origin && <OriginSection origin={musician.origin} />}
        <MaterialsSection musician={musician} />
      </div>
      <div className="n-musician__columns">
        <LinksSection links={musician.links} />
      </div>
    </article>
  )
}
