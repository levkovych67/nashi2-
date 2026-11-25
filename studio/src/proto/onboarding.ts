const key = 'nashi:onboarding:v1'

export function shouldShowOnboarding(storage: Pick<Storage, 'getItem'> | null) {
  try {
    return storage !== null && storage.getItem(key) !== '1'
  } catch {
    return false
  }
}

export function markOnboardingSeen(storage: Pick<Storage, 'setItem'> | null) {
  try {
    storage?.setItem(key, '1')
  } catch {
    /* The open prototype still records the dismissal in its own state. */
  }
}

export function browserStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}
