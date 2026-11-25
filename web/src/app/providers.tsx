import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { DesignContext, loadDesignConfig, withDecisionOverrides } from '../core/design/config'
import type { DecisionsFile } from '../core/design/types'
import { AudioRoot } from '../core/audio/AudioRoot'
import { LikesRoot } from '../core/likes/LikesRoot'
import { CopyContext, createCopy } from '../core/i18n/copy'
import decisions from '../design/decisions.json'
import copyOverrides from '../design/copy.uk.json'

declare global {
  interface Window {
    /** e2e / mock runs only: decisions over src/design/decisions.json, set before the app loads (e.g. APPROVED ones). */
    __nashiDecisions?: DecisionsFile['decisions']
  }
}

// Never read in a production build: the condition is false there and the lookup is dropped.
const TEST_HOOKS = import.meta.env.DEV || import.meta.env.VITE_API_MOCK === '1'
const design = loadDesignConfig(
  withDecisionOverrides(decisions as DecisionsFile, TEST_HOOKS ? window.__nashiDecisions : undefined),
)
const copy = createCopy(copyOverrides)

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: 1 } } }))
  return (
    <QueryClientProvider client={client}>
      <DesignContext.Provider value={design}>
        <CopyContext.Provider value={copy}>
          <AudioRoot>
            <LikesRoot>{children}</LikesRoot>
          </AudioRoot>
        </CopyContext.Provider>
      </DesignContext.Provider>
    </QueryClientProvider>
  )
}
