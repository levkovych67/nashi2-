import { createContext, useEffect, useState, type ReactNode } from 'react'
import { createAudioEngine, isVolumeWritable, type AudioEngine, type Clock, type EngineState } from './engine'

declare global {
  interface Window {
    __nashiAudioState?: EngineState
  }
}

/** One engine per app; `null` until `AudioRoot` mounts. */
export const AudioEngineContext = createContext<AudioEngine | null>(null)

export const realClock: Clock = {
  now: () => performance.now(),
  setTimeout: (fn, ms) => window.setTimeout(fn, ms),
  clearTimeout: (h) => window.clearTimeout(h),
}

function createBrowserEngine(): AudioEngine {
  const audio = new Audio()
  audio.preload = 'auto'
  return createAudioEngine({ audio, clock: realClock, volumeWritable: isVolumeWritable(audio) })
}

const PUBLISH = import.meta.env.DEV || import.meta.env.VITE_API_MOCK === '1'

export function AudioRoot({ children }: { children: ReactNode }) {
  const [engine] = useState(createBrowserEngine)
  useEffect(() => {
    if (!PUBLISH) return
    window.__nashiAudioState = engine.getState()
    return engine.subscribe((s) => {
      window.__nashiAudioState = s
    })
  }, [engine])
  return <AudioEngineContext.Provider value={engine}>{children}</AudioEngineContext.Provider>
}
