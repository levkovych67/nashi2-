import type { ReactNode } from 'react'
import { isSafeHttpUrl } from '../../core/musician/view'

/** An external link: only http(s) addresses are rendered, always in a new tab without access to this page. */
export function ExternalLink({ href, children, className }: { href: string | null | undefined; children: ReactNode; className?: string }) {
  if (!isSafeHttpUrl(href)) return null
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" className={className}>
      {children}
    </a>
  )
}

export const hostOf = (url: string): string => {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}
