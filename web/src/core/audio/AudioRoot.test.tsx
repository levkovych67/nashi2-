import { render } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AudioRoot } from './AudioRoot'

describe('AudioRoot', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('unlocks the shared audio element on the first user gesture only', () => {
    const play = vi.fn(() => Promise.resolve())
    const pause = vi.fn()
    class FakeAudio {
      src = ''
      currentTime = 0
      volume = 1
      preload = ''
      play = play
      pause = pause
      addEventListener() {}
      removeEventListener() {}
    }
    vi.stubGlobal('Audio', FakeAudio)
    render(<AudioRoot>x</AudioRoot>)
    expect(play).not.toHaveBeenCalled()
    document.dispatchEvent(new Event('pointerdown'))
    expect(play).toHaveBeenCalledTimes(1)
    expect(pause).toHaveBeenCalledTimes(1)
    document.dispatchEvent(new Event('pointerdown'))
    expect(play).toHaveBeenCalledTimes(1)
  })
})
