# NatePad (iOS & Web)

A dual-platform PGP notepad + key manager. Features a native iOS SwiftUI app optimized for iOS 26 Liquid Glass, and a fully offline-first web application optimized for modern desktop and mobile browsers.

Both platforms are built, signed, and published via GitHub Actions:
- **iOS App**: Packaged into an unsigned `.ipa` attached to GitHub releases.
- **Web App**: Deployed automatically to [GitHub Pages](https://nphil.github.io/natepad/) and also attached as `Natepad-Web.zip` to releases.

---

## How the build works

1. You push code to a GitHub repo.
2. The workflow in `.github/workflows/build.yml` runs on a macOS runner.
3. It generates the Xcode project from `project.yml` (using xcodegen — keeps the project file out of git so it can't get mangled).
4. It imports your signing certificate and provisioning profile from GitHub secrets into a temporary keychain.
5. It archives, signs, and exports an `.ipa`.
6. The `.ipa` is uploaded as a workflow artifact you can download from the Actions tab.

---

## One-time setup

### 1. Push the code to a private GitHub repo

```bash
cd "NatePad"
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin git@github.com:YOUR_USER/natepad.git
git push -u origin main
```

### 2. Get your signing material ready

You said you have a certificate already. You'll need three things, all of which come from your Apple Developer account:

| Thing | What it is | Where to get it |
|---|---|---|
| **Signing certificate (.p12)** | Your developer cert + private key, exported from Keychain Access | Keychain Access → My Certificates → right-click your "Apple Development" cert → Export → save as `.p12` with a password |
| **Provisioning profile (.mobileprovision)** | Tells iOS this app is allowed to run on your device | developer.apple.com → Certificates, IDs & Profiles → Profiles → create a Development profile that includes your device's UDID and the bundle ID `com.natepad.app` |
| **Team ID** | 10-character ID from your Apple Developer membership page | developer.apple.com → Membership |

### 3. Add GitHub secrets

Repo → Settings → Secrets and variables → Actions → **New repository secret**. Add all six:

| Secret name | Value |
|---|---|
| `BUILD_CERT_P12_BASE64` | `base64 -i your-cert.p12 \| pbcopy` and paste |
| `BUILD_CERT_P12_PASSWORD` | The password you set when exporting the .p12 |
| `PROVISIONING_PROFILE_BASE64` | `base64 -i your-profile.mobileprovision \| pbcopy` and paste |
| `PROVISIONING_PROFILE_NAME` | The exact profile name from the developer portal (e.g. `Natepad Development`) |
| `DEVELOPMENT_TEAM` | Your 10-character team ID |
| `KEYCHAIN_PASSWORD` | Any random string — used to lock the temp keychain on the runner |

### 4. (If needed) match the bundle ID

The workflow expects `com.natepad.app`. If your provisioning profile uses a different bundle ID:
- Edit `project.yml` → `PRODUCT_BUNDLE_IDENTIFIER`
- Edit `.github/workflows/build.yml` → the line `:provisioningProfiles:com.natepad.app`

### 5. Trigger a build

Either push a commit, or go to **Actions → Build IPA → Run workflow**.

When it finishes, click the run → scroll to **Artifacts** → download `Natepad-ipa`. You'll get a zip containing `Natepad.ipa`.

---

## Installing the .ipa on your phone

A few options, pick whatever you already use:

- **Xcode → Devices & Simulators → Installed Apps → drag the .ipa in** (simplest if you have a Mac handy)
- **Apple Configurator 2** (free Mac app)
- **AltStore / SideStore** (sideload without a Mac)
- **TestFlight** — change the workflow's `method` in `ExportOptions.plist` to `app-store` and add an upload step (not currently in the workflow)

The `.ipa` will only install on devices listed in your provisioning profile.

---

## File map

| File | Purpose |
|---|---|
| `project.yml` | xcodegen spec — defines the Xcode project, target, deps, signing settings |
| `ExportOptions.plist` | Tells `xcodebuild -exportArchive` how to sign and package |
| `.github/workflows/build.yml` | The CI pipeline |
| `NatepadApp.swift` | App entry, root TabView with Liquid Glass tab bar, lock screen |
| `KeyStore.swift` | Keychain-backed key store (single JSON blob, encrypted at rest) |
| `KeyRecord.swift` | Key data model + helpers |
| `PGPService.swift` | Wrapper around ObjectivePGP (encrypt / decrypt / sign / verify / generate / parse) |
| `BiometricGate.swift` | Optional Face ID / Touch ID gate on launch |
| `NotepadView.swift` | Notepad tab — mode picker + per-mode input/output |
| `KeysView.swift` | Keys tab — list, generate, import, export, delete |
| `Sheets.swift` | Generate / Import / Export / Passphrase sheets |
| `Components.swift` | Shared UI: mode picker, recipient chips, glass cards, settings |

---

## iPhone UX choices

- **Bottom TabView** — iOS 26 renders this as the floating Liquid Glass capsule automatically.
- **Per-mode notepad state** — encrypt and decrypt don't share input/output. (Same fix as the web version.)
- **Sheets with `.presentationDetents` + `.regularMaterial` background** — feel native, breathe with the rest of the system.
- **Document picker** for importing `.asc`/`.txt`/`.key` files from Files / iCloud Drive / AirDrop.
- **ShareLink** for exporting — sends the armored block to any app (Mail, Messages, AirDrop, save to Files).
- **Swipe-to-delete** on key rows.
- **Paste button** on the input toolbar — keystrokes are precious on a phone.
- **Face ID lock** is opt-in via Settings tab.
- **`.glass` and `.glassProminent` button styles** for primary actions.

---

## Security model (same as web version)

- Private keys are passphrase-encrypted by OpenPGP itself (RSA-4096, AES-256).
- The whole key store sits inside iOS Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` — encrypted at rest by the OS, never iCloud-synced, doesn't migrate to a different device.
- Optional Face ID gate on app launch.
- Always export private keys to your own backup before relying on the app.

---

## Things you might want to tweak

- **Key generation type** — `PGPService.swift → generate()` uses RSA-4096. ObjectivePGP's ECC support is patchy.
- **Accent color** — `.tint(.indigo)` in `NatepadApp.swift`.
- **App icon** — drop a 1024×1024 png at `Assets.xcassets/AppIcon.appiconset/` or use Xcode's auto-generated icon set, then commit it.
- **Bundle ID / display name** — `project.yml`.

---

## Honest caveats about the code

This is a substantial scaffold and I haven't been able to compile it against ObjectivePGP's exact current API surface. Likely places you might need to iterate after the first build:

- **ObjectivePGP key fingerprint accessor** — the spelling of `key.publicKey?.fingerprint.description` may differ between versions; if it doesn't compile, check the latest [ObjectivePGP API](https://github.com/krzyzanowskim/ObjectivePGP).
- **Cleartext signature format** — the iOS sign mode produces an armored PGP MESSAGE rather than the cleartext `-----BEGIN PGP SIGNED MESSAGE-----` format the web app does. Verifiable in any PGP tool, just looks different.
- **Decrypt passphrase flow** — currently passes nil for passphrases on first attempt. If your private keys all have passphrases, you'll want to enhance `runDecrypt()` in `NotepadView.swift` to prompt up front (similar to how `runEncrypt` does).

If a specific compile error blocks you, paste it back here and I'll fix it inline.
