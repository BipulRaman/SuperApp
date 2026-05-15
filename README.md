# SuperApp

A tiny React Native (Expo) "super app" that opens **Facebook**, **Instagram**, **X / Twitter** and **LinkedIn** in a real native `WebView` — not an `iframe` — so cookies, logins, media playback and pull‑to‑refresh all behave like a normal mobile browser.

| Home screen | WebView screen |
|---|---|
| 2×2 grid of branded tiles | Native WebView with mobile User‑Agent + toolbar |

---

## Stack

- **Expo SDK 51** + **React Native 0.74**
- **React Navigation** (native stack)
- **react-native-webview** (cookies, JS, mobile UA, pull‑to‑refresh)
- No backend; all state is local

---

## Project layout

The entire Expo project lives inside [app/](app/). The repo root only holds repo-wide metadata (`README.md`, `.gitignore`, `.github/`).

```
SuperApp/                          # repo root
├─ README.md
├─ .gitignore
├─ .github/workflows/
│  └─ build-and-release.yml    # CI: builds APK on every push; tag v* → GitHub Release
└─ app/                          # Expo project root — run all npm/expo commands here
   ├─ index.js                   # Expo entry
   ├─ package.json
   ├─ app.json                   # Expo config (name, package id, icon, splash)
   ├─ eas.json                   # EAS build profiles (preview = APK, production = AAB)
   ├─ babel.config.js
   ├─ assets/                    # icons, splash, favicon (referenced by app.json)
   ├─ scripts/
   │  └─ generate-icons.ps1
   └─ src/
      ├─ App.js                  # navigation root
      ├─ screens/
      │  ├─ HomeScreen.js        # tile grid
      │  └─ WebViewScreen.js     # native WebView + toolbar + back handler
      └─ data/
         └─ apps.js              # list of apps shown on the home grid
```

---

## Run locally

```powershell
cd app
npm install
npx expo start
```

Scan the QR code with **Expo Go** on your phone, or press `a` for an Android emulator.

To add or change apps, edit [app/src/data/apps.js](app/src/data/apps.js):

```js
{ id: 'youtube', name: 'YouTube', url: 'https://m.youtube.com/', color: '#FF0000', initial: 'YT' }
```

---

## Build an Android APK

### Option A — GitHub Actions (recommended)

A single workflow at [.github/workflows/build-and-release.yml](.github/workflows/build-and-release.yml) handles everything:

| Trigger | What it does |
|---|---|
| Push to `main` / PR / **Run workflow** button | Builds a debug APK and uploads it as a workflow artifact (`SuperApp-<sha>.apk`) |
| Push a tag matching `v*` (e.g. `v1.0.0`) | Same build **plus** publishes a GitHub Release with the APK attached as `SuperApp-v1.0.0.apk` |

**No secrets required** — uses the built-in `GITHUB_TOKEN`.

**Get an APK now:**

1. Push this repo to GitHub.
2. Wait for the run, or trigger manually: **Actions → Build & Release APK → Run workflow**.
3. Open the finished run → **Artifacts** → download the `.apk`.
4. Copy to your phone, tap to install (allow "Install unknown apps" once).

**Cut a public release with a downloadable APK:**

```powershell
git tag v1.0.0
git push origin v1.0.0
```

The workflow builds the APK and creates a GitHub Release at `https://github.com/<you>/SuperApp/releases/tag/v1.0.0` with the APK as a download asset.

### Option B — Build locally

Requires **JDK 17** and **Android Studio** (with the Android SDK).

```powershell
cd app
npm install
npx expo prebuild --platform android --no-install --clean
cd android
.\gradlew assembleDebug
```

The APK lands at `app/android/app/build/outputs/apk/debug/app-debug.apk`.

For a signed release APK / AAB, generate a keystore once and configure signing in `app/android/gradle.properties` — easiest path is `eas build` (Option A above), which manages the keystore for you.

---

## Before you publish — checklist

Edit [app/app.json](app/app.json) and bump these for any public release:

- `expo.name` — display name on the phone
- `expo.android.package` — **must be globally unique** (e.g. `com.<you>.superapp`). Once on the Play Store this can never change.
- `expo.version` — user‑visible version
- `expo.icon` — path to a 1024×1024 PNG (or remove the line to use the Expo default)

---

## Notes & limitations

- **Login walls.** Instagram / Facebook sometimes detect WebViews and ask you to "open in app." If that happens, swap the User‑Agent in [app/src/screens/WebViewScreen.js](app/src/screens/WebViewScreen.js) for a desktop one, or use OAuth flows for production apps.
- **Cookies are shared per app** (sandboxed by your package id) but isolated from the system Chrome — so logins live inside SuperApp only.
- **iOS** is supported by the same code; just run `eas build -p ios` (requires an Apple Developer account).

---

## License

MIT — do whatever you want.
