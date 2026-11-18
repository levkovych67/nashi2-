import { useEffect } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import {
  areas,
  captures,
  FINDINGS,
  severities,
  sourceUrl,
} from '../audit/findings'
import { byId } from '../decisions/catalog'
import '../audit/audit.css'

export function AuditPage() {
  const [query, setQuery] = useSearchParams()
  const { hash } = useLocation()
  const area = query.get('area') || ''
  const severity = query.get('severity') || ''
  const matches = FINDINGS.filter(
    (f) => (!area || f.area === area) && (!severity || f.severity === severity),
  )
  useEffect(() => {
    if (hash)
      document
        .getElementById(hash.slice(1))
        ?.scrollIntoView?.({ block: 'start' })
  }, [hash, area, severity])
  function filter(key: string, value: string) {
    const next = new URLSearchParams(query)
    if (value) next.set(key, value)
    else next.delete(key)
    setQuery(next, { replace: true })
  }
  return (
    <main className="studio-page audit">
      <header>
        <h1>Аудит V1</h1>
        <p className="studio-lead">
          Як перша версія «Наших» показує митців на карті та які рішення це
          підказує для музичного атласу.
        </p>
        <p>
          Знімки локальної V1 зроблено 18 листопада 2026 року. Кожне
          спостереження має посилання на відповідний файл фронтенду.
        </p>
      </header>
      <div className="audit-controls">
        <label>
          Розділ{' '}
          <select value={area} onChange={(e) => filter('area', e.target.value)}>
            <option value="">Усі розділи</option>
            {Object.entries(areas).map(([key, title]) => (
              <option key={key} value={key}>
                {title}
              </option>
            ))}
          </select>
        </label>
        <label>
          Важливість{' '}
          <select
            value={severity}
            onChange={(e) => filter('severity', e.target.value)}
          >
            <option value="">Будь-яка</option>
            {Object.entries(severities).map(([key, title]) => (
              <option key={key} value={key}>
                {title}
              </option>
            ))}
          </select>
        </label>
        {(area || severity) && (
          <button onClick={() => setQuery({})}>Скинути</button>
        )}
        <span role="status">
          Знайдено {matches.length} із {FINDINGS.length}
        </span>
      </div>
      <div className="audit-findings">
        {matches.map((f) => (
          <article key={f.id} id={f.id}>
            <header>
              <small>
                {f.id} · {areas[f.area]} · Важливість{' '}
                {severities[f.severity].toLocaleLowerCase('uk')}
              </small>
              <h2>{f.title}</h2>
            </header>
            {f.evidence[0] && (
              <figure>
                <a
                  aria-label={'Відкрити знімок. ' + f.title}
                  href={f.evidence[0]}
                >
                  <img
                    loading="lazy"
                    src={f.evidence[0]}
                    alt={
                      captures[
                        f.evidence[0].split('/').pop()!.replace('.png', '')
                      ]
                    }
                  />
                </a>
              </figure>
            )}
            <p>{f.observation}</p>
            <p>
              <b>{f.keep ? 'Зберегти у V2' : 'Змінити у V2'}</b> ·{' '}
              {f.recommendation}
            </p>
            <p className="audit-sources">
              <a href={sourceUrl(f.v1Ref)}>
                Код V1 · {f.v1Ref.split('/').pop()?.split('#')[0]}
              </a>
              {f.evidence.slice(1).map((e) => (
                <a key={e} href={e}>
                  {captures[e.split('/').pop()!.replace('.png', '')]}
                </a>
              ))}
            </p>
            <nav aria-label={'Рішення для ' + f.id}>
              {f.affects.map((id) => (
                <Link to={'/d/' + id} key={id}>
                  {byId(id)?.title}
                </Link>
              ))}
            </nav>
          </article>
        ))}
      </div>
      {!matches.length && <p>Для цих умов спостережень немає.</p>}
    </main>
  )
}
