import { useEffect, useState } from 'react'
import QRCode from 'qrcode'

export function QrCode({ value, size = 160 }: { value: string; size?: number }) {
  const [result, setResult] = useState<{ value: string; src: string | null }>()
  useEffect(() => {
    let active = true
    QRCode.toString(value, { type: 'svg', margin: 1 }).then(svg => {
      if (active) setResult({ value, src: 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg) })
    }).catch(() => {
      if (active) setResult({ value, src: null })
    })
    return () => { active = false }
  }, [value])

  if (result?.value !== value) return <span aria-live="polite">Готуємо QR-код…</span>
  if (!result.src) return <span role="status">QR-код не вдалося створити. Скористайтеся посиланням на весь екран.</span>
  return <img className="studio-qr" alt="QR-код для відкриття цього варіанта на телефоні" width={size} height={size} src={result.src} />
}
