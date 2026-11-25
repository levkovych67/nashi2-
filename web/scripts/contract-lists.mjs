// Checked-in mirror of docs/02-contracts.md sections 5, 6 and 8. design-sync.test.mjs verifies it against the doc.
const seq = (prefix, from, to) => Array.from({ length: to - from + 1 }, (_, i) => `${prefix}${from + i}`)

export const REQUIRED_TOKENS = [
  ...['bg', 'surface', 'surface-raised', 'overlay', 'border', 'text', 'text-muted', 'text-inverse', 'accent',
    'accent-hover', 'on-accent', 'like', 'on-like', 'success', 'danger', 'focus'].map((n) => `color.${n}`),
  ...['land', 'water', 'road', 'label', 'label-halo', 'ua-border', 'region-line', 'region-fill-active']
    .map((n) => `color.map.${n}`),
  ...['ring', 'ring-audio', 'ring-playing', 'fallback-bg', 'fallback-text'].map((n) => `color.pin.${n}`),
  ...['bg', 'text', 'ring'].map((n) => `color.cluster.${n}`),
  'font.family.display', 'font.family.body',
  'font.weight.regular', 'font.weight.medium', 'font.weight.bold',
  ...['xs', 'sm', 'md', 'lg', 'xl', '2xl', '3xl'].map((n) => `font.size.${n}`),
  'font.line-height.tight', 'font.line-height.normal',
  'radius.sm', 'radius.md', 'radius.lg', 'radius.pill',
  ...seq('space.', 1, 12),
  'shadow.sm', 'shadow.md', 'shadow.lg',
  'motion.duration.fast', 'motion.duration.base', 'motion.duration.slow', 'motion.duration.map-fly',
  'motion.easing.standard', 'motion.easing.emphasized',
  'size.pin.sm', 'size.pin.md', 'size.pin.lg', 'size.touch-min', 'size.sheet.peek',
]

export const tokenToCssVar = (token) => `--n-${token.replaceAll('.', '-')}`

const SUBMIT_FIELDS = ['name', 'kind', 'originPlaceId', 'birthPlaceId', 'summary', 'genres', 'links',
  'youtubeChannelUrl', 'wikipediaUrl', 'contact', 'images']
const ERROR_CODES = ['VALIDATION_FAILED', 'PAYLOAD_TOO_LARGE', 'UNSUPPORTED_MEDIA_TYPE', 'RATE_LIMITED', 'INTERNAL',
  'UNAVAILABLE']

export const REQUIRED_COPY_KEYS = [
  'app.name', 'app.tagline', 'map.originNotice', 'map.soundOn', 'map.soundOff', 'map.soundBlockedHint',
  'map.nearMe', 'map.nearMeDenied', 'map.resultsCount', 'map.backToUkraine', 'cluster.ariaLabel', 'pin.ariaLabel',
  'pin.noAudio', 'preview.openProfile', 'preview.play', 'preview.stop', 'musician.originLabel.BIRTH',
  'musician.originLabel.FORMATION', 'musician.originLabel.ORIGIN', 'musician.precisionNote.CITY',
  'musician.likeAria', 'musician.likesCount', 'musician.listenOn', 'musician.youtubeChannel',
  'musician.textSource', 'musician.photoCredit', 'musician.noAudio', 'filters.title', 'filters.region',
  'filters.reset', 'filters.apply', 'list.empty', 'submit.title', 'submit.intro',
  ...SUBMIT_FIELDS.flatMap((f) => [`submit.field.${f}.label`, `submit.field.${f}.hint`]),
  'submit.consent', 'submit.send', 'submit.sending', 'submit.success',
  ...ERROR_CODES.map((c) => `submit.error.${c}`),
  'errors.network', 'errors.notFound', 'errors.rateLimited', 'errors.generic',
]

// Section 6, column "Технічні ID". `identity` and `map.basemap` have free ids and are absent.
export const TECHNICAL_VARIANTS = {
  'map.initial-view': ['UKRAINE', 'UKRAINE_WITH_NEAR_ME_PROMPT'],
  'map.aggregation': ['REGION_PLACE_PIN', 'PLACE_PIN', 'CHOROPLETH_PLACE_PIN'],
  'map.pin': ['AVATAR_RING', 'DROP_ICON', 'DOT_LABEL'],
  'map.cluster': ['BUBBLE_COUNT', 'BUBBLE_COUNT_LABEL'],
  'map.origin-notice': ['PERSISTENT_CHIP', 'LEGEND_BAR', 'PIN_CARD_LABEL'],
  'audio.mobile-trigger': ['TAP_PREVIEW_AND_PLAY', 'TAP_PREVIEW_PLAY_BUTTON', 'LONG_PRESS_PLAY'],
  'audio.desktop-hover': ['RING_PROGRESS', 'EQUALIZER_BADGE'],
  'audio.player': ['MINI_BAR', 'IN_CARD'],
  'musician.preview': ['BOTTOM_SHEET', 'FLOATING_CARD'],
  'musician.page': ['HERO', 'EDITORIAL', 'COMPACT'],
  'like.button': ['HEART_BURST', 'APPLAUSE', 'CUSTOM_SYMBOL'],
  'filters.region': ['SHEET_LIST', 'CHIPS_SCROLL', 'MAP_TAP_REGION'],
  'submission.form': ['SINGLE_PAGE', 'WIZARD'],
  'nav.structure': ['MAP_ONLY_SHEETS', 'TAB_BAR', 'TOP_BAR_MENU'],
  onboarding: ['NONE', 'FIRST_VISIT_TIPS'],
}
export const FREE_ID_DECISIONS = ['identity', 'map.basemap']
