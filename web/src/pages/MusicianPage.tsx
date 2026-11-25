import { useParams } from 'react-router-dom'
import { ApiProblem } from '../api/client'
import { useMusician } from '../api/queries'
import { useDesign } from '../core/design/config'
import { useCopy } from '../core/i18n/copy'
import { AppShell } from '../ui/layout/AppShell'
import type { MusicianPageSkinProps } from '../ui/musician/variants/EditorialPage'
import { resolveVariant } from '../ui/variants'
import NotFoundPage from './NotFoundPage'
import '../ui/musician/musician.css'

const DESCRIPTION_MAX = 160

const shorten = (text: string) => (text.length > DESCRIPTION_MAX ? `${text.slice(0, DESCRIPTION_MAX - 1).trimEnd()}…` : text)

export default function MusicianPage() {
  const t = useCopy()
  const { slug = '' } = useParams()
  const { data, error } = useMusician(slug)
  const Page = resolveVariant<MusicianPageSkinProps>('musician.page', useDesign())

  if (error instanceof ApiProblem && error.status === 404) return <NotFoundPage />
  const description = data && (data.summary ?? data.description ?? data.tagline)
  return (
    <AppShell>
      <div className="n-musician">
        {data && (
          <>
            <title>{`${data.name} — ${t('app.name')}`}</title>
            {description && <meta name="description" content={shorten(description)} />}
            {Page && <Page musician={data} />}
          </>
        )}
        {error && (
          <p role="alert" className="n-musician__page">
            {t(error instanceof ApiProblem && error.status === 0 ? 'errors.network' : 'errors.generic')}
          </p>
        )}
      </div>
    </AppShell>
  )
}
