import { useId, useState } from 'react'
import { useDesign } from '../../core/design/config'
import { useCopy } from '../../core/i18n/copy'
import '../icon.css'

const SEEN_KEY = 'nashi.nearMeSeen'

function wasSeen(): boolean {
  try {
    return window.localStorage.getItem(SEEN_KEY) === '1'
  } catch {
    return false
  }
}

function markSeen() {
  try {
    window.localStorage.setItem(SEEN_KEY, '1')
  } catch {
    // the prompt just shows again next time
  }
}

/**
 * «Біля мене». map.initial-view = UKRAINE_WITH_NEAR_ME_PROMPT makes it a prominent prompt with the handoff caption
 * «Знайдіть музикантів зі своєї області» on the first visit; it only ever asks for the position when the user presses
 * it, and a refusal leaves the map as it is.
 */
export function NearMeButton({ onClick }: { onClick(): void }) {
  const t = useCopy()
  const hintId = useId()
  const wanted = useDesign().variant('map.initial-view') === 'UKRAINE_WITH_NEAR_ME_PROMPT'
  const [seen, setSeen] = useState(wasSeen)
  const prompt = wanted && !seen
  // one structure with and without the caption, so pressing the prompt keeps the same (focused) button
  return (
    <span className="n-near-me">
      {prompt && (
        <small className="n-near-me__hint" id={hintId} data-testid="near-me-hint">
          {t('map.nearMePrompt')}
        </small>
      )}
      <button
        type="button"
        className={prompt ? 'n-map-button n-map-button--primary' : 'n-map-button'}
        data-testid="near-me"
        data-prompt={prompt}
        aria-describedby={prompt ? hintId : undefined}
        onClick={() => {
          markSeen()
          setSeen(true)
          onClick()
        }}
      >
        <span className="n-icon n-icon--pin" aria-hidden="true" />
        {t('map.nearMe')}
      </button>
    </span>
  )
}
