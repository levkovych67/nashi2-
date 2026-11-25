import { useMemo, useRef, type PointerEvent } from 'react'

/** How a pin was activated: a mouse click (fragments already play on hover) or a tap (touch, pen, keyboard). */
export type PinInput = 'mouse' | 'tap'

/**
 * Remembers which pointer pressed a control, so its click can be told apart per event (a touch laptop has both).
 * A click without a pointer before it (Enter / Space) is a tap.
 */
export function usePinInput() {
  const pressedBy = useRef<string | null>(null)
  return useMemo(
    () => ({
      onPointerDown: (e: PointerEvent) => {
        pressedBy.current = e.pointerType
      },
      take: (): PinInput => {
        const input = pressedBy.current === 'mouse' ? 'mouse' : 'tap'
        pressedBy.current = null
        return input
      },
    }),
    [],
  )
}
