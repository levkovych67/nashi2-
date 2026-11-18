export interface Decision {
  id: string
  group: string
  title: string
  question: string
  techVariantIds: readonly string[]
}

function decision(
  id: string,
  group: string,
  title: string,
  question: string,
  ...techVariantIds: string[]
): Decision {
  return { id, group, title, question, techVariantIds }
}

export const DECISIONS = [
  decision(
    'identity',
    'Айдентика',
    'Візуальний напрям',
    'Якою має бути музична карта України?',
    'v1-aero',
    'ornament',
    'nichna-karta',
    'atlas',
  ),
  decision(
    'map.basemap',
    'Карта',
    'Основа карти',
    'Скільки деталей залишити на підкладці?',
    'light-minimal',
    'night',
    'atlas-paper',
  ),
  decision(
    'map.initial-view',
    'Карта',
    'Перше відкриття карти',
    'З чого почати знайомство з картою?',
    'UKRAINE',
    'UKRAINE_WITH_NEAR_ME_PROMPT',
  ),
  decision(
    'map.aggregation',
    'Карта',
    'Від області до музиканта',
    'Як розкривати карту під час наближення?',
    'REGION_PLACE_PIN',
    'PLACE_PIN',
    'CHOROPLETH_PLACE_PIN',
  ),
  decision(
    'map.pin',
    'Карта',
    'Позначка музиканта',
    'Як упізнати музиканта на карті?',
    'AVATAR_RING',
    'DROP_ICON',
    'DOT_LABEL',
  ),
  decision(
    'map.cluster',
    'Карта',
    'Кількість на карті',
    'Як підписувати групи музикантів?',
    'BUBBLE_COUNT',
    'BUBBLE_COUNT_LABEL',
  ),
  decision(
    'map.origin-notice',
    'Карта',
    'Пояснення походження',
    'Де читач побачить, що означає позначка?',
    'PERSISTENT_CHIP',
    'LEGEND_BAR',
    'PIN_CARD_LABEL',
  ),
  decision(
    'audio.mobile-trigger',
    'Звук',
    'Жест для прослуховування',
    'Як на телефоні запустити фрагмент?',
    'TAP_PREVIEW_AND_PLAY',
    'TAP_PREVIEW_PLAY_BUTTON',
    'LONG_PRESS_PLAY',
  ),
  decision(
    'audio.desktop-hover',
    'Звук',
    'Наведення на позначку',
    'Як показати, що звучить фрагмент?',
    'RING_PROGRESS',
    'EQUALIZER_BADGE',
  ),
  decision(
    'audio.player',
    'Звук',
    'Керування звуком',
    'Де розташувати плеєр?',
    'MINI_BAR',
    'IN_CARD',
  ),
  decision(
    'musician.preview',
    'Музикант',
    'Картка на карті',
    'Як подивитися картку і зберегти контекст карти?',
    'BOTTOM_SHEET',
    'FLOATING_CARD',
  ),
  decision(
    'musician.page',
    'Музикант',
    'Сторінка музиканта',
    'Як читати історію і знаходити записи?',
    'HERO',
    'EDITORIAL',
    'COMPACT',
  ),
  decision(
    'like.button',
    'Музикант',
    'Лайки',
    'Як підтримати музиканта кількома натисканнями?',
    'HEART_BURST',
    'APPLAUSE',
    'CUSTOM_SYMBOL',
  ),
  decision(
    'filters.region',
    'Навігація',
    'Фільтр за регіоном',
    'Як вибрати одну чи кілька областей?',
    'SHEET_LIST',
    'CHIPS_SCROLL',
    'MAP_TAP_REGION',
  ),
  decision(
    'submission.form',
    'Заявки',
    'Пропозиція музиканта',
    'Як заповнити й перевірити заявку?',
    'SINGLE_PAGE',
    'WIZARD',
  ),
  decision(
    'nav.structure',
    'Навігація',
    'Розділи застосунку',
    'Як переміщатися між картою, пошуком і формою?',
    'MAP_ONLY_SHEETS',
    'TAB_BAR',
    'TOP_BAR_MENU',
  ),
  decision(
    'onboarding',
    'Навігація',
    'Перше знайомство',
    'Які підказки потрібні при першому відкритті?',
    'NONE',
    'FIRST_VISIT_TIPS',
  ),
]
export function byId(id: string | undefined) {
  return DECISIONS.find((d) => d.id === id)
}

import { variants } from './variants'
export function known(id: string, variant: string) {
  return (
    variant !== 'PIN_CARD_LABEL' &&
    Object.hasOwn(variants, id) &&
    variants[id].some((v) => v.id === variant)
  )
}
