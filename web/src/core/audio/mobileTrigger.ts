export type MobileTrigger = 'TAP_PREVIEW_AND_PLAY' | 'TAP_PREVIEW_PLAY_BUTTON' | 'LONG_PRESS_PLAY'
export type PinGesture = { type: 'tap' | 'longPress' | 'playButton' | 'closePreview'; musicianId: string }
export interface PinUiState {
  previewId: string | null
  playingId: string | null
}
export interface PinAction {
  preview?: string | null
  play?: string
  stop?: true
}

const toggle = (s: PinUiState, id: string): PinAction => (s.playingId === id ? { stop: true } : { play: id })

/** Tap that only opens the preview; stops audio of a different musician. */
const previewOnly = (s: PinUiState, id: string): PinAction =>
  s.playingId !== null && s.playingId !== id ? { preview: id, stop: true } : { preview: id }

export function reducePinGesture(mode: MobileTrigger, s: PinUiState, g: PinGesture): PinAction {
  const id = g.musicianId
  if (g.type === 'closePreview') return s.playingId !== null ? { preview: null, stop: true } : { preview: null }
  switch (mode) {
    case 'TAP_PREVIEW_AND_PLAY':
      if (g.type === 'tap') return s.playingId === id ? { stop: true } : { preview: id, play: id }
      return {}
    case 'TAP_PREVIEW_PLAY_BUTTON':
      if (g.type === 'tap') return previewOnly(s, id)
      if (g.type === 'playButton') return toggle(s, id)
      return {}
    case 'LONG_PRESS_PLAY':
      if (g.type === 'tap') return previewOnly(s, id)
      if (g.type === 'longPress') return toggle(s, id)
      return {}
  }
}
