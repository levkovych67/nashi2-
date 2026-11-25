import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { DesignContext, loadDesignConfig } from '../core/design/config'
import type { DecisionsFile } from '../core/design/types'
import { AudioRoot } from '../core/audio/AudioRoot'
import { CopyContext, createCopy } from '../core/i18n/copy'
import decisions from '../design/decisions.json'
import copyOverrides from '../design/copy.uk.json'

const design = loadDesignConfig(decisions as DecisionsFile)
const copy = createCopy(copyOverrides)

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: 1 } } }))
  return (
    <QueryClientProvider client={client}>
      <DesignContext.Provider value={design}>
        <CopyContext.Provider value={copy}>
          <AudioRoot>{children}</AudioRoot>
        </CopyContext.Provider>
      </DesignContext.Provider>
    </QueryClientProvider>
  )
}
