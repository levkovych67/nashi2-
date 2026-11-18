import { Link } from 'react-router-dom'
import file from '../../decisions/decisions.json'
import { DECISIONS } from '../decisions/catalog'

type Entry = { status: 'APPROVED' | 'PROPOSED'; variant: string }
const decisions = file.decisions as Record<string, Entry>

export function HandoffPage() {
  const approved = DECISIONS.filter(({id}) => decisions[id]?.status === 'APPROVED').length
  return <main className="studio-page">
    <h1>Передача рішень</h1>
    <p className="studio-lead">Для технічної команди затверджено {approved} із {DECISIONS.length} рішень. Дизайн-лід фіксує погоджений вибір після перегляду з командою.</p>
    <table className="studio-ledger">
      <caption className="studio-visually-hidden">Стан погодження каталогу рішень</caption>
      <thead><tr><th scope="col">Рішення</th><th scope="col">Стан</th></tr></thead>
      <tbody>{DECISIONS.map(d => {
        const entry = decisions[d.id]
        return <tr key={d.id}>
          <th scope="row"><Link to={'/d/' + d.id}>{d.title}</Link>{entry && <small>{entry.variant}</small>}</th>
          <td>{entry?.status === 'APPROVED' ? 'Затверджено' : entry ? 'Запропоновано' : 'Очікує вибору'}</td>
        </tr>
      })}</tbody>
    </table>
  </main>
}
