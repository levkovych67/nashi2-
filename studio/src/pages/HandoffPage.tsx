import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { DECISIONS } from '../decisions/catalog'
import { variants } from '../decisions/variants'
import { saveApprovals, useReviewed } from '../handoff/approvals'
import { useDraft } from '../selection/store'
import baseCopy from '../proto/copy/copy.uk.json'
import {
  inspectResources,
  localDate,
  type DecisionEntry,
  type DecisionFile,
} from '../handoff/review'
import '../handoff/review.css'

const tokenFiles = import.meta.glob('../identities/*/tokens.json', {
  eager: true,
  import: 'default',
})
const copyFiles = import.meta.glob('../identities/*/copy.uk.json', {
  eager: true,
  import: 'default',
})

function download(file: DecisionFile) {
  const data = { ...file, generatedAt: new Date().toISOString() }
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(data, null, 2) + '\n'], {
      type: 'application/json',
    }),
  )
  const link = document.createElement('a')
  link.href = url
  link.download = 'decisions.json'
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function HandoffPage() {
  const draft = useDraft()
  const base = useReviewed(draft)
  const [chosen, setChosen] = useState<string[]>([])
  const [who, setWho] = useState('')
  const [note, setNote] = useState('')
  const [consent, setConsent] = useState(false)
  const [notice, setNotice] = useState('')
  const file = base.file
  const identity = file.decisions.identity.variant
  const checks = useMemo(
    () =>
      inspectResources(
        identity,
        tokenFiles['../identities/' + identity + '/tokens.json'],
        baseCopy,
        copyFiles['../identities/' + identity + '/copy.uk.json'],
      ),
    [identity],
  )
  const errors = [...base.errors, ...checks.errors]
  const pending = DECISIONS.filter(
    (d) => file.decisions[d.id].status !== 'APPROVED',
  )
  const selected = chosen.filter((key) => pending.some((d) => d.id === key))
  const readyToSign =
    selected.length > 0 && !!who.trim() && consent && !errors.length
  function approve() {
    if (!readyToSign) return
    const next: Record<string, DecisionEntry> = {}
    const day = localDate(new Date())
    for (const id of selected) {
      next[id] = {
        ...file.decisions[id],
        status: 'APPROVED',
        approvedAt: day,
        approvedBy: who.trim(),
        note: note.trim() || undefined,
      }
    }
    setNotice(
      'Затверджено рішень у локальному протоколі — ' +
        selected.length +
        '. Завантажте файл для передачі дизайн-ліду.',
    )
    if (!saveApprovals(next))
      setNotice(
        'Погодження діє у цій вкладці. Завантажте файл перед закриттям.',
      )
    setChosen([])
    setConsent(false)
    setNote('')
  }
  return (
    <main className="studio-page handoff-page">
      <h1>Передача рішень</h1>
      <p className="studio-lead">
        Затверджено {DECISIONS.length - pending.length} із {DECISIONS.length}{' '}
        рішень. Перевірте варіанти та параметри перед фіксацією у протоколі.
      </p>
      <section className="handoff-readiness">
        <h2>Що бракує до передачі</h2>
        {pending.length > 0 ? (
          <p>
            Очікують погодження — {pending.length}. Для порожніх позицій
            показано пропозицію за замовчуванням.
          </p>
        ) : (
          <p>
            Усі 17 рішень погоджено. Файл можна передати на перевірку
            дизайн-ліду.
          </p>
        )}
        {errors.length > 0 ? (
          <ul>
            {errors.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        ) : (
          <p>
            Токени, контраст і українські тексти обраного напряму пройшли
            перевірку.
          </p>
        )}
      </section>
      <div className="handoff-ledger">
        <table className="studio-ledger">
          <caption className="studio-visually-hidden">
            Стан погодження каталогу рішень
          </caption>
          <thead>
            <tr>
              <th scope="col">Рішення та варіант</th>
              <th scope="col">Стан і параметри</th>
              <th scope="col">До погодження</th>
            </tr>
          </thead>
          <tbody>
            {DECISIONS.map((d) => {
              const entry = file.decisions[d.id]
              const approved = entry.status === 'APPROVED'
              return (
                <tr key={d.id}>
                  <th scope="row">
                    <Link to={'/d/' + d.id}>{d.title}</Link>
                    <small>
                      {variants[d.id].find((v) => v.id === entry.variant)?.name}{' '}
                      · <code>{entry.variant}</code>
                    </small>
                  </th>
                  <td>
                    {approved ? 'Затверджено' : 'Запропоновано'}
                    {approved && (
                      <small>
                        {entry.approvedBy} · {entry.approvedAt}
                      </small>
                    )}
                    {entry.note && <small>{entry.note}</small>}
                    {entry.params && (
                      <small>
                        <code>{JSON.stringify(entry.params)}</code>
                      </small>
                    )}
                  </td>
                  <td>
                    {approved ? (
                      'Погоджено'
                    ) : (
                      <label>
                        <input
                          aria-label={'До погодження · ' + d.title}
                          type="checkbox"
                          checked={chosen.includes(d.id)}
                          onChange={(e) => {
                            setChosen(
                              e.target.checked
                                ? [...chosen, d.id]
                                : chosen.filter((key) => key !== d.id),
                            )
                            setConsent(false)
                          }}
                        />
                      </label>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {pending.length > 0 && (
        <section className="handoff-sign">
          <h2>Зафіксувати погодження</h2>
          <p>
            Позначте потрібні рішення у таблиці. Ім’я, дата та примітка увійдуть
            до експортованого файла.
          </p>
          <button
            type="button"
            onClick={() => {
              setChosen(
                selected.length === pending.length
                  ? []
                  : pending.map((d) => d.id),
              )
              setConsent(false)
            }}
          >
            {selected.length === pending.length
              ? 'Зняти всі позначки'
              : 'Позначити всі запропоновані'}
          </button>
          <label>
            Хто погоджує
            <input
              value={who}
              autoComplete="name"
              onChange={(e) => {
                setWho(e.target.value)
                setConsent(false)
              }}
              placeholder="Ім’я або роль учасника"
            />
          </label>
          <label>
            Примітка або протокол сесії
            <input value={note} onChange={(e) => setNote(e.target.value)} />
          </label>
          <label className="handoff-consent">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
            />
            Підтверджую свій вибір і погоджую позначені рішення (
            {selected.length}).
          </label>
          <button
            type="button"
            className="studio-action"
            disabled={!readyToSign}
            onClick={approve}
          >
            Затвердити позначені рішення
          </button>
        </section>
      )}
      <section className="handoff-export">
        <h2>Експорт</h2>
        <p>
          Завантажте локальний протокол і передайте дизайн-ліду для перегляду в
          репозиторії. Статуси кожного рішення зберігаються у файлі.
        </p>
        <button
          disabled={!!base.errors.length}
          onClick={() => {
            download(file)
            setNotice('Файл decisions.json підготовлено до завантаження.')
          }}
        >
          Завантажити decisions.json
        </button>
        {notice && <p role="status">{notice}</p>}
        <p>
          Після внесення файла до <code>studio/decisions/</code> зберіть пакет
          командою <code>npm run handoff -w studio</code>.
        </p>
      </section>
    </main>
  )
}
