# SuperApp

A tiny native Android app (Kotlin) that opens **Facebook**, **Instagram**, **X / Twitter** and **LinkedIn** in real `WebView`s — cookies, logins, media, uploads and downloads behave like a normal mobile browser.

| Home screen | Site screen |
|---|---|
| 2×2 grid of branded tiles (a green dot marks sites that are already loaded) | Full-screen WebView; toolbar with a menu drawer to switch sites (link settings and app version at its bottom), close, reload and share |

---

## Why it's fast

SuperApp used to be an Expo / React Native app. It was rewritten as a plain native app because the slowness was architectural:

| | Before (Expo / React Native) | Now (native) |
|---|---|---|
| APK size | 64.7 MB (4 ABIs of React Native, Hermes, Fabric) | ~75 KB, no native libraries |
| App start | Boot a JS engine + ~45 native libs before the first frame | One Activity, a few views |
| Opening a site | New WebView + full page load **every time** | WebView created once, then kept alive |
| Going back to a site | Cold reload, scroll position lost | Instant, exactly where you left it |
| While loading | Opaque white overlay until the page fully finished loading | Content shows as soon as it paints; slim progress bar |
| First tap | Paid for WebView engine start-up | Engine pre-warmed right after the first frame |

Other details: X loads `x.com` directly instead of going through two redirects, and if the page renderer crashes or is killed for memory, SuperApp returns to the grid instead of crashing.

### Battery

Pages only run while a site is on screen:

- **Leaving the app** (home button, screen off, another app) stops all audio and video, then pauses every page's scripts, timers and layout until you're back. SuperApp has no background services, alarms, location access or push, so it does nothing in the background.
- **On the home grid**, every site is paused the same way.
- **Sites you switch away from** stop their audio and video, embedded players (YouTube…) included, and WebView throttles their timers.
- **Battery Saver on:** only one site stays loaded (opening another closes the rest) and no WebView is pre-warmed. Battery Saver also turns on dark theme, which the sites follow (they always follow the system's light/dark setting); SuperApp's own screens are always dark.
- **Facebook's page fix** only reacts to changes in Facebook's pinned bars, so it costs next to nothing while the feed scrolls and updates.

---

## Stack

- **Kotlin** on the Android SDK, plus only `androidx.webkit` (for Chrome's client hints) — no Compose, AppCompat or start-up initializers
- **minSdk 26** (Android 8.0), **targetSdk 36** (Android 16), edge-to-edge, predictive back
- Android Gradle Plugin 9.4 (built-in Kotlin), Gradle 9.8 wrapper, JDK 17+
- Tests: JUnit + Robolectric

---

## Project layout

Everything lives inside [app/](app/) — it is a single-module Gradle project. The repo root only holds repo-wide metadata.

```
SuperApp/                                  # repo root: metadata only
├─ README.md, LICENSE, CODE_OF_CONDUCT.md, .gitignore, .gitattributes
├─ .github/workflows/
│  ├─ build-and-release.yml                # builds the signed APK when a GitHub Release is published
│  └─ setup-keystore.yml                   # one-time: prints keystore for repo secret
└─ app/                                    # the Android project — run all Gradle commands here
   ├─ settings.gradle.kts, build.gradle.kts, gradle.properties, proguard-rules.pro
   ├─ gradlew, gradlew.bat, gradle/wrapper/
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml
      │  ├─ java/com/example/superapp/
      │  │  ├─ MainActivity.kt             # switches screens, lifecycle, back navigation
      │  │  ├─ data/Sites.kt               # the sites shown on the home grid
      │  │  ├─ home/HomeScreen.kt          # tile grid + "already loaded" dots
      │  │  ├─ browser/
      │  │  │  ├─ Tabs.kt                  # one persistent WebView per site (the speed-up) + its pop-ups
      │  │  │  ├─ WebViews.kt              # WebView settings
      │  │  │  ├─ ChromeIdentity.kt        # look exactly like Chrome: user agent + client hints
      │  │  │  ├─ BrowserScreen.kt         # toolbar (menu, title, close, reload, share) + progress bar
      │  │  │  ├─ SiteDrawer.kt            # menu drawer: Home + every site, app version
      │  │  │  ├─ ShareLinks.kt            # strips tracking parameters from shared links
      │  │  │  ├─ PageTweaks.kt            # per-site page fixes (hides Facebook's "Open app" banners)
      │  │  │  ├─ IncomingLinks.kt         # site links tapped in other apps (WhatsApp…) + Android 12+ link approval
      │  │  │  ├─ ExternalLinks.kt         # which links leave the app, and how
      │  │  │  ├─ FileChooser.kt           # <input type="file"> uploads (files, camera, camcorder)
      │  │  │  ├─ CaptureProvider.kt       # private file for camera captures (minimal FileProvider)
      │  │  │  ├─ Downloads.kt             # downloads via DownloadManager
      │  │  │  └─ FullscreenVideo.kt       # fullscreen <video>
      │  │  └─ ui/                         # square tiles, site colors, system bars / insets
      │  └─ res/
      │     ├─ drawable/                   # vector icons: launcher layers, site glyphs, toolbar
      │     ├─ mipmap-anydpi/ic_launcher.xml  # adaptive launcher icon (+ themed monochrome layer)
      │     └─ layout/, values*/, xml/     # screens, colors, strings, themes, backup rules
      └─ test/java/com/example/superapp/   # JVM + Robolectric tests (mirrors the packages above)
```

---

## Run locally

Requires **JDK 17+** and the **Android SDK** (Android Studio installs both). Open the `app/` folder in Android Studio and press Run, or:

```powershell
cd app
.\gradlew installDebug          # build + install on a connected device/emulator
.\gradlew testDebugUnitTest     # unit + Robolectric tests
```

To add or change sites, edit [app/src/main/java/com/example/superapp/data/Sites.kt](app/src/main/java/com/example/superapp/data/Sites.kt):

```kotlin
Site(id = "youtube", name = "YouTube", url = "https://m.youtube.com/", colors = intArrayOf(0xFFFF0000.toInt()))
```

`icon` is optional — without one the tile shows the first letter of the name. Icons are vector drawables in `app/src/main/res/drawable/` (24×24 viewport, white). `linkHosts` (optional) lists the hosts whose links, tapped in other apps, open on that site; add the same hosts to the link intent filters in [AndroidManifest.xml](app/src/main/AndroidManifest.xml) (a test checks they match).

---

## Releasing an APK

APKs are built **only when a GitHub Release is published**. Pushes and merges to `main` never build or publish anything.

### Updates

The app never checks for updates itself. Release APKs are signed with a permanent key, and each release has a higher `versionCode`, so a user with SuperApp already installed just installs the newer APK and Android updates the app in place, keeping their logins.

That only holds while the key stays the same. It lives in the repository secret `DEBUG_KEYSTORE_BASE64` (alias `androiddebugkey`, password `android`), and a release build fails if the secret is missing or the APK ends up signed with any other key. **Never delete or replace that secret**: an APK signed with a different key can't update existing installs — people would have to uninstall first and lose their logins.

### One-time setup — generate a stable signing keystore

Only needed for a fresh fork; this repo already has its key.

1. Push this repo to GitHub.
2. Go to **Actions → "Setup signing keystore (run once)" → Run workflow**.
3. Open the finished run and find the **"Print setup instructions"** step. It prints a base64 block between `BEGIN` / `END` markers.
4. Go to **Settings → Secrets and variables → Actions → New repository secret**:
   - **Name:** `DEBUG_KEYSTORE_BASE64`
   - **Value:** the base64 block from step 3 (no surrounding whitespace)
5. Save, keep a private backup of the keystore, and delete the workflow run (its log contains the key).

The keystore is **never committed to the repo** — only in the secret.

### Publish a release

1. **Releases → Draft a new release**, create a tag with a semantic version such as `v1.2.3` (or `1.2.3`) on the commit you want to ship, and **Publish**.
2. [.github/workflows/build-and-release.yml](.github/workflows/build-and-release.yml) runs the tests and Android Lint, builds the minified release APK, signs it, and attaches it to that release as `SuperApp-v1.2.3.apk` (usually within a few minutes).

The tag version is stamped into the APK: `versionName` = `1.2.3`, `versionCode` = `10203` (major × 10000 + minor × 100 + patch), so every newer version installs as an update over older ones. If the tag isn't in that format, or minor/patch reach 100, the release build fails.

**Install it:** download the APK from the release, copy it to your phone and tap to install (allow "Install unknown apps" once). If SuperApp is already installed, this updates it in place — including over the old Expo-based versions, since the package id and signing key are unchanged.

### Build locally

```powershell
cd app
.\gradlew assembleRelease
```

The APK lands at `app/build/outputs/apk/release/SuperApp-release.apk`. Without extra configuration it is signed with your local debug key. To sign with the stable keystore instead, set `SUPERAPP_KEYSTORE` to the keystore path (and, if they differ from the Android defaults, `SUPERAPP_KEYSTORE_PASSWORD`, `SUPERAPP_KEY_ALIAS`, `SUPERAPP_KEY_PASSWORD`). Pass `-PversionCode=<n> -PversionName=<x.y.z>` to set the version.

---

## Before you publish — checklist

- `applicationId` in [app/build.gradle.kts](app/build.gradle.kts) — **must be globally unique** for the Play Store (e.g. `com.<you>.superapp`). Changing it makes it a different app: existing installs won't upgrade in place.
- App name: `app_name` in [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml).
- Launcher icon: vector layers `ic_launcher_background.xml` / `ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml` in `app/src/main/res/drawable/`.

---

## Notes & limitations

- **Android only** (8.0+). The old React Native code could in theory target iOS, but that path was never built or shipped.
- **Logins.** Sites refuse or redirect logins when they spot an embedded WebView, so SuperApp identifies itself exactly like Chrome for Android on the same engine version: Chrome's User-Agent string *and* matching User-Agent Client Hints (`Sec-CH-UA*` headers, `navigator.userAgentData`), which WebView would otherwise fill with "Android WebView". Sign-in buttons that open a pop-up ("Continue with Google/Apple") get a real window over the site, linked back to it like in Chrome; back or the page itself closes it. Pop-ups the user didn't tap for are blocked.
- **Cookies live inside SuperApp** (sandboxed by the package id), separate from Chrome. They are excluded from backups and device transfers.
- **Memory.** Opened sites stay loaded for instant switching (paused while hidden, see [Battery](#battery)). They all share one page renderer, so phones with less than 6 GB of RAM keep only the 2–3 most recently used sites loaded (opening another closes the least recently used one). If the renderer still crashes or is killed, the loaded sites close and SuperApp returns to the grid. To close a site yourself, tap **✕** (left of reload): it's unloaded and SuperApp returns to the grid; you stay logged in, and opening it again loads it fresh.
- **Uploads** use the system file picker, plus the camera/camcorder when a site accepts photos or videos.
- **Facebook's "Open app" nags** are hidden: its "Open app" buttons, and the bar pinned to the bottom of the screen when that button is all it holds (the comment box and dialogs stay). A small script ([PageTweaks.kt](app/src/main/java/com/example/superapp/browser/PageTweaks.kt)) is injected into facebook.com pages only, before Facebook's own scripts run. Similar fixes for other sites go in the same file.
- **Sharing** (top-right) sends the current page's link without tracking parameters: `utm_*`, `fbclid`, `igsh`, X's `s`/`t`, Facebook's `mibextid`/`__cft__`, LinkedIn's `trk`/`rcm` and similar are removed; parameters the page needs (post ids, `v=`…) stay.
- **Links from other apps.** Facebook, Instagram, X / Twitter and LinkedIn links tapped in WhatsApp, Gmail, Messages… open in SuperApp, on that site. If the site is already open, the link opens over it, so the page you were on stays as it was; back closes the link and returns to the app you tapped it in. Links to other sites still open in your browser.
  - **Android 12 and newer** only send these links to an app you approve. Once: open the menu → **Open links in SuperApp** (it shows Off / On) → **Add link** → tick every link → **Add**. On Android 11 and older, pick SuperApp (and **Always**) the first time you tap such a link.
  - If the official Facebook, Instagram, X or LinkedIn app is installed, it claims its links first and Android greys them out under **Add link**. In that app's App info → **Open by default**, choose **In your browser** (on older Android, turn off **Open supported links**) to free them for SuperApp.
  - Only plain `http`/`https` links on those sites' hosts are accepted, and they're reloaded over `https` from the checked host, so a crafted link can't slip another site into a site's tab.
- **Links to other apps** (`intent:`, `mailto:`, `tel:`, store links…) open only when you tap them, so pages can't bounce you out of SuperApp on their own.

---

## License

MIT — do whatever you want.
