import { Link } from 'react-router-dom'
import { FINDINGS } from './findings'

export function DecisionEvidence({ id }: { id: string }) {
  const matches = FINDINGS.filter((f) => f.affects.includes(id))
  if (!matches.length) return null
  return (
    <details className="decision-evidence">
      <summary>Що ми бачили у V1 ({matches.length})</summary>
      <ul>
        {matches.map((f) => (
          <li key={f.id}>
            <Link to={'/audit#' + f.id}>
              {f.id} · {f.title}
            </Link>
          </li>
        ))}
      </ul>
    </details>
  )
}
