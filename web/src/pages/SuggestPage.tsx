import { useDesign } from '../core/design/config'
import { useCopy } from '../core/i18n/copy'
import { resolveVariant } from '../ui/variants'
import '../ui/suggest/suggest.css'

export default function SuggestPage() {
  const t = useCopy()
  const Form = resolveVariant<object>('submission.form', useDesign())
  return (
    <main className="n-suggest">
      <div className="n-suggest__page">
        <header>
          <h1>{t('submit.title')}</h1>
          <p>{t('submit.intro')}</p>
        </header>
        {Form && <Form />}
      </div>
    </main>
  )
}
