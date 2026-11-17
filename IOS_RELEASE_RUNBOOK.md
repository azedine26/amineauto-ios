# AmineAuto iOS app — release runbook

App: **AmineAuto** · bundle **com.amineauto.app** · loads **https://amineauto.com** (same WebView model as Android).
Firebase project: **amineauto-app** (number 167855592293). Capacitor 7, plugins: app, geolocation, keyboard, push-notifications.

The build & submit **must happen on macOS + Xcode**. This VPS (Linux) can scaffold and configure, but cannot run `pod install`, sign, or upload. Options if you have no Mac: a borrowed Mac, a cloud Mac (MacStadium / MacinCloud), or a **GitHub Actions `macos-14` runner** (recommended — repeatable, ~free for private builds).

---

## 0) What ONLY you can provide (gather these first)

**Apple Developer (developer.apple.com):**
1. **App ID** for `com.amineauto.app` → enable **Push Notifications** + **Associated Domains**.
2. **APNs Authentication Key** (Keys → +, "Apple Push Notifications service (APNs)") → download the **`.p8`** once; note the **Key ID** and your **Team ID**. (This is what lets FCM push to iOS.)
3. **Distribution certificate** + an **App Store provisioning profile** for the App ID (or let Xcode "Automatically manage signing").

**App Store Connect:** create the app record (name, bundle, SKU), prepare screenshots + privacy answers.

**Firebase console (amineauto-app):**
4. **Add app → iOS**, bundle `com.amineauto.app` → download **`GoogleService-Info.plist`**.
5. Cloud Messaging → **upload the APNs `.p8`** (Key ID + Team ID). Without this, iOS push stays silent.

---

## 1) Linux-side (I can do now, no Mac needed)

- Add the platform dep: `npm install @capacitor/ios@^7.0.0`.
- Serve the **Universal Links** file on the site: `https://amineauto.com/.well-known/apple-app-site-association` (JSON, `application/json`, no extension), content:
  ```json
  { "applinks": { "apps": [], "details": [ { "appID": "<TEAM_ID>.com.amineauto.app", "paths": ["*"] } ] } }
  ```
  (mirror of the Android `assetlinks.json`; fill `<TEAM_ID>`). This is an nginx/backend add on the main site.
- `GoogleService-Info.plist` goes into `ios/App/App/` once the platform is added (step 2) — keep it **out of git** like the Android `google-services.json` (store in `/opt/amineauto-secrets/firebase/`).

## 2) On the Mac (one-time)

```
cd amineauto-capacitor
npm install
npx cap add ios            # scaffolds ios/, runs pod install
# copy GoogleService-Info.plist into ios/App/App/
npx cap sync ios
npx cap open ios           # opens Xcode
```
In **Xcode → Signing & Capabilities** (target App):
- Team = your Apple Developer team; bundle `com.amineauto.app`.
- **+ Capability → Push Notifications**.
- **+ Capability → Associated Domains** → `applinks:amineauto.com`.
- **+ Capability → Background Modes** → check **Remote notifications**.
- Confirm `GoogleService-Info.plist` is in the target (Build Phases → Copy Bundle Resources).

`Info.plist` notes: set a user-facing app name; add `NSLocationWhenInUseUsageDescription` (geolocation plugin). Icons/splash: reuse the Android assets (1024² app icon for the store).

## 3) Verify before submit
- Run on a real device (push needs a device, not the simulator): permission prompt appears, a token registers → check it lands in `push_devices` (kind `IOS`) via `POST /push/native`.
- Tap a test push → app opens the `data.url` (NativePushRegister already handles this — it's web-shared, so iOS gets it for free).
- Universal Link: open an `https://amineauto.com/...` link → opens the app (needs the AASA file live + Associated Domains).

## 4) Submit
- Xcode → Product → Archive → Distribute App → App Store Connect → Upload.
- In App Store Connect: attach the build, fill listing + privacy, submit for review.

---

## Already done / reused (no work)
- Native push **backend + frontend are live in prod** (FCM key mounted) — iOS just needs its APNs key in Firebase + the capability in the app; `push.ts` already stores `kind: IOS`.
- `NativePushRegister.tsx` + `nativeApp.ts` are platform-agnostic (web/Android/iOS) — no new frontend code for iOS push or the notification-tap deep link.
- Google Sign-In native bridge exists (`@capgo/capacitor-social-login`); iOS needs its own OAuth client (iOS type) in the Google project + the reversed-client-id URL scheme — **only if** you want Google login in the iOS app (email OTP already works, so this is optional for v1).

## Option 3 — GitHub Actions macOS CI (chosen)

Workflow: `.github/workflows/ios.yml` (builds on `macos-14`, uploads to **TestFlight**). It generates `ios/` fresh each run, drops in our committed `ios-config/App.entitlements` (push + `applinks:amineauto.com`) and the Firebase plist, signs, archives, and uploads.

**Prereqs (one-time):**
1. **Put this Capacitor project on GitHub** — it currently has **no git remote**. Create a private repo (e.g. `azedine26/amineauto-ios`) and push it. Actions run there.
2. Ensure `ios/` is in `.gitignore` (CI regenerates it), and run `npm install` once locally so `package-lock.json` includes `@capacitor/ios` (I added it to `package.json`).
3. **Deploy the AASA file** on the main site (see §1) with your real Team ID.

**GitHub secrets to add** (repo → Settings → Secrets and variables → Actions):

| Secret | What |
|---|---|
| `IOS_GOOGLE_SERVICE_PLIST_B64` | `base64 -i GoogleService-Info.plist` |
| `IOS_DIST_CERT_P12_B64` | base64 of your Apple **distribution cert** `.p12` |
| `IOS_DIST_CERT_PASSWORD` | that `.p12`'s password |
| `IOS_PROVISION_PROFILE_B64` | base64 of the **App Store provisioning profile** (`.mobileprovision`) |
| `IOS_PROFILE_NAME` | the profile's name (as shown in the portal) |
| `IOS_TEAM_ID` | your Apple **Team ID** |
| `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_API_KEY_P8_B64` | an **App Store Connect API key** (Users & Access → Integrations → API keys) — for the TestFlight upload |

Run it from the Actions tab (**Run workflow**) or push a tag `ios-v1.1.0`. First green run → the build lands in **TestFlight**; promote to the store from App Store Connect.

## Open decisions
- **minimum iOS version** (Capacitor 7 default: iOS 14+).
- **minimum iOS version** (Capacitor 7 default is iOS 14+).
- Google Sign-In in the iOS app now, or defer (email OTP covers login)?
