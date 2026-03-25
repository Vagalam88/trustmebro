# CLAUDE.md — TrustMeBro Android App

## Project Overview

**TrustMeBro** is an Android app that verifies the integrity and authenticity of APK files downloaded from GitHub Releases, F-Droid, or any third-party source. The tagline is "Don't trust. Verify."

The tone is ironic and slightly toxic — it's vibecodé and proud of it.

## Target Environment

- **Android Studio Panda 2** (2025.3.2)
- **AGP 9.1.0** with built-in Kotlin (NO separate `org.jetbrains.kotlin.android` plugin)
- **Gradle 9.3.1**
- **Kotlin 2.2.10** (bundled with AGP 9.1)
- **Compose Compiler plugin**: `org.jetbrains.kotlin.plugin.compose` version `2.2.10`
- **compileSdk / targetSdk**: 36
- **minSdk**: 26
- **Compose BOM**: latest 2025.x

## CRITICAL BUILD NOTES

These are the issues encountered during initial development. Fix them proactively:

1. **AGP 9.1 has built-in Kotlin** — do NOT apply `org.jetbrains.kotlin.android` plugin. Only use `com.android.application` + `org.jetbrains.kotlin.plugin.compose`.
2. **Compose Compiler plugin is REQUIRED** with Kotlin 2.x — add `id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"` to app plugins.
3. **Do NOT use `composeOptions { kotlinCompilerExtensionVersion = ... }`** — that's the old way. The compose compiler plugin handles it.
4. **`FlowRow`** is experimental — annotate composables using it with `@OptIn(ExperimentalLayoutApi::class)`.
5. **`Divider`** is deprecated in latest Compose Material3 — use `HorizontalDivider` instead.
6. **Gradle wrapper must be 9.3.1** — not 9.0.1 (doesn't exist on mirrors).
7. **Splash screen theme**: parent should be `Theme.SplashScreen` from `androidx.core:core-splashscreen:1.0.1`.

## App Features (3 verification modes, unified screen)

### 1. Checksum Verification (SHA-256, SHA-512, SHA-1, MD5)
- Compute real hashes using `java.security.MessageDigest`
- Stream large files (100MB+ APKs) in chunks with progress callback
- Auto-parse checksum files: `SHA256SUMS`, `.sha256`, lines of `hash  filename`
- Auto-parse PGP cleartext signed checksum files (extract body between headers)
- Compare computed hash against expected, show MATCH/MISMATCH
- Allow manual hash input (paste from GitHub)

### 2. PGP Signature Verification
- Use **Bouncy Castle** (`org.bouncycastle:bcpg-jdk18on:1.79` + `bcprov-jdk18on:1.79`)
- Verify **cleartext signed messages** (like `SHA256SUM.asc` — PGP signed checksums)
- Verify **detached signatures** (`.sig`, `.sign`, `.asc` files)
- Display signer identity, key ID, fingerprint, algorithm, signature date
- Support RSA, DSA, ECDSA, EdDSA keys

### 3. APK Certificate Analysis
- Parse APK as ZIP, scan `META-INF/` for `*.RSA`, `*.DSA`, `*.EC`, `*.SF`, `MANIFEST.MF`
- Extract X.509 certificate from PKCS#7 DER data
- Display: subject DN, issuer, serial number, signature algorithm, validity dates
- Compute SHA-256, SHA-1, MD5 fingerprints of the signing certificate
- Detect APK Signing Block (v2/v3) by finding "APK Sig Block 42" magic before Central Directory
- User compares fingerprints with those published by the developer (README, F-Droid, etc.)

### Smart File Detection
All files are classified automatically when imported:
- `.apk`, `.aab`, `.xapk` → APK
- `.asc`, `.sig`, `.sign`, `.gpg` → Signature (detached) or Signed Checksum (if filename contains sha256/checksum)
- Content starting with `-----BEGIN PGP PUBLIC KEY BLOCK-----` → Public Key
- Content starting with `-----BEGIN PGP SIGNED MESSAGE-----` → Signed Checksum
- Content starting with `-----BEGIN PGP SIGNATURE-----` → Detached Signature
- Content matching `^[a-fA-F0-9]{32,128}\s+.+$` → Checksum File
- Files > 5MB are treated as binary (skip text analysis to avoid false positives)

### Unified UI Flow
- Single screen with file picker (SAF `OpenMultipleDocuments`)
- User selects all files at once, app auto-classifies each one
- Shows detected capabilities ("✓ Checksum Verification", "✓ PGP Verification", "📦 APK Certificate", "🔑 PGP key optional")
- Single "Lancer la vérification" button runs everything
- Results appear below: checksums with MATCH/MISMATCH, PGP signature details, APK cert info with fingerprints
- No file access permissions needed (SAF handles everything)

## Architecture

```
app/src/main/java/com/trustmebro/
├── MainActivity.kt                  # Entry point, splash screen, edge-to-edge
├── crypto/
│   ├── HashEngine.kt                # Streaming MessageDigest with progress callback
│   ├── PGPEngine.kt                 # Bouncy Castle: cleartext verify, detached verify, key parsing
│   └── APKParser.kt                 # ZIP parsing, META-INF cert extraction, signing block detection
├── utils/
│   └── FileClassifier.kt            # Smart file type detection by name + content analysis
└── ui/
    ├── MainViewModel.kt             # MVVM StateFlow, orchestrates all verifications
    ├── theme/
    │   └── Theme.kt                 # Dark theme, TMB color palette, typography
    ├── components/
    │   └── Components.kt            # FileCard, HashDisplay, StatusBadge, ProgressBar, Chip, etc.
    └── screens/
        └── MainScreen.kt            # Main unified verification screen
```

## Visual Design

- **Dark mode only** — hacker vibes
- Background: `#0A0E14` with subtle grid pattern
- Accent: `#00FF88` (neon green)
- Danger: `#FF4444`, Warning: `#FFBB00`, Info: `#00AAFF`
- Monospace font for hashes and technical data
- Header: Shield emoji + "Trust**Me**Bro" with "Me" in accent green
- Subtitle: "Don't trust. Verify."
- Animated glow on the shield icon (infinite transition)
- Green gradient button for "Lancer la vérification"
- File cards with emoji icons per type (📱 APK, 🔏 Signature, 🔑 Key, #️⃣ Checksum)

## App Icon

- Adaptive icon with dark background (`#0A0E14`)
- Green shield with checkmark inside
- Red dashed diagonal slash through it (the irony — "trust me bro" but crossed out)

## Splash Screen

- Uses `androidx.core:core-splashscreen:1.0.1`
- Dark background with shield icon
- Quick transition to main screen

## Dependencies

```kotlin
// Core
androidx.core:core-ktx
androidx.lifecycle:lifecycle-runtime-ktx
androidx.lifecycle:lifecycle-viewmodel-compose
androidx.activity:activity-compose

// Compose (use latest BOM)
androidx.compose:compose-bom
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.compose.material:material-icons-extended
androidx.compose.animation:animation

// Navigation
androidx.navigation:navigation-compose

// Splash
androidx.core:core-splashscreen:1.0.1

// PGP (Bouncy Castle)
org.bouncycastle:bcpg-jdk18on:1.79
org.bouncycastle:bcprov-jdk18on:1.79

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android
```

## ProGuard Rules

```
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
```

## Example Use Case: Bitcoin Keeper v2.5.9

The reference scenario for testing:
1. Download from https://github.com/KeeperCommunity/bitcoin-keeper/releases/tag/v2.5.9
2. Files: `Bitcoin_Keeper_v2.5.9.apk` + `SHA256SUM.asc` (PGP cleartext signed checksums) + `KEEPER_DETACHED_SIGN.sign` (detached PGP signature)
3. Public key: Hexa Team `hexa@bithyve.com`, fingerprint `389F 4CAD A078 5AC0 E28A 0C18 1BEB DE26 1DC3 CF62`, available on `keys.openpgp.org`
4. Expected flow: user imports all 4 files → app detects types → verifies checksum matches → verifies PGP signatures → shows APK certificate fingerprints
5. APK cert fingerprint from their README: `77:82:54:70:5D:C4:DA:83:2C:F8:39:96:49:69:FE:AF:63:BD:79:EF:00:0A:34:43:86:0C:7C:AD:A2:55:1C:95`

## Language

The UI is in **French**. All labels, buttons, status messages are in French.

## License

MIT — "Do whatever you want, bro. Just verify it first."
