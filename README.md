# PRAMAAN

PRAMAAN turns an Android smartphone into a verifiable fitness-testing station using on-device AI pose tracking, hardware-backed Android Keystore signatures, and physics checks.

## Project Structure

- `:app` - Android application (Jetpack Compose, Material 3, CameraX, MediaPipe)
- `:core` - Pure Kotlin/JVM library (scoring, integrity engine, canonical JSON, cryptographic verification)
- `:server` - Ktor JVM application (Netty server on port 8080, SQLite database, verification API & dashboard)

## Prerequisites

- JDK 17
- JDK 17
- Android SDK (minSdk 31 / Android 12+, compileSdk/targetSdk 36)

## Quick Start

### Run All Unit & Integration Tests
```bash
./gradlew test
```
(Runs `:core:test` [26 tests] and `:server:test` [2 integration tests]).

### Build Android App
```bash
./gradlew :app:assembleDebug
```
Produces `app/build/outputs/apk/debug/app-debug.apk`.

### Run Verification Server
```bash
./gradlew :server:run
```
The server starts at `http://localhost:8080`.
- Web Dashboard: `GET http://localhost:8080/`
- JSON Sessions List: `GET http://localhost:8080/api/sessions`
- Upload Session: `POST http://localhost:8080/api/sessions` (multipart with manifest.json, manifest.sig, frames.jsonl, gyro.jsonl, video.mp4, witness.json)
- Public Certificate Verification: `GET http://localhost:8080/v/{jws}`

## Master Acceptance Flow (PRD §4 Live Demo)

1. **Roster Setup**: Add athlete with verified height (e.g., Maya, 14F, 152 cm).
2. **Pre-flight Alignment**: Screen 2 checks camera levelness via gyro and athlete standing 3m away in framing box.
3. **Live Capture & Provisional Scoring**: Record 3 jump attempts with live MediaPipe 33-landmark skeleton overlay.
4. **Hardware Sealing**: Stop test seals session folder in app-private storage, computes SHA-256 hashes, and signs manifest with Android Keystore ECDSA P-256 key.
5. **Instant Local Audit**: Screen 4 evaluates C1 (hardware seal), C2 (freefall gravity $g \approx 9.8$ m/s²), C3 (two-method height agreement $< 5$ cm), and C4 (steady camera). Displays VERIFIED badge.
6. **Offline Witness Co-Signing**: Coach generates offline QR (Type S); witness phone scans and signs attestation, displaying Type W QR; coach scans back to promote session to WITNESSED badge.
7. **Sync to Laptop Server**: In Sync tab, submit session over WiFi to laptop server (`POST /api/sessions`).
8. **Server Re-verification & Certificate**: Server independently recalculates hashes, checks ECDSA signature, re-scores frames, and issues compact ES256 JWS certificate displayed on web dashboard.
9. **Tamper & Cheat Resistance Demo**:
   - Simulated tamper button flips video byte $\to$ Server/local immediately flags TAMPERED (C1 fails).
   - Slow-motion video imported $\to$ C2 gravity check flags $g \approx 2.45$ m/s², marking session FLAGGED.
