import { DECISIONS } from '../decisions/catalog'
import { variants } from '../decisions/variants'
import { FINDINGS } from '../audit/findings'
import { defaultVariant, type DecisionFile } from './review'

const behavior: Record<string, string> = {
  identity:
    'Перевірте обкладинку, карту, профіль та форму на мобільному і широкому екранах. Дизайн-токени, локальні шрифти й знаки додано до пакета.',
  'map.basemap':
    'Стиль MapLibre містить території, воду, дороги, підписи місць і межі України. Підставте адресу TileJSON та шаблон гліфів під час конфігурації продукту.',
  'map.initial-view':
    'Камера охоплює межі України. Запит координат потребує явної дії користувача. Відмова дозволу зберігає доступ до карти.',
  'map.aggregation':
    'Пороги за замовчуванням становлять 7 та 10. Вибір області або населеного пункту наближає карту. Кількість у кластері є сумою музикантів.',
  'map.pin':
    'Пін доступний із клавіатури, має ім’я виконавця й індикатор аудіо. Порожній портрет замінюють ініціали.',
  'map.cluster':
    'Кількість відображається на маркері. Натискання відкриває наступний рівень географічної деталізації.',
  'map.origin-notice':
    'Пояснення походження постійно доступне на карті, у картці та у профілі музиканта.',
  'audio.mobile-trigger':
    'Одночасно звучить один локальний фрагмент. Перемикання виконавця зупиняє попередній запис. Утримання для LONG_PRESS_PLAY триває 400 мс.',
  'audio.desktop-hover':
    'Наведення запускає фрагмент після затримки 250 мс. Вихід за межі позначки завершує прослуховування.',
  'audio.player':
    'Керування охоплює запуск, паузу, продовження та зміну доступного фрагмента. Підпис містить джерело аудіо.',
  'musician.preview':
    'Картка зберігає контекст карти, відкриває профіль і містить назву та походження. Передбачено закриття з клавіатури.',
  'musician.page':
    'Профіль містить біографію, місце походження, матеріали, посилання й атрибуцію фото та тексту. Повернення відновлює контекст карти.',
  'like.button':
    'Кожне натискання збільшує локальний лічильник. Пакетна фіксація враховує повторні натискання та обмеження частоти.',
  'filters.region':
    'Кілька областей можна поєднати. Застосування оновлює кількість виконавців, скидання відновлює повну вибірку.',
  'submission.form':
    'Контракт 2.10. Валідація на blur і при надсиланні, географічне автодоповнення, 1–5 фото JPEG/PNG/WebP по 10 МБ, локальне зменшення до 2048 px. Після помилки дані збережено для повторної спроби.',
  'nav.structure':
    'З карти доступні список і форма. Повернення дає продовжити перегляд виконавців.',
  onboarding:
    'Початковий перегляд використовує обраний варіант. Пояснення походження лишається доступним після проходження підказок.',
}
export function handoffMarkdown(file: DecisionFile, screenFiles: string[]) {
  const lines = [
    '# Передача дизайну «Наші V2»',
    '',
    'Згенеровано ' +
      file.generatedAt +
      '. Версія студії `' +
      file.studioCommit +
      '`.',
    '',
    '| Рішення | Варіант | Стан |',
    '| --- | --- | --- |',
  ]
  const pending: string[] = [],
    changes: string[] = []
  for (const { id } of DECISIONS) {
    const e = file.decisions[id]
    lines.push(
      '| ' +
        id +
        ' | ' +
        e.variant +
        ' | ' +
        (e.status === 'APPROVED' ? 'Затверджено' : 'Запропоновано') +
        ' |',
    )
    if (e.status !== 'APPROVED') pending.push('⚠️ ' + id + ' — НЕ ЗАТВЕРДЖЕНО')
    if (e.variant !== defaultVariant(id) || Object.keys(e.params ?? {}).length)
      changes.push(
        id +
          ' · ' +
          defaultVariant(id) +
          ' / обрано ' +
          e.variant +
          (e.params ? ' · ' + JSON.stringify(e.params) : ''),
      )
  }
  lines.push('', '## Готовність до передачі', '')
  lines.push(
    ...(pending.length
      ? [
          'Це пакет пропозицій. Продукт застосовує погоджені рішення за розділом 7 контрактів.',
          '',
          ...pending.map((s) => '- ' + s),
        ]
      : ['Усі 17 рішень мають зафіксоване погодження.']),
  )
  lines.push(
    '',
    '## Відмінності від початкової конфігурації',
    '',
    ...(changes.length
      ? changes.map((s) => '- ' + s)
      : ['Вибрано початкові варіанти без додаткових параметрів.']),
  )
  for (const decision of DECISIONS) {
    const { id } = decision,
      entry = file.decisions[id]
    const variant = variants[id].find((v) => v.id === entry.variant)!
    lines.push(
      '',
      '## ' + id,
      '',
      '### ' + decision.title,
      '',
      '**' + variant.name + '** · технічний ID `' + variant.id + '`',
      '',
      variant.description,
      '',
      behavior[id],
      '',
      'Параметри `' + JSON.stringify(entry.params ?? {}) + '`.',
      '',
      'Перевага. ' + variant.plus,
      '',
      'Обмеження. ' + variant.minus,
    )
    if (entry.approvedBy)
      lines.push(
        '',
        'Погодження · ' + entry.approvedBy + ' · ' + entry.approvedAt,
      )
    if (entry.note) lines.push('', 'Примітка · ' + entry.note)
    const audit = FINDINGS.filter((finding) => finding.affects.includes(id))
    for (const finding of audit)
      lines.push('', finding.id + ' · ' + finding.observation)
    const shots = screenFiles.filter((path) =>
      path.startsWith('screens/' + id + '/'),
    )
    if (shots.length)
      lines.push(
        '',
        ...shots.map(
          (path) =>
            '- [' +
            (path.endsWith('--mobile.png') ? 'Телефон' : 'Комп’ютер') +
            '](' +
            path +
            ')',
        ),
      )
  }
  if (screenFiles.some((path) => path.startsWith('screens/flows/'))) {
    lines.push('', '## Наскрізні сценарії', '')
    for (const path of screenFiles.filter((path) =>
      path.startsWith('screens/flows/'),
    ))
      lines.push('- [' + path.slice(14, -4) + '](' + path + ')')
  } else lines.push('', 'Пакет зібрано без знімків екрана.')
  return lines.join('\n') + '\n'
}
