import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './app/App'
import './index.css'

async function start() {
  if (import.meta.env.VITE_API_MOCK === '1') {
    const { worker } = await import('./api/mocks/browser')
    await worker.start({ onUnhandledRequest: 'bypass' })
  }
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}

void start()
