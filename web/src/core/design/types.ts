export type DecisionId =
  | 'identity'
  | 'map.basemap'
  | 'map.initial-view'
  | 'map.aggregation'
  | 'map.pin'
  | 'map.cluster'
  | 'map.origin-notice'
  | 'audio.mobile-trigger'
  | 'audio.desktop-hover'
  | 'audio.player'
  | 'musician.preview'
  | 'musician.page'
  | 'like.button'
  | 'filters.region'
  | 'submission.form'
  | 'nav.structure'
  | 'onboarding'

export interface DecisionEntry {
  variant: string
  status: 'PROPOSED' | 'APPROVED'
  approvedBy?: string
  approvedAt?: string
  params?: Record<string, number | string>
  note?: string
}

export interface DecisionsFile {
  schemaVersion: number
  generatedAt?: string
  studioCommit?: string
  decisions: Partial<Record<DecisionId, DecisionEntry>>
}

export interface DesignConfig {
  variant(id: DecisionId): string
  param(id: DecisionId, name: string, fallback: number): number
}
