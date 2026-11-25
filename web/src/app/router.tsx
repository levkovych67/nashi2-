import { lazy } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { RouteError } from '../ui/layout/RouteError'

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

// A pathless parent gives every route the Ukrainian error element (render errors, a chunk gone after a deploy).
export const appRoutes = [{ errorElement: <RouteError />, children: routes }]

export const createRouter = () => createBrowserRouter(appRoutes)
