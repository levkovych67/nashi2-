import { useState } from 'react'
import { useDesign } from '../../core/design/config'
import { useCopy } from '../../core/i18n/copy'

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
 * «Біля мене». map.initial-view = UKRAINE_WITH_NEAR_ME_PROMPT makes it a prominent prompt on the first visit; it only
 * ever asks for the position when the user presses it, and a refusal leaves the map as it is.
 */
export function NearMeButton({ onClick }: { onClick(): void }) {
  const t = useCopy()
  const wanted = useDesign().variant('map.initial-view') === 'UKRAINE_WITH_NEAR_ME_PROMPT'
  const [seen, setSeen] = useState(wasSeen)
  const prompt = wanted && !seen
  return (
    <button
      type="button"
      className={prompt ? 'n-map-button n-map-button--primary' : 'n-map-button'}
      data-testid="near-me"
      data-prompt={prompt}
      onClick={() => {
        markSeen()
        setSeen(true)
        onClick()
      }}
    >
      {t('map.nearMe')}
    </button>
  )
}
