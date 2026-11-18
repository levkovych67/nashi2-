import { Link, useParams } from 'react-router-dom'

const scenarios = {
  'map-to-musician': {
    title: 'Від карти до музиканта',
    description: 'Обрати область і населений пункт, послухати фрагмент на карті, перейти до сторінки музиканта й повернутися до того самого місця.',
    steps: [
      { id: 'map.aggregation', title: 'Наблизити карту' },
      { id: 'audio.mobile-trigger', title: 'Послухати фрагмент' },
      { id: 'musician.page', title: 'Відкрити сторінку музиканта' },
    ],
  },
  'suggest-musician': {
    title: 'Запропонувати музиканта',
    description: 'Знайти форму, вказати ім’я та місце походження, додати посилання і зображення, перевірити дані й надіслати пропозицію.',
    steps: [
      { id: 'nav.structure', title: 'Знайти форму' },
      { id: 'submission.form', title: 'Заповнити й перевірити пропозицію' },
    ],
  },
}

export function FlowPage() {
  const { scenarioId } = useParams()
  const scenario = Object.hasOwn(scenarios, scenarioId ?? '') ? scenarios[scenarioId as keyof typeof scenarios] : null
  return (
    <main className="studio-page">
      <h1>Сценарії</h1>
      <nav className="studio-tabs" aria-label="Обрати сценарій">
        {Object.entries(scenarios).map(([id, item]) => <Link key={id} to={'/flow/' + id} aria-current={scenarioId === id ? 'page' : undefined}>{item.title}</Link>)}
      </nav>
      {scenario ? <section className="studio-prose">
        <p className="studio-aside">Наскрізний прототип у підготовці</p>
        <h2>{scenario.title}</h2>
        <p>{scenario.description}</p>
        <h3>Рішення для цього сценарію</h3>
        <ul className="studio-link-list">{scenario.steps.map(({id, title}) => <li key={id}><Link to={'/d/' + id}>{title} <span aria-hidden="true">↗</span></Link></li>)}</ul>
      </section> : <p role="status">Такого сценарію у студії немає. Оберіть один зі списку.</p>}
    </main>
  )
}
