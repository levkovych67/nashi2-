import { useParams } from 'react-router-dom'
import { ApiProblem } from '../api/client'
import { useMusician } from '../api/queries'
import { useDesign } from '../core/design/config'
import { useCopy } from '../core/i18n/copy'
import { loadErrorKey } from '../core/i18n/loadError'
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
  const { data, error, isPending, refetch } = useMusician(slug)
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
        {isPending && (
          <p role="status" className="n-musician__page n-musician__status" data-testid="page-loading">
            {t('common.loading')}
          </p>
        )}
        {error && (
          <div role="alert" className="n-musician__page n-musician__status" data-testid="page-error">
            <p>{t(loadErrorKey(error))}</p>
            <button type="button" className="n-musician__button n-musician__button--primary" onClick={() => void refetch()}>
              {t('errors.retry')}
            </button>
          </div>
        )}
      </div>
    </AppShell>
  )
}
