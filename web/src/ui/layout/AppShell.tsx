import type { ReactNode } from 'react'
import { useDesign } from '../../core/design/config'
import { resolveVariant } from '../variants'
import './layout.css'

/** Page frame: the navigation chosen in nav.structure above the page content. */
export function AppShell({ children }: { children: ReactNode }) {
  const Nav = resolveVariant('nav.structure', useDesign())
  return (
    <div className="n-app">
      {Nav && <Nav />}
      <main className="n-main">{children}</main>
    </div>
  )
}
