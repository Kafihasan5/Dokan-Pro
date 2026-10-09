# Server API

The Cloudflare Worker in `functions/cloudflare` serves the Firebase-callable API used by the Android and desktop apps. Shared handlers are in `functions/lib`.

## Deploy

From `backend/functions/cloudflare`, run `npx wrangler deploy`. Configure the existing Worker secrets (`FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY`, and `FIREBASE_DATABASE_URL`) in Cloudflare before deployment. The owner-shop lookup also calls the read-only Supabase `verify_app_license` RPC; its public anon key is already configured in the handler, like the client apps.

`findExistingOwnerShop` validates the licensed email and registered device, checks whether that email maps to a cloud shop, and returns only `hasExistingShop`. Access to shop contents still requires the shop's Master PIN.
