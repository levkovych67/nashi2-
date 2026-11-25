/** Size that fits `maxSide` on the long side, keeping the aspect ratio and never upscaling. */
export function targetSize(w: number, h: number, maxSide: number): { width: number; height: number } {
  const longest = Math.max(w, h)
  if (longest <= maxSide) return { width: w, height: h }
  const k = maxSide / longest
  return { width: Math.max(1, Math.round(w * k)), height: Math.max(1, Math.round(h * k)) }
}
