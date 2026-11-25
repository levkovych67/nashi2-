import { describe, expect, it } from 'vitest'
import { reducePinGesture, type MobileTrigger, type PinAction, type PinGesture, type PinUiState } from './mobileTrigger'

type Row = [MobileTrigger, PinUiState, PinGesture, PinAction]

describe('reducePinGesture', () => {
  it.each<Row>([
    ['TAP_PREVIEW_AND_PLAY', { previewId: null, playingId: null }, { type: 'tap', musicianId: 'a' }, { preview: 'a', play: 'a' }],
    ['TAP_PREVIEW_AND_PLAY', { previewId: 'a', playingId: 'a' }, { type: 'tap', musicianId: 'a' }, { stop: true }],
    ['TAP_PREVIEW_AND_PLAY', { previewId: 'a', playingId: 'a' }, { type: 'tap', musicianId: 'b' }, { preview: 'b', play: 'b' }],
    ['TAP_PREVIEW_AND_PLAY', { previewId: 'a', playingId: 'a' }, { type: 'closePreview', musicianId: 'a' }, { preview: null, stop: true }],
    ['TAP_PREVIEW_PLAY_BUTTON', { previewId: null, playingId: null }, { type: 'tap', musicianId: 'a' }, { preview: 'a' }],
    ['TAP_PREVIEW_PLAY_BUTTON', { previewId: 'a', playingId: null }, { type: 'playButton', musicianId: 'a' }, { play: 'a' }],
    ['TAP_PREVIEW_PLAY_BUTTON', { previewId: 'a', playingId: 'a' }, { type: 'playButton', musicianId: 'a' }, { stop: true }],
    ['TAP_PREVIEW_PLAY_BUTTON', { previewId: 'a', playingId: 'a' }, { type: 'tap', musicianId: 'b' }, { preview: 'b', stop: true }],
    ['LONG_PRESS_PLAY', { previewId: null, playingId: null }, { type: 'longPress', musicianId: 'a' }, { play: 'a' }],
    ['LONG_PRESS_PLAY', { previewId: null, playingId: null }, { type: 'tap', musicianId: 'a' }, { preview: 'a' }],
    ['LONG_PRESS_PLAY', { previewId: null, playingId: 'a' }, { type: 'longPress', musicianId: 'a' }, { stop: true }],
    ['LONG_PRESS_PLAY', { previewId: 'a', playingId: 'a' }, { type: 'tap', musicianId: 'b' }, { preview: 'b', stop: true }],
  ])('%s %o %o → %o', (mode, s, g, expected) => expect(reducePinGesture(mode, s, g)).toEqual(expected))

  it('ignores gestures that the mode does not use', () => {
    const idle = { previewId: null, playingId: null }
    expect(reducePinGesture('TAP_PREVIEW_AND_PLAY', idle, { type: 'longPress', musicianId: 'a' })).toEqual({})
    expect(reducePinGesture('LONG_PRESS_PLAY', idle, { type: 'playButton', musicianId: 'a' })).toEqual({})
  })
})
