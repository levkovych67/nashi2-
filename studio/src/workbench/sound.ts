import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
} from 'react'
import { loadProfile } from './profile'

export interface Clip {
  clipId: string
  title: string
  url: string
  startSec: number
  durationSec: number
  attribution: string
}
export interface AudioPin {
  id: string
  slug?: string
  hasAudio: boolean
}
const demoTitle = 'Синтетичний демонстраційний звук студії «Наші»'
const cache = new Map<string, Promise<Clip[]>>()
const localWav = /^\/fixtures\/audio\/clip-0[1-6]\.wav$/
function synthetic(id: string, index: number): Clip {
  return {
    clipId: id + '-demo-' + index,
    title: 'Демоетюд ' + index,
    url: '/fixtures/audio/clip-0' + index + '.wav',
    startSec: 0,
    durationSec: 15,
    attribution: demoTitle,
  }
}
export function fragments(pin: AudioPin, all = false): Promise<Clip[]> {
  if (!pin.hasAudio)
    return Promise.resolve(
      all ? [synthetic(pin.id, 1 + ((pin.id.charCodeAt(0) || 0) % 6))] : [],
    )
  const key = pin.id + '/' + (pin.slug || '')
  const prior = cache.get(key)
  if (prior) return prior
  const result = Promise.all([
    fetch('/fixtures/fragments/' + encodeURIComponent(pin.id) + '.json').then(
      async (r) => {
        if (r.status === 204) return null
        if (!r.ok) throw Error('fragment')
        const clip: Clip = await r.json()
        if (!localWav.test(clip.url)) throw Error('fixture')
        return clip
      },
    ),
    pin.slug ? loadProfile(pin.slug).catch(() => null) : null,
  ])
    .then(([first, profile]) => {
      if (!first) return []
      const tracks = [first]
      for (const clip of profile?.materials.clips || []) {
        const number = /^Демоетюд ([1-6])$/.exec(clip.title)?.[1]
        if (number && clip.clipId !== first.clipId)
          tracks.push({
            ...synthetic(pin.id, Number(number)),
            clipId: clip.clipId,
          })
      }
      return tracks
    })
    .catch((error) => {
      cache.delete(key)
      throw error
    })
  cache.set(key, result)
  return result
}

type Status = 'idle' | 'playing' | 'paused' | 'blocked' | 'loading'
interface Snapshot {
  owner?: symbol
  id?: string
  clip?: Clip
  status: Status
  progress: number
}
const idle: Snapshot = { status: 'idle', progress: 0 }
let snapshot = idle
let audio: HTMLAudioElement | undefined
let timer: ReturnType<typeof setInterval> | undefined
let token = 0
let lastWritable = true
const listeners = new Set<() => void>()
function publish(value: Snapshot) {
  snapshot = value
  listeners.forEach((fn) => fn())
}
function cancelTimer() {
  clearInterval(timer)
  timer = undefined
}
function iosDevice() {
  return (
    /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
  )
}
export function stopSound(owner: symbol, fade = false, simulateIOS = false) {
  if (snapshot.owner !== owner) return
  ++token
  cancelTimer()
  const media = audio
  if (media && fade && !media.paused && !simulateIOS && !iosDevice()) {
    const started = Date.now(),
      from = media.volume
    timer = setInterval(() => {
      const step = Math.max(0, 1 - (Date.now() - started) / 600)
      media.volume = from * step
      if (step === 0) {
        cancelTimer()
        media.pause()
      }
    }, 30)
  } else media?.pause()
  publish(idle)
}
function unlockSound() {
  if (snapshot.status === 'playing' || snapshot.status === 'loading') return
  const media = (audio ??= new Audio())
  cancelTimer()
  if (!media.src) media.src = '/fixtures/audio/clip-01.wav'
  media.muted = true
  void media.play().catch(() => {})
  media.pause()
  media.muted = false
}
function start(
  owner: symbol,
  id: string,
  clip: Clip,
  simulateIOS: boolean,
  resume: boolean,
) {
  const writable = !simulateIOS && !iosDevice()
  const request = ++token
  cancelTimer()
  audio?.pause()
  // A fresh element inherits hardware volume when entering fixed-volume mode.
  if (!writable && lastWritable && audio && audio.volume < 1) {
    audio = undefined
    resume = false
  }
  const media = (audio ??= new Audio())
  lastWritable = writable
  if (!resume) {
    media.src = clip.url
    media.currentTime = clip.startSec
  }
  if (writable) media.volume = 0
  const at = Math.max(0, media.currentTime - clip.startSec)
  publish({
    owner,
    id,
    clip,
    status: 'loading',
    progress: at / clip.durationSec,
  })
  media.onended = () => {
    if (request === token) stopSound(owner)
  }
  media.onpause = () => {
    if (request === token && media.paused && snapshot.status === 'playing') {
      cancelTimer()
      publish({ ...snapshot, status: 'paused' })
    }
  }
  media.onerror = () => {
    if (request === token) {
      cancelTimer()
      publish({ ...snapshot, status: 'blocked' })
    }
  }
  void media.play().then(
    () => {
      if (request !== token) return
      const started = Date.now()
      publish({ ...snapshot, status: 'playing' })
      timer = setInterval(() => {
        const elapsed = Math.max(0, media.currentTime - clip.startSec)
        if (elapsed >= clip.durationSec) {
          stopSound(owner)
          return
        }
        if (writable)
          media.volume = Math.min(
            1,
            (Date.now() - started) / 300,
            (clip.durationSec - elapsed) / 0.6,
          )
        publish({ ...snapshot, progress: elapsed / clip.durationSec })
      }, 30)
    },
    () => {
      if (request === token) publish({ ...snapshot, status: 'blocked' })
    },
  )
}
const subscribe = (notify: () => void) => {
  listeners.add(notify)
  return () => {
    listeners.delete(notify)
  }
}
export const SoundMuted = createContext(false)
export function useSound(pins: AudioPin[], all = false, simulateIOS = false) {
  const muted = useContext(SoundMuted)
  const owner = useMemo(() => Symbol(), [])
  useEffect(() => {
    if (muted) stopSound(owner)
  }, [muted, owner])
  const shared = useSyncExternalStore(subscribe, () => snapshot)
  const state = shared.owner === owner ? shared : idle
  const [lists, setLists] = useState<Record<string, Clip[] | null>>({})
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let live = true
    setLists({})
    for (const pin of pins) {
      fragments(pin, all).then(
        (clips) => {
          if (live) setLists((prev) => ({ ...prev, [pin.id]: clips }))
        },
        () => {
          if (live) setLists((prev) => ({ ...prev, [pin.id]: null }))
        },
      )
    }
    return () => {
      live = false
      stopSound(owner)
    }
  }, [pins, owner, all, attempt])
  function toggle(pin: AudioPin, next = false, specific?: Clip) {
    if (muted) return
    const clips = lists[pin.id]
    if (clips === undefined) {
      if (
        snapshot.owner === owner &&
        snapshot.id === pin.id &&
        snapshot.status === 'loading'
      ) {
        stopSound(owner)
        return
      }
      if (snapshot.owner) stopSound(snapshot.owner)
      unlockSound()
      const request = ++token
      publish({ owner, id: pin.id, status: 'loading', progress: 0 })
      void fragments(pin, all).then(
        (loaded) => {
          if (request !== token) return
          if (!loaded.length) {
            stopSound(owner)
            return
          }
          start(
            owner,
            pin.id,
            loaded[Math.floor(Math.random() * loaded.length)],
            simulateIOS,
            false,
          )
        },
        () => {
          if (request === token)
            publish({ owner, id: pin.id, status: 'blocked', progress: 0 })
        },
      )
      return
    }
    if (!clips?.length) {
      stopSound(owner)
      return
    }
    const previous =
      snapshot.owner === owner && snapshot.id === pin.id ? snapshot : idle
    if (
      !next &&
      (!specific || previous.clip?.clipId === specific.clipId) &&
      ['playing', 'loading'].includes(previous.status)
    ) {
      ++token
      cancelTimer()
      audio?.pause()
      publish({ ...previous, status: 'paused' })
      return
    }
    const alternatives =
      next && clips.length > 1
        ? clips.filter((c) => c.clipId !== previous.clip?.clipId)
        : clips
    const clip =
      specific ||
      (!next && previous.clip) ||
      alternatives[Math.floor(Math.random() * alternatives.length)]
    start(
      owner,
      pin.id,
      clip,
      simulateIOS,
      !next && previous.status === 'paused' && previous.clip === clip,
    )
  }
  return {
    state,
    lists,
    toggle,
    muted,
    unlock: () => {
      if (!muted) unlockSound()
    },
    stop: (fade = false) => stopSound(owner, fade, simulateIOS),
    retry: () => setAttempt((n) => n + 1),
  }
}
export type SoundDeck = ReturnType<typeof useSound>
