const KEY = 'nashi.soundMuted'

export function readMuted(): boolean {
  try {
    return window.localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

export function writeMuted(muted: boolean) {
  try {
    window.localStorage.setItem(KEY, muted ? '1' : '0')
  } catch {
    // the choice then only lasts for this visit
  }
}
