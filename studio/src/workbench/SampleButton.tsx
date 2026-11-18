import { useEffect, useRef, useState } from 'react'

interface Sample {
  url: string
  startSec: number
}
let active: HTMLAudioElement | null = null

export function SampleButton({ id }: { id: string }) {
  const [sample, setSample] = useState<Sample | null>()
  const [attempt, setAttempt] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [blocked, setBlocked] = useState(false)
  const audio = useRef<HTMLAudioElement | null>(null)
  useEffect(() => {
    let live = true
    fetch('/fixtures/fragments/' + id + '.json')
      .then((response) => {
        if (!response.ok) throw Error()
        return response.json() as Promise<Sample>
      })
      .then(
        (clip) => {
          if (live) setSample(clip)
        },
        () => {
          if (live) setSample(null)
        },
      )
    return () => {
      live = false
      const current = audio.current
      if (current) {
        current.onpause = null
        current.onended = null
        current.pause()
      }
      if (current === active) active = null
    }
  }, [id, attempt])
  if (sample === null)
    return (
      <button
        onClick={() => {
          setSample(undefined)
          setAttempt((n) => n + 1)
        }}
      >
        Повторити завантаження фрагмента
      </button>
    )
  function toggle() {
    if (!sample) return
    if (playing) {
      audio.current?.pause()
      return
    }
    active?.pause()
    const next = audio.current ?? new Audio(sample.url)
    audio.current = active = next
    next.onpause = next.onended = () => setPlaying(false)
    next.currentTime = sample.startSec
    // Call play in the click handler so Safari retains the user's activation.
    void next.play().then(
      () => {
        if (active === next) {
          setBlocked(false)
          setPlaying(!next.paused)
        }
      },
      () => {
        if (active === next) {
          setPlaying(false)
          setBlocked(true)
        }
      },
    )
  }
  return (
    <>
      <button disabled={!sample} aria-pressed={playing} onClick={toggle}>
        {playing ? 'Зупинити' : '▷ Тестовий фрагмент'}
      </button>
      {blocked && (
        <p role="status">
          Відтворення недоступне. Натисніть кнопку, щоб спробувати знову.
        </p>
      )}
    </>
  )
}
