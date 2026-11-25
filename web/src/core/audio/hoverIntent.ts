import type { Clock } from './engine'

export function createHoverIntent(delayMs: number, clock: Clock) {
  let timer: number | null = null
  let pendingId: string | null = null
  const clear = () => {
    if (timer !== null) clock.clearTimeout(timer)
    timer = null
    pendingId = null
  }
  return {
    enter(id: string, fire: () => void) {
      clear()
      pendingId = id
      timer = clock.setTimeout(() => {
        timer = null
        pendingId = null
        fire()
      }, delayMs)
    },
    leave(id: string) {
      if (pendingId === id) clear()
    },
    cancel: clear,
  }
}
