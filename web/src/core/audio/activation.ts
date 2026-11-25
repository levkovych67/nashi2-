let activated = false

export function hasUserActivation(): boolean {
  if (activated) return true
  const ua = (globalThis.navigator as Navigator | undefined)?.userActivation
  return ua?.hasBeenActive === true
}

const EVENTS = ['pointerdown', 'keydown', 'touchend'] as const

/** Calls `cb` once on the first user gesture (the browser's autoplay gate opens then). */
export function onFirstActivation(target: EventTarget, cb: () => void): () => void {
  const off = () => EVENTS.forEach((e) => target.removeEventListener(e, handler, true))
  function handler() {
    activated = true
    off()
    cb()
  }
  EVENTS.forEach((e) => target.addEventListener(e, handler, true))
  return off
}
