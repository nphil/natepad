# NatePad iOS — Project Reference

## What this is
PGP notepad iPhone app. Encrypt/decrypt/sign/verify messages, manage PGP keys (generate, import, export).
Target: iOS 26, Liquid Glass UI, sideloaded via AltStore (no App Store).

## Repo
`https://github.com/nphil/natepad` — every push to `main` triggers CI and produces an IPA.
IPA download: GitHub Releases → "latest" pre-release (overwritten on each main push).

## Build / CI
- **Never run Xcode locally** — use GitHub Actions (`.github/workflows/build.yml`)
- xcodegen reads `project.yml` → generates `Natepad.xcodeproj` in CI (never committed)
- Unsigned IPA: `CODE_SIGNING_ALLOWED=NO` — AltStore signs at sideload time
- Runner: `macos-15`, auto-selects latest Xcode (currently Xcode 26.3 with iOS 26 SDK)
- **Always commit + push after any meaningful change** — CI does the rest
- `CFBundleShortVersionString` and `CFBundleVersion` in `project.yml` are stamped automatically by CI — do not edit them manually

## Versioning (automatic, conventional commits)
Every push to `main` is analysed and versioned automatically:

| Commit prefix | Version bump | Example |
|---|---|---|
| `feat:` | **minor** | `2.1.x → 2.2.0` |
| `fix:` / `perf:` / `refactor:` | **patch** | `2.1.0 → 2.1.1` |
| `BREAKING CHANGE` / `feat!:` / `fix!:` | **major** | `2.x.x → 3.0.0` |
| `chore:` / `docs:` / `ci:` / `style:` / `test:` | none — rolling build only | |
| free-form message (no prefix) | **patch** | |

A versioned GitHub Release (`v2.1.1`) is created only when the bump is non-none.
Feather/AltStore `apps.json` is updated automatically after each versioned release.
A rolling `latest` prerelease is always updated on every push (for quick sideloading).

## Android app (`android/`)
Jetpack Compose + Material You, tablet-adaptive (NavigationBar/Rail/PermanentDrawer).
Bouncy Castle PGP, EncryptedSharedPreferences key storage, BiometricPrompt lock.

- CI: `.github/workflows/build-android.yml` — builds **signed release APK** on every push to `main`
- Both workflows run the same semver logic, so APK and IPA carry the same version
- The Android workflow waits for the iOS workflow to create the `vX.Y.Z` release, then attaches the APK
- Rolling `android-latest` prerelease refreshed on every push
- **Signing**: `android/app/natepad-release.keystore` (PKCS12, alias `natepad`, password in `build.gradle.kts`)
  is **committed on purpose** — keeps the APK signature identical across CI runs so
  Obtainium/sideload updates install without signature-mismatch errors. Never delete or regenerate it:
  a new keystore breaks updates for every existing install (users would have to uninstall/reinstall).
- Obtainium: add source URL `https://github.com/nphil/natepad` — it tracks `vX.Y.Z` releases and
  auto-picks the single `.apk` asset
- `versionCode` = commit count, `versionName` = semver — both injected by CI via `-P` flags;
  Compose BOM 2024.06.00 ships material3 1.2.1 — **do not use** material3 1.3+ APIs (e.g. `MenuAnchorType`)

## File map

| File | Purpose |
|---|---|
| `NatepadApp.swift` | `@main`, gradient background ZStack, biometric background-lock notifications, tab icons, BrandMark, LockScreen |
| `Theme.swift` | `AppTheme` enum (5 themes), `ThemeManager` ObservableObject, `ThemeSwatch` view, `Color(hex:)` extension |
| `NotepadView.swift` | Encrypt/Decrypt/Sign/Verify UI, per-mode state dict, keyboard dismiss (scrollDismissesKeyboard + Done toolbar + tap-outside) |
| `KeysView.swift` | Key list, generate/import/export/delete actions |
| `Sheets.swift` | `GenerateKeySheet`, `ImportKeySheet`, `ExportKeySheet`, `PassphraseSheet`, `PickerDelegate`, `topMostViewController()` |
| `Components.swift` | `ModePicker`, `StatusBanner`, `RecipientChips`, `GlassCard`, `SettingsView` (theme picker + security toggle) |
| `PGPService.swift` | Static enum wrapping ObjectivePGP — parseKeys, generate, encrypt, decrypt, sign, verify |
| `KeyRecord.swift` | `struct KeyRecord: Identifiable, Codable` — fingerprint, userIDs, armored keys |
| `KeyStore.swift` | `@MainActor ObservableObject` — Keychain persistence, add/delete keys |
| `BiometricGate.swift` | Face ID / Touch ID gate, `requireUnlock` in UserDefaults, `lockIfRequired()` for background-lock |
| `project.yml` | xcodegen spec — deployment target, signing, SPM deps, asset catalog |
| `Assets.xcassets/` | App icon (AppIcon.png 1024×1024, blue padlock "Lock/Vault") |

## Key dependencies
- **ObjectivePGP** 0.99.4 via SPM (`https://github.com/krzyzanowskim/ObjectivePGP`)
- No other third-party deps

## Architecture rules
- **No custom `.glass` / `.glassProminent` button style definitions** — iOS 26 SDK provides these natively. Adding custom ones causes "ambiguous use of 'glass'" compile error.
- **Gradient background**: applied in `NatepadApp` as a full-screen `LinearGradient` ZStack behind the TabView. All Forms/Lists use `.scrollContentBackground(.hidden)`. GlassCards use `.ultraThinMaterial` to let gradient show through.
- **Theming**: `ThemeManager` is a `@StateObject` in `NatepadApp`, injected as `@EnvironmentObject`. All views that need accent color or gradient read from it. `.tint()` is set at root level.
- **Biometric lock on background**: `NatepadApp` listens for `UIApplication.didEnterBackgroundNotification` → calls `biometric.lockIfRequired()`. On `willEnterForegroundNotification`, calls `biometric.unlock()` if `requireUnlock` is true.
- Per-mode notepad state: `modeState: [NotepadMode: ModeState]` dict — each mode has its own `input` and `output` strings, prevents content bleed.
- File import: present `UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)` directly from `topMostViewController()`.
  - **`[.item]`** — most permissive UTI. `.data` or `.text` silently disable Open for `.asc` files.
  - **`asCopy: true`** — iOS copies file to sandbox; avoids security-scoped resource issues with iCloud files.
  - Self-retaining `PickerDelegate` — `delegate` is `weak`; delegate sets `strongSelf = self` in init, releases in callbacks.
  - Do NOT use SwiftUI `.fileImporter` (drops callback inside sheets) or `.background { UIViewControllerRepresentable }` (host VC not in proper hierarchy).
- Keys stored in iOS Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.

## Common tasks

### Add a new UI element
Use native iOS 26 SwiftUI — `.ultraThinMaterial`, `.buttonStyle(.glass)`, `.buttonStyle(.glassProminent)`, `GlassCard`.

### Add a new theme
Add a case to `AppTheme` in `Theme.swift`, fill in `gradientColors`, `accentColor`, `swatchColors`, `name`.

### Add a new PGP operation
Add a static function to `PGPService.swift`, wire UI in `NotepadView.swift`, add a case to `NotepadMode` in `Components.swift`.

### Change app icon
Replace `Assets.xcassets/AppIcon.appiconset/AppIcon.png` with a new 1024×1024 PNG.

### Push and trigger a build
Always commit and push directly to `main` — no feature branches.
```bash
git add <files>
git commit -m "..."
git push origin main
```

## Known gotchas
- `UTType(filenameExtension: "asc")` returns `nil` on iOS — use `[.item]` to accept all files.
- ObjectivePGP ObjC bridge makes `Key` come through as `Key?` — always guard-unwrap in `passphraseForKey:` closure.
- `SKIP_INSTALL=NO` and `INSTALL_PATH="/Applications"` required in archive command or `.app` won't appear in xcarchive.
- xcodegen sources path is `.` — any new `.swift` file in root is auto-included.
- For debugging: add in-app logging temporarily by creating an `AppLogger` singleton + `LogsView` (see git history for reference implementation). Remove before shipping.
