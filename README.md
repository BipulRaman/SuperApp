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
│  ├─ build-and-release.yml        # CI: builds signed APK; tag v* → GitHub Release
│  └─ setup-keystore.yml           # one-time: prints keystore for repo secret
└─ app/                            # Expo project root — run all npm/expo commands here
   ├─ index.js                     # Expo entry
   ├─ package.json
   ├─ app.json                     # Expo config (name, package id, icon, splash, version)
   ├─ eas.json                     # EAS build profiles (preview = APK, production = AAB)
   ├─ babel.config.js
   ├─ assets/                      # icons, splash, favicon (referenced by app.json)
   ├─ scripts/
   │  └─ generate-icons.ps1
   └─ src/
      ├─ App.js                    # navigation root
      ├─ screens/
      │  ├─ HomeScreen.js          # tile grid
      │  └─ WebViewScreen.js       # native WebView + toolbar + back handler
      └─ data/
         └─ apps.js                # list of apps shown on the home grid
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

### One-time setup — generate a stable signing keystore

For users to install a newer APK over an older one **without uninstalling**, every build must be signed with the **same key**. By default each CI runner generates a fresh ephemeral debug key, so APK #2 wouldn't install over APK #1.

Fix it once:

1. Push this repo to GitHub.
2. Go to **Actions → "Setup signing keystore (run once)" → Run workflow**.
3. Open the finished run and find the **"Print setup instructions"** step. It prints a base64 block between `BEGIN` / `END` markers.
4. Go to **Settings → Secrets and variables → Actions → New repository secret**:
   - **Name:** `DEBUG_KEYSTORE_BASE64`
   - **Value:** the base64 block from step 3 (no surrounding whitespace)
5. Save.

The build pipeline now decodes that secret on every run and signs the APK with it. The keystore is **never committed to the repo** — only in the secret. If you ever lose the secret, regenerate it with the setup workflow, but be aware that already-installed APKs will need to be uninstalled before the next update will install.

### Option A — GitHub Actions (recommended)

A single workflow at [.github/workflows/build-and-release.yml](.github/workflows/build-and-release.yml) handles everything:

| Trigger | What it does |
|---|---|
| Push to `main` / PR / **Run workflow** button | Builds a debug APK signed with the stable keystore and uploads it as a workflow artifact |
| Push a tag matching `v*` (e.g. `v1.0.0`) | Same build **plus** publishes a GitHub Release with the APK attached as `SuperApp-v1.0.0.apk` |

**Versioning is automatic:**

- `versionName` (the user-visible "1.2.3") comes from the git tag (`v1.2.3`). On non-tag pushes it stays at whatever `app/app.json` says.
- `versionCode` (the integer Android uses to detect updates) is set to `github.run_number` for every build. Each CI run is strictly higher than the previous one, so Android always treats the new APK as an upgrade.

You don't need to edit `app.json` to bump versions — just push a tag.

**No secrets required** — uses the built-in `GITHUB_TOKEN`.

**Get an APK now:**

1. Push this repo to GitHub (after running the keystore setup workflow above).
2. Wait for the build run, or trigger manually: **Actions → Build & Release APK → Run workflow**.
3. Open the finished run → **Artifacts** → download the `.apk`.
4. Copy to your phone, tap to install (allow "Install unknown apps" once).
5. Push a newer build → install it the same way → it upgrades the existing app in place, your data is preserved.

**Cut a public release with a downloadable APK:**

```powershell
git tag v1.0.0
git push origin v1.0.0
```

The workflow builds the APK (with `versionName=1.0.0`) and creates a GitHub Release at `https://github.com/<you>/SuperApp/releases/tag/v1.0.0` with the APK as a download asset.

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
- `expo.icon` — path to a 1024×1024 PNG (or remove the line to use the Expo default)

`expo.version` and `expo.android.versionCode` are bumped automatically by CI — `expo.version` from the git tag (e.g. `v1.2.3` → `1.2.3`) and `versionCode` from the GitHub Actions run number. You don't need to touch them.

---

## Notes & limitations

- **Login walls.** Instagram / Facebook sometimes detect WebViews and ask you to "open in app." If that happens, swap the User‑Agent in [app/src/screens/WebViewScreen.js](app/src/screens/WebViewScreen.js) for a desktop one, or use OAuth flows for production apps.
- **Cookies are shared per app** (sandboxed by your package id) but isolated from the system Chrome — so logins live inside SuperApp only.
- **iOS** is supported by the same code; just run `eas build -p ios` (requires an Apple Developer account).

---

## License

MIT — do whatever you want.
