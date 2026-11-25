import type { DecisionId } from './types'

// Defaults from docs/01-decisions.md; used for every decision that is not APPROVED.
export const DEFAULT_VARIANTS: Record<DecisionId, string> = {
  identity: 'default',
  'map.basemap': 'default',
  'map.initial-view': 'UKRAINE',
  'map.aggregation': 'REGION_PLACE_PIN',
  'map.pin': 'AVATAR_RING',
  'map.cluster': 'BUBBLE_COUNT',
  'map.origin-notice': 'PERSISTENT_CHIP',
  'audio.mobile-trigger': 'TAP_PREVIEW_AND_PLAY',
  'audio.desktop-hover': 'RING_PROGRESS',
  'audio.player': 'MINI_BAR',
  'musician.preview': 'BOTTOM_SHEET',
  'musician.page': 'HERO',
  'like.button': 'HEART_BURST',
  'filters.region': 'SHEET_LIST',
  'submission.form': 'SINGLE_PAGE',
  'nav.structure': 'MAP_ONLY_SHEETS',
  onboarding: 'NONE',
}

// Variants the product supports technically (02-contracts section 6). `identity` and
// `map.basemap` are free-form (tokens / map-style are read from files), so they are absent.
export const SUPPORTED_VARIANTS: Partial<Record<DecisionId, readonly string[]>> = {
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
