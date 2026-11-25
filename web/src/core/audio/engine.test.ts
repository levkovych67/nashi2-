import { describe, expect, it } from 'vitest'
import type { components } from '../../api/types.gen'
import { createAudioEngine, type AudioLike, type Clock } from './engine'

type Fragment = components['schemas']['Fragment']

function fakeClock() {
  let t = 0
  let next = 1
  const timers = new Map<number, { at: number; fn: () => void }>()
  const clock: Clock = {
    now: () => t,
    setTimeout(fn, ms) {
      const id = next++
      timers.set(id, { at: t + ms, fn })
      return id
    },
    clearTimeout(id) {
      timers.delete(id)
    },
  }
  const advance = (ms: number) => {
    const target = t + ms
    for (;;) {
      let pick: [number, { at: number; fn: () => void }] | undefined
      for (const e of timers) if (e[1].at <= target && (!pick || e[1].at < pick[1].at)) pick = e
      if (!pick) break
      timers.delete(pick[0])
      t = pick[1].at
      pick[1].fn()
    }
    t = target
  }
  return { clock, advance }
}

function fakeAudio(opts: { playError?: string; autoMeta?: boolean } = {}) {
  const listeners = new Map<string, Set<() => void>>()
  const log = { pauses: 0, volumeWrites: 0, plays: 0 }
  let src = ''
  let volume = 1
  const emit = (t: string) => listeners.get(t)?.forEach((l) => l())
  const audio: AudioLike & { paused: boolean } = {
    paused: true,
    currentTime: 0,
    get src() {
      return src
    },
    set src(v: string) {
      src = v
      if (opts.autoMeta !== false) queueMicrotask(() => emit('loadedmetadata'))
    },
    get volume() {
      return volume
    },
    set volume(v: number) {
      log.volumeWrites++
      volume = v
    },
    async play() {
      log.plays++
      if (opts.playError) throw new DOMException('blocked', opts.playError)
      audio.paused = false
    },
    pause() {
      log.pauses++
      audio.paused = true
    },
    addEventListener(t, l) {
      if (!listeners.has(t)) listeners.set(t, new Set())
      listeners.get(t)!.add(l)
    },
    removeEventListener(t, l) {
      listeners.get(t)?.delete(l)
    },
  }
  return { audio, log, emit }
}

const frag = (over: Partial<Fragment> = {}): Fragment => ({
  clipId: 'c1',
  url: 'https://x/a.wav',
  startSec: 12,
  durationSec: 15,
  fadeInMs: 300,
  fadeOutMs: 600,
  title: 't',
  source: 'UPLOAD' as Fragment['source'],
  attribution: 'a',
  ...over,
})

describe('audio engine', () => {
  it('plays from startSec with linear fade-in when volume is writable', async () => {
    const { clock, advance } = fakeClock()
    const { audio } = fakeAudio()
    const e = createAudioEngine({ audio, clock, volumeWritable: true })
    await e.play('m1', frag())
    expect(audio.src).toBe('https://x/a.wav')
    expect(audio.currentTime).toBe(12)
    expect(e.getState()).toEqual({ status: 'playing', musicianId: 'm1', clipId: 'c1' })
    expect(audio.volume).toBe(0)
    advance(150)
    expect(audio.volume).toBeCloseTo(0.5, 5)
    advance(150)
    expect(audio.volume).toBe(1)
  })

  it('fades out and pauses at durationSec', async () => {
    const { clock, advance } = fakeClock()
    const { audio } = fakeAudio()
    const e = createAudioEngine({ audio, clock, volumeWritable: true })
    await e.play('m1', frag())
    advance(14_399)
    expect(audio.volume).toBe(1)
    advance(1 + 300) // t = 14 700: halfway through fade-out
    expect(audio.volume).toBeCloseTo(0.5, 5)
    expect(audio.paused).toBe(false)
    advance(299)
    expect(audio.paused).toBe(false)
    advance(1) // t = 15 000
    expect(audio.paused).toBe(true)
    expect(e.getState()).toEqual({ status: 'idle', musicianId: null, clipId: null })
  })

  it('a new play cancels the previous one', async () => {
    const { clock, advance } = fakeClock()
    const { audio } = fakeAudio()
    const e = createAudioEngine({ audio, clock, volumeWritable: true })
    await e.play('m1', frag())
    advance(5_000)
    const second = e.play('m2', frag({ clipId: 'c2', url: 'https://x/b.wav', startSec: 3 }))
    await second
    expect(audio.src).toBe('https://x/b.wav')
    expect(audio.currentTime).toBe(3)
    expect(e.getState()).toEqual({ status: 'playing', musicianId: 'm2', clipId: 'c2' })
    advance(14_000) // the first clip's end timer (t = 15 000) must not fire
    expect(audio.paused).toBe(false)
    advance(1_000)
    expect(audio.paused).toBe(true)
  })

  it('a play superseded while loading never starts', async () => {
    const { clock } = fakeClock()
    const { audio, log } = fakeAudio()
    const e = createAudioEngine({ audio, clock, volumeWritable: true })
    const first = e.play('m1', frag())
    const second = e.play('m2', frag({ clipId: 'c2' }))
    await Promise.all([first, second])
    expect(log.plays).toBe(1)
    expect(e.getState().musicianId).toBe('m2')
  })

  it('play rejected with NotAllowedError goes to blocked', async () => {
    const { clock } = fakeClock()
    const { audio } = fakeAudio({ playError: 'NotAllowedError' })
    const e = createAudioEngine({ audio, clock, volumeWritable: true })
    await expect(e.play('m1', frag())).resolves.toBeUndefined()
    expect(e.getState().status).toBe('blocked')
  })

  it('skips fades when volume is not writable', async () => {
    const { clock, advance } = fakeClock()
    const { audio, log } = fakeAudio()
    const e = createAudioEngine({ audio, clock, volumeWritable: false })
    await e.play('m1', frag())
    advance(14_999)
    expect(log.volumeWrites).toBe(0)
    expect(audio.paused).toBe(false)
    advance(1)
    expect(audio.paused).toBe(true)
    expect(log.volumeWrites).toBe(0)
    expect(e.getState().status).toBe('idle')
  })

  it('stop without fade pauses immediately', async () => {
    const { clock } = fakeClock()
    const { audio } = fakeAudio()
    const e = createAudioEngine({ audio, clock, volumeWritable: true })
    await e.play('m1', frag())
    e.stop()
    expect(audio.paused).toBe(true)
    expect(e.getState().status).toBe('idle')
  })

  it('stop with fade ramps volume down then pauses', async () => {
    const { clock, advance } = fakeClock()
    const { audio } = fakeAudio()
    const e = createAudioEngine({ audio, clock, volumeWritable: true })
    await e.play('m1', frag())
    advance(1_000)
    e.stop({ fade: true })
    expect(e.getState().status).toBe('idle')
    expect(audio.paused).toBe(false)
    advance(300)
    expect(audio.volume).toBeCloseTo(0.5, 5)
    advance(300)
    expect(audio.paused).toBe(true)
  })

  it('error event goes to error status, ended goes to idle', async () => {
    const { clock } = fakeClock()
    const { audio, emit } = fakeAudio()
    const e = createAudioEngine({ audio, clock, volumeWritable: true })
    await e.play('m1', frag())
    emit('ended')
    expect(e.getState().status).toBe('idle')
    await e.play('m1', frag())
    emit('error')
    expect(e.getState().status).toBe('error')
  })

  it('notifies subscribers and supports unsubscribe', async () => {
    const { clock } = fakeClock()
    const { audio } = fakeAudio()
    const e = createAudioEngine({ audio, clock, volumeWritable: true })
    const seen: string[] = []
    const off = e.subscribe((s) => seen.push(s.status))
    await e.play('m1', frag())
    off()
    e.stop()
    expect(seen).toEqual(['loading', 'playing'])
  })
})
