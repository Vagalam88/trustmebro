# 🛡️ TrustMeBro

**Don't trust. Verify.**

> *Vibecodé une main dans le calbard* 🤌

[![Android](https://img.shields.io/badge/Android-26%2B-brightgreen?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Bouncy Castle](https://img.shields.io/badge/PGP-Bouncy%20Castle-orange)](https://www.bouncycastle.org/)

---

An Android app that verifies the integrity and authenticity of APK files downloaded from GitHub Releases, F-Droid, or any third-party source.

You download an APK from GitHub. The dev provides a `SHA256SUM.asc`, a `.sign` file, maybe a public key. Now what? Open a terminal? Install GPG? Nah.

**Drop everything into TrustMeBro. One tap. Done.**
<img width="1080" height="2269" alt="Screenshot_001" src="https://github.com/user-attachments/assets/5591fe95-2e42-430a-8786-cb7854379870" />
<img width="1080" height="2278" alt="Screenshot_002" src="https://github.com/user-attachments/assets/5de1a4c3-85f6-403e-bf46-93bb7937f63d" />

---

## Features

### #️⃣ Checksum Verification
Real hash computation using `java.security.MessageDigest`. Streams large files (100MB+ APKs) in chunks without eating your RAM. Supports **SHA-256**.

- Auto-parses `SHA256SUMS`, `.sha256`, and any `hash  filename` format
- Handles PGP cleartext signed checksum files (extracts the body)
- Manual hash input — paste directly from GitHub
- Clear **MATCH** / **MISMATCH** verdict

### 🔏 PGP Signature Verification
Powered by **Bouncy Castle** (`bcpg-jdk18on`). No GPG installation needed.

- Verifies **cleartext signed messages** (like `SHA256SUM.asc`)
- Verifies **detached signatures** (`.sig`, `.sign`, `.asc`)
- Displays signer identity, key ID, fingerprint, algorithm, date
- RSA, DSA, ECDSA, EdDSA support

### 📦 APK Certificate Analysis
Parses the APK ZIP structure and extracts signing certificate info.

- Extracts `META-INF/*.RSA` certificates (v1 JAR signatures)
- Detects APK Signing Block (v2/v3 signatures)
- Parses X.509 certificate: subject, issuer, algorithm, validity
- Computes **SHA-256**, **SHA-1**, **MD5** fingerprints of the signing cert
- Compare with fingerprints published by the developer

### 🧠 Smart File Detection
Drop all your files at once — TrustMeBro figures out what each one is:

| You drop... | TrustMeBro sees... |
|---|---|
| `app-v2.5.9.apk` | 📱 APK |
| `SHA256SUM.asc` | 📝 PGP-signed checksums |
| `DETACHED_SIGN.sign` | 🔏 Detached signature |
| `hexa_public.asc` | 🔑 PGP public key |
| `checksums.sha256` | #️⃣ Checksum file |

Detection works by filename **and** content analysis — a `.asc` file containing `-----BEGIN PGP PUBLIC KEY BLOCK-----` is correctly identified as a key, not a signature.

---

## Example: Verifying Bitcoin Keeper

Real-world scenario using [Bitcoin Keeper v2.5.9](https://github.com/KeeperCommunity/bitcoin-keeper/releases/tag/v2.5.9):

1. Download: `Bitcoin_Keeper_v2.5.9.apk` + `SHA256SUM.asc` + `KEEPER_DETACHED_SIGN.sign`
2. Get Hexa Team's public key from `keys.openpgp.org`
   ```
   Fingerprint: 389F 4CAD A078 5AC0 E28A 0C18 1BEB DE26 1DC3 CF62
   ```
3. Open TrustMeBro → select all 4 files
4. Tap **"Lancer la vérification"**
5. Results:
   - ✅ SHA-256 checksum **MATCH**
   - ✅ PGP signature **valid** — signed by `Hexa Team <hexa@bithyve.com>`
   - 📦 APK cert fingerprint: `77:82:54:70:5D:C4:DA:83:...`

---

## Tech Stack

| Component | Choice |
|---|---|
| Language | **Kotlin 2.2** (AGP built-in) |
| UI | **Jetpack Compose** + Material 3 |
| Hashes | `java.security.MessageDigest` (native) |
| PGP | **Bouncy Castle** `bcpg-jdk18on` 1.79 |
| File access | Storage Access Framework (zero permissions) |
| Architecture | MVVM + StateFlow + Coroutines |
| Min SDK | 26 (Android 8.0) |
| Build | AGP 9.1.0 + Gradle 9.3.1 |

---

## Build

```bash
git clone https://github.com/Vagalam88/TrustMeBro.git
cd TrustMeBro
./gradlew assembleDebug
```

Or open in **Android Studio Panda 2** (2025.3.2+) and hit ▶️.

---

## Project Structure

```
app/src/main/java/com/trustmebro/
├── MainActivity.kt              # Entry point + splash
├── crypto/
│   ├── HashEngine.kt            # Streaming hash computation
│   ├── PGPEngine.kt             # Bouncy Castle PGP verification
│   └── APKParser.kt             # APK ZIP parsing + cert extraction
├── utils/
│   └── FileClassifier.kt        # Smart file type detection
└── ui/
    ├── MainViewModel.kt         # MVVM state management
    ├── theme/
    │   └── Theme.kt             # Dark theme
    ├── components/
    │   └── Components.kt        # Reusable UI components
    └── screens/
        └── MainScreen.kt        # Main verification screen
```

---

## How It Works

```
┌─────────────────────────────────────────┐
│         User drops files                │
│   APK + .asc + .sign + public key       │
└─────────────┬───────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│       FileClassifier                    │
│   Detects type by name + content        │
│   📱 APK  🔏 Sig  🔑 Key  #️⃣ Hash     │
└─────────────┬───────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│       MainViewModel                     │
│   Determines available verifications    │
│   Runs everything in coroutines         │
└───┬─────────┬──────────┬────────────────┘
    │         │          │
    ▼         ▼          ▼
┌────────┐ ┌────────┐ ┌──────────┐
│ Hash   │ │  PGP   │ │ APK Cert │
│ Engine │ │ Engine │ │ Parser   │
└────────┘ └────────┘ └──────────┘
    │         │          │
    ▼         ▼          ▼
┌─────────────────────────────────────────┐
│           Results UI                    │
│  ✅ MATCH  🔏 Valid  📦 Fingerprints   │
└─────────────────────────────────────────┘
```

---

## Security Notes

- **Everything runs locally** — no data leaves your device, no network calls during verification
- **No permissions required** — file access through Android's Storage Access Framework
- Bouncy Castle is the same crypto library used by Signal, ProtonMail, and most PGP implementations
- Hash computation uses Android's native `java.security.MessageDigest`

---

## Contributing

PRs welcome. Found a bug? Open an issue. Want to add a feature? Fork it.

Some ideas:
- 🌐 Fetch public keys from keyservers directly
- 📋 Verification history / logs
- 🔗 Import from GitHub Release URL
- 🌍 English / multilingual UI
- 🧪 Reproducible build verification

---

## License

MIT — Do whatever you want, bro. Just verify it first.

---

<p align="center">
  <b>TrustMeBro</b> — Because "trust me bro" is not a verification method.<br>
  <sub>Built with ☕ and questionable life choices.</sub>
</p>
