import { lazy } from 'react'
import { createBrowserRouter } from 'react-router-dom'

const MapPage = lazy(() => import('../pages/MapPage'))
const MusicianPage = lazy(() => import('../pages/MusicianPage'))
const SuggestPage = lazy(() => import('../pages/SuggestPage'))
const AboutPage = lazy(() => import('../pages/AboutPage'))
const NotFoundPage = lazy(() => import('../pages/NotFoundPage'))

export const routes = [
  { path: '/', element: <MapPage /> },
  { path: '/m/:slug', element: <MusicianPage /> },
  { path: '/suggest', element: <SuggestPage /> },
  { path: '/about', element: <AboutPage /> },
  { path: '*', element: <NotFoundPage /> },
]

export const createRouter = () => createBrowserRouter(routes)
