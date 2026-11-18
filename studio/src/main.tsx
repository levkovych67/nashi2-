import './styles/theme.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { StudioRouter } from './app/router'

createRoot(document.getElementById('root')!).render(<StrictMode><StudioRouter /></StrictMode>)
