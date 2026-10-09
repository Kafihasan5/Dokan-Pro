import { initializeApp } from 'firebase/app';
import { getDatabase } from 'firebase/database';
import { getAuth, browserSessionPersistence, browserLocalPersistence, setPersistence } from 'firebase/auth';
import { getFunctions, httpsCallableFromURL } from 'firebase/functions';
import { initializeAppCheck, ReCaptchaEnterpriseProvider } from 'firebase/app-check';

// Firebase web config is public by design; access control lives in firebase/database.rules.json.
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || 'AIzaSyCr9mupOPAeN95gvzr92Wq-qx-OMPs2peI',
  authDomain: 'dokan-pro-837f3.firebaseapp.com',
  databaseURL: 'https://dokan-pro-837f3-default-rtdb.asia-southeast1.firebasedatabase.app',
  projectId: 'dokan-pro-837f3',
  storageBucket: 'dokan-pro-837f3.firebasestorage.app',
  messagingSenderId: '1076298030517',
  appId: '1:1076298030517:web:01595522331855b8550dfc',
};

const app = initializeApp(firebaseConfig);

// 'secure' = PINs are checked by Cloud Functions and the database is locked by rules.
// 'legacy' = old client-side PIN check against the old open database (set VITE_AUTH_MODE=legacy).
export const SECURE_AUTH = import.meta.env.VITE_AUTH_MODE !== 'legacy';

const appCheckKey = import.meta.env.VITE_RECAPTCHA_ENTERPRISE_KEY;
if (appCheckKey) {
  initializeAppCheck(app, {
    provider: new ReCaptchaEnterpriseProvider(appCheckKey),
    isTokenAutoRefreshEnabled: true,
  });
}

export const db = getDatabase(app);
export const firebaseAuth = getAuth(app);
export const functions = getFunctions(app, 'asia-southeast1');

// Server endpoints run on a Cloudflare Worker (firebase/functions/cloudflare) so Firebase can stay on the free plan.
export const API_BASE = import.meta.env.VITE_API_BASE || 'https://dokan-pro-api.dokanpro.workers.dev/api';
export const callable = (name) => httpsCallableFromURL(functions, `${API_BASE}/${name}`);

// "Remember this device" keeps the session across browser restarts; otherwise it ends with the tab.
export function setRememberDevice(remember) {
  return setPersistence(firebaseAuth, remember ? browserLocalPersistence : browserSessionPersistence);
}

export default app;
