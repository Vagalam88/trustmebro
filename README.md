# TrustMeBro

**Don't trust. Verify.**

Android app to verify the integrity and authenticity of APKs and other files downloaded from GitHub Releases, F-Droid, or anywhere else.

## What it does

Drop your files in, tap verify. That's it.

- **SHA-256 checksum** — computes the hash and compares it against a checksum file or a hash you paste manually
- **PGP signature** — verifies cleartext signed messages (`.asc`) and detached signatures (`.sig`, `.sign`) against a public key
- **APK certificate** — extracts the signing certificate from the APK and shows you the SHA-256 fingerprint to compare with what the developer publishes

## How to use

1. Tap the file zone and select any combination of:
   - The APK (or any executable)
   - A checksum file (`SHA256SUMS`, `.sha256`, etc.)
   - A PGP signed checksum (cleartext `.asc`)
   - A detached PGP signature (`.sig`, `.sign`, `.asc`)
   - The signer's public key (optional — shows key info without it)
2. Optionally paste a hash manually if you copied it from GitHub
3. Tap **Lancer la vérification**
4. Check the results

The app auto-classifies every file you add — no manual type selection needed.

## Reference test case

[Bitcoin Keeper v2.5.9](https://github.com/KeeperCommunity/bitcoin-keeper/releases/tag/v2.5.9):
- `Bitcoin_Keeper_v2.5.9.apk`
- `SHA256SUM.asc` (PGP cleartext signed checksums)
- `KEEPER_DETACHED_SIGN.sign` (detached PGP signature)
- Public key: Hexa Team `hexa@bithyve.com` — fingerprint `389F 4CAD A078 5AC0 E28A 0C18 1BEB DE26 1DC3 CF62` (fetch from `keys.openpgp.org`)

## Tech

- Kotlin + Jetpack Compose
- Bouncy Castle for PGP (`bcpg-jdk18on:1.79`)
- No internet permission — fully offline
- No file storage permission — uses Android SAF (Storage Access Framework)
- minSdk 26 (Android 8.0)

## License

MIT
