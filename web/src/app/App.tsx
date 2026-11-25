import { Suspense } from 'react'
import { RouterProvider } from 'react-router-dom'
import { Providers } from './providers'
import { createRouter } from './router'

const router = createRouter()

export default function App() {
  return (
    <Providers>
      <Suspense fallback={null}>
        <RouterProvider router={router} />
      </Suspense>
    </Providers>
  )
}
