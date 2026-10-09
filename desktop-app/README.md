# Dokan Pro Desktop

Electron desktop wrapper for the Dokan Pro web application. It loads a local production build, keeps Firebase device persistence on a stable local origin, and opens external links in the system browser.

## Requirements

- Node.js 22 or newer
- npm

## Develop

```sh
npm install
npm run dev
```

## Build installers

```sh
npm run build:desktop:mac:arm64  # Apple Silicon Mac
npm run build:desktop:mac:x64    # Intel Mac
npm run build:desktop:win        # Windows x64 portable ZIP
```

Build outputs are written to `release/`. The Windows ZIP can be extracted and `Dokan Pro.exe` launched directly.
