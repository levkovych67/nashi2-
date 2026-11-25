import { useParams } from 'react-router-dom'
import { useMusician } from '../api/queries'
import { useCopy } from '../core/i18n/copy'

export default function MusicianPage() {
  const t = useCopy()
  const { slug = '' } = useParams()
  const { data, isError } = useMusician(slug)
  return (
    <main>
      {data && <h1>{data.name}</h1>}
      {isError && <p role="alert">{t('errors.notFound')}</p>}
    </main>
  )
}
