# Natepad iOS — Project Reference

## What this is
PGP notepad iOS app. Encrypt/decrypt/sign/verify messages, manage PGP keys (generate, import, export).
Target: iOS 26, Liquid Glass UI, sideloaded via AltStore (no App Store).

## Repo
`https://github.com/nphil/natepad-ios` — every push to `main` triggers CI and produces an IPA.
IPA download: GitHub Releases → "latest" pre-release (overwritten on each main push).

## Build / CI
- **Never run Xcode locally** — use GitHub Actions (`.github/workflows/build.yml`)
- xcodegen reads `project.yml` → generates `Natepad.xcodeproj` in CI (never committed)
- Unsigned IPA: `CODE_SIGNING_ALLOWED=NO` — AltStore signs at sideload time
- Runner: `macos-15`, auto-selects latest Xcode (currently Xcode 26.3 with iOS 26 SDK)
- **Always commit + push after any meaningful change** — CI does the rest

## File map

| File | Purpose |
|---|---|
| `NatepadApp.swift` | `@main`, app entry, biometric lock gate, `TabView` root |
| `NotepadView.swift` | Encrypt/Decrypt/Sign/Verify UI, per-mode state (`modeState` dict), passphrase prompt |
| `KeysView.swift` | Key list, generate/import/export/delete actions |
| `Sheets.swift` | `GenerateKeySheet`, `ImportKeySheet`, `ExportKeySheet`, `PassphraseSheet`, `DocumentPicker` |
| `Components.swift` | `ModePicker`, `StatusBanner`, `RecipientChips`, `GlassCard`, `SettingsView` |
| `PGPService.swift` | Static enum wrapping ObjectivePGP — parseKeys, generate, encrypt, decrypt, sign, verify |
| `KeyRecord.swift` | `struct KeyRecord: Identifiable, Codable` — fingerprint, userIDs, armored keys |
| `KeyStore.swift` | `@MainActor ObservableObject` — Keychain persistence, add/delete keys |
| `BiometricGate.swift` | Face ID / Touch ID gate, `requireUnlock` in UserDefaults |
| `project.yml` | xcodegen spec — deployment target, signing, SPM deps, asset catalog |
| `Assets.xcassets/` | App icon (AppIcon.png 1024×1024, icon option 1 "Lock/Vault") |

## Key dependencies
- **ObjectivePGP** 0.99.4 via SPM (`https://github.com/krzyzanowskim/ObjectivePGP`)
- No other third-party deps

## Architecture rules
- **No custom `.glass` / `.glassProminent` button style definitions** — iOS 26 SDK provides these natively. Adding custom ones causes "ambiguous use of 'glass'" compile error.
- Per-mode notepad state: `modeState: [NotepadMode: ModeState]` dict — each mode has its own `input` and `output` strings, prevents content bleed.
- File import uses `UIDocumentPickerViewController` (not SwiftUI `.fileImporter`) — SwiftUI's version silently drops its callback when used inside a sheet.
- Keys stored in iOS Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.

## Common tasks

### Add a new UI element
Use native iOS 26 SwiftUI — `.ultraThinMaterial`, `.buttonStyle(.glass)`, `.buttonStyle(.glassProminent)`, `GlassCard` (defined in Components.swift).

### Add a new PGP operation
Add a static function to `PGPService.swift`, wire UI in `NotepadView.swift`, add a case to `NotepadMode` in `Components.swift`.

### Change app icon
Replace `Assets.xcassets/AppIcon.appiconset/AppIcon.png` with a new 1024×1024 PNG. `Contents.json` is already configured for universal iOS.

### Push and trigger a build
```bash
cd "/Users/nitin/AI Playground/Natepad-iOS"
git add -p   # or specific files
git commit -m "..."
git push origin main
```
Then watch: `https://github.com/nphil/natepad-ios/actions`

## Known gotchas
- `UTType(filenameExtension: "asc")` returns `nil` on iOS — use `.data` to accept all files.
- ObjectivePGP ObjC bridge makes `Key` come through as `Key?` — always guard-unwrap in `passphraseForKey:` closure.
- `SKIP_INSTALL=NO` and `INSTALL_PATH="/Applications"` are required in the archive command or the `.app` won't appear in the xcarchive Products folder.
- xcodegen sources path is `.` — any new `.swift` file dropped in the root is auto-included. Subdirectories need to be either in root or added explicitly.
