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

#### On Windows (Single Click)
1. **Allow Port 8080 (One-Time Setup)**: Right-click `allow-firewall.bat` and select **"Run as administrator"**. This allows phones on your Wi-Fi or mobile hotspot to connect to port 8080.
2. **Start Server**: Double-click `run-server.bat` (or execute `./gradlew :server:run`).
   - The server displays an ASCII startup banner showing your laptop's local LAN IP (e.g., `http://192.168.1.15:8080`).
   - Note: Gradle will show `> 91% EXECUTING [:server:run]` when active and listening. This is expected behavior for a running server process!

#### Accessing the Dashboard & Pairing
- Open `http://localhost:8080/` (or your laptop's LAN IP) in your browser.
- The dashboard displays a **"Pair Field Station"** card containing a QR code encoded with your laptop's local server URL.
- In the Pramaan mobile app on the **Sync** tab, tap **"Scan Server QR"** to pair instantly without manually typing IP addresses.
- Tap **"Test Connection"** to verify communication with `GET /api/health` before syncing.

### Master Acceptance Flow (PRD §4 Live Demo)

1. **Roster Setup**: Select or add athlete with verified height (e.g., Maya, 14F, 152 cm).
2. **Pre-flight Alignment**: Screen 2 guides camera setup 3m away with phone steadiness indicators.
3. **Live Capture & Provisional Scoring**: Record jump attempt with live MediaPipe 33-landmark skeleton overlay.
4. **Hardware Sealing**: Stop test seals session folder in app-private storage, computes SHA-256 hashes, and signs manifest with Android Keystore ECDSA P-256 key.
5. **Instant Local Audit**: Screen 4 evaluates C1 (hardware seal), C2 (freefall gravity $g \approx 9.8$ m/s²), C3 (two-method height agreement $< 5$ cm), and C4 (steady camera). Displays VERIFIED badge.
6. **Offline Witness Co-Signing**: Coach generates offline QR (Type S); witness phone scans and signs attestation, displaying Type W QR; coach scans back to promote session to WITNESSED badge.
7. **Sync to Laptop Server**: In Sync tab, scan the laptop dashboard QR (or enter IP) and tap "Sync" over Wi-Fi/Hotspot (`POST /api/sessions`).
8. **Server Re-verification & Certificate**: Server independently recalculates hashes, checks ECDSA signature, re-scores frames, and issues compact ES256 JWS certificate displayed on web dashboard.
9. **Tamper & Cheat Resistance Demo**:
   - Simulated tamper button flips video byte $\to$ Server/local immediately flags TAMPERED (C1 fails).
   - Slow-motion video imported $\to$ C2 gravity check flags $g \approx 2.45$ m/s², marking session FLAGGED.

