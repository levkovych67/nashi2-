import { act, render } from '@testing-library/react'
import { useContext } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { components } from '../../api/types.gen'
import { AudioEngineContext, AudioRoot } from './AudioRoot'
import type { AudioEngine } from './engine'

const play = vi.fn<() => Promise<void>>()
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

let engine: AudioEngine | null = null
function Probe() {
  engine = useContext(AudioEngineContext)
  return null
}
const gesture = (type: string) => act(async () => void document.dispatchEvent(new Event(type)))

function mount() {
  vi.stubGlobal('Audio', FakeAudio)
  return render(
    <AudioRoot>
      <Probe />
    </AudioRoot>,
  )
}

describe('AudioRoot unlock', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    play.mockReset()
    pause.mockReset()
    engine = null
  })

  it('retries on later gestures until play succeeds, then removes its listeners', async () => {
    const notAllowed = new DOMException('blocked', 'NotAllowedError')
    play.mockRejectedValueOnce(notAllowed).mockResolvedValue(undefined)
    mount()
    expect(play).not.toHaveBeenCalled()
    await gesture('touchend')
    expect(play).toHaveBeenCalledTimes(1)
    await gesture('click')
    expect(play).toHaveBeenCalledTimes(2)
    await gesture('touchend')
    await gesture('keydown')
    expect(play).toHaveBeenCalledTimes(2)
  })

  it('a gesture while the engine is busy does not consume the attempt', async () => {
    play.mockResolvedValue(undefined)
    mount()
    void engine!.play('m1', { clipId: 'c', url: 'u', startSec: 0, durationSec: 15, fadeInMs: 300, fadeOutMs: 600, title: 't', source: 'UPLOAD', attribution: 'a' } as components['schemas']['Fragment'])
    expect(engine!.getState().status).toBe('loading')
    await gesture('touchend')
    expect(play).not.toHaveBeenCalled()
    engine!.stop()
    await gesture('touchend')
    expect(play).toHaveBeenCalledTimes(1)
    await gesture('touchend')
    expect(play).toHaveBeenCalledTimes(1)
  })
})
