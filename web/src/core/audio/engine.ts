import type { components } from '../../api/types.gen'

type Fragment = components['schemas']['Fragment']

export interface AudioLike {
  src: string
  currentTime: number
  volume: number
  play(): Promise<void>
  pause(): void
  addEventListener(t: 'ended' | 'error' | 'loadedmetadata', l: () => void): void
  removeEventListener(t: string, l: () => void): void
}

export interface Clock {
  now(): number
  setTimeout(fn: () => void, ms: number): number
  clearTimeout(h: number): void
}

export type EngineStatus = 'idle' | 'loading' | 'playing' | 'blocked' | 'error'
export interface EngineState {
  status: EngineStatus
  musicianId: string | null
  clipId: string | null
}

export interface AudioEngine {
  play(musicianId: string, f: Fragment): Promise<void>
  stop(opts?: { fade?: boolean }): void
  getState(): EngineState
  subscribe(l: (s: EngineState) => void): () => void
}

const FADE_STEP_MS = 50
const IDLE: EngineState = { status: 'idle', musicianId: null, clipId: null }

/** iOS Safari: `volume` is read-only, so a write is silently ignored. */
export function isVolumeWritable(a: HTMLAudioElement): boolean {
  const prev = a.volume
  try {
    a.volume = 0.5
    const ok = a.volume === 0.5
    a.volume = prev
    return ok
  } catch {
    return false
  }
}

export function createAudioEngine(deps: { audio: AudioLike; clock: Clock; volumeWritable: boolean }): AudioEngine {
  const { audio, clock, volumeWritable } = deps
  let state: EngineState = IDLE
  let gen = 0
  let current: Fragment | null = null
  let endTimer: number | null = null
  let fadeTimer: number | null = null
  let detach: (() => void) | null = null
  let abortWait: (() => void) | null = null
  const subs = new Set<(s: EngineState) => void>()

  const set = (s: EngineState) => {
    if (s.status === state.status && s.musicianId === state.musicianId && s.clipId === state.clipId) return
    state = s
    subs.forEach((l) => l(s))
  }

  const clearTimers = () => {
    if (endTimer !== null) clock.clearTimeout(endTimer)
    if (fadeTimer !== null) clock.clearTimeout(fadeTimer)
    endTimer = fadeTimer = null
  }

  const teardown = () => {
    clearTimers()
    detach?.()
    detach = null
    abortWait?.()
    abortWait = null
    audio.pause()
  }

  const ramp = (from: number, to: number, ms: number, done?: () => void) => {
    if (fadeTimer !== null) clock.clearTimeout(fadeTimer)
    const start = clock.now()
    const tick = () => {
      const p = ms <= 0 ? 1 : Math.min(1, (clock.now() - start) / ms)
      audio.volume = p >= 1 ? to : from + (to - from) * p
      if (p >= 1) {
        fadeTimer = null
        done?.()
      } else fadeTimer = clock.setTimeout(tick, FADE_STEP_MS)
    }
    tick()
  }

  const finish = () => {
    clearTimers()
    audio.pause()
    set(IDLE)
  }

  async function play(musicianId: string, f: Fragment): Promise<void> {
    const my = ++gen
    teardown()
    current = f
    set({ status: 'loading', musicianId, clipId: f.clipId })

    const outcome = await new Promise<'ok' | 'error' | 'cancelled'>((resolve) => {
      const onMeta = () => resolve('ok')
      const onErr = () => resolve('error')
      audio.addEventListener('loadedmetadata', onMeta)
      audio.addEventListener('error', onErr)
      detach = () => {
        audio.removeEventListener('loadedmetadata', onMeta)
        audio.removeEventListener('error', onErr)
      }
      abortWait = () => resolve('cancelled')
      audio.src = f.url
    })
    if (my !== gen || outcome === 'cancelled') return
    abortWait = null
    detach?.()
    detach = null
    if (outcome === 'error') {
      set({ status: 'error', musicianId, clipId: f.clipId })
      return
    }

    audio.currentTime = f.startSec
    if (volumeWritable) audio.volume = 0
    try {
      await audio.play()
    } catch (e) {
      if (my !== gen) return
      const blocked = (e as { name?: string })?.name === 'NotAllowedError'
      set({ status: blocked ? 'blocked' : 'error', musicianId, clipId: f.clipId })
      return
    }
    if (my !== gen) return

    const onEnded = () => finish()
    const onError = () => {
      clearTimers()
      set({ status: 'error', musicianId, clipId: f.clipId })
    }
    audio.addEventListener('ended', onEnded)
    audio.addEventListener('error', onError)
    detach = () => {
      audio.removeEventListener('ended', onEnded)
      audio.removeEventListener('error', onError)
    }
    set({ status: 'playing', musicianId, clipId: f.clipId })

    const totalMs = f.durationSec * 1000
    if (volumeWritable) {
      ramp(0, 1, f.fadeInMs)
      endTimer = clock.setTimeout(() => {
        endTimer = null
        ramp(audio.volume, 0, f.fadeOutMs, finish)
      }, Math.max(0, totalMs - f.fadeOutMs))
    } else {
      endTimer = clock.setTimeout(finish, totalMs)
    }
  }

  function stop(opts?: { fade?: boolean }) {
    gen++
    const wasPlaying = state.status === 'playing'
    if (opts?.fade && volumeWritable && wasPlaying && current) {
      clearTimers()
      detach?.()
      detach = null
      set(IDLE)
      ramp(audio.volume, 0, current.fadeOutMs, () => audio.pause())
      return
    }
    teardown()
    set(IDLE)
  }

  return {
    play,
    stop,
    getState: () => state,
    subscribe(l) {
      subs.add(l)
      return () => void subs.delete(l)
    },
  }
}
