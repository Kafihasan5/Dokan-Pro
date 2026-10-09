import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { applyInitialTheme } from './utils/theme'
import { startMobileTables } from './utils/mobileTables'

applyInitialTheme()
startMobileTables()

// Chrome/Android: keep the install event so our own "ইনস্টল করুন" button can trigger it later.
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault()
  window.__dokanInstallEvent = e
  window.dispatchEvent(new Event('dokan:installready'))
})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
