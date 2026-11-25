import type { components } from '../../api/types.gen'
import { useCopy } from '../../core/i18n/copy'
import { groupLinks, isSafeHttpUrl } from '../../core/musician/view'
import { ExternalLink, hostOf } from './ExternalLink'
import './musician.css'

type Link = components['schemas']['Link']

/** Music platforms, reference sources and social links; unsafe addresses are dropped. */
export function LinksSection({ links }: { links: Link[] }) {
  const t = useCopy()
  const groups = groupLinks(links.filter((l) => isSafeHttpUrl(l.url)))
  const block = (title: string, items: Link[], testId?: string) =>
    items.length > 0 && (
      <section className="n-musician__section" data-testid={testId} aria-label={title}>
        <h2>{title}</h2>
        <ul className="n-musician__links">
          {items.map((l) => (
            <li key={l.url}>
              <ExternalLink href={l.url}>{l.platform === 'OTHER' ? hostOf(l.url) : t(`musician.platform.${l.platform}`)}</ExternalLink>
            </li>
          ))}
        </ul>
      </section>
    )
  return (
    <>
      {block(t('musician.listenOn'), groups.MUSIC, 'musician-music-links')}
      {block(t('musician.links.REFERENCE'), groups.REFERENCE)}
      {block(t('musician.links.SOCIAL'), groups.SOCIAL)}
    </>
  )
}
