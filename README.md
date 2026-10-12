# PRAMAAN (प्रमाण)
### *Tamper-Proof Athletic Talent Identification & Certification System*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-12%2B%20(API%2031--36)-green.svg?logo=android)](https://developer.android.com)
[![Ktor](https://img.shields.io/badge/Server-Ktor%203.1-orange.svg?logo=ktor)](https://ktor.io)
[![Security](https://img.shields.io/badge/Crypto-Android%20Keystore%20ECDSA%20P--256-red.svg)](https://developer.android.com/training/articles/keystore)
[![SAI NeGD Target](https://img.shields.io/badge/Benchmark-SAI%20NeGD%20Concept%20Note-emerald.svg)](https://sportsauthorityofindia.nic.in)
[![Tests](https://img.shields.io/badge/Tests-All%20Green-brightgreen.svg)](#testing--validation)

---

## 1. Executive Summary & The Problem

India’s sports talent identification currently relies almost entirely on manual, in-person testing conducted at a handful of regional centres. This setup reaches only a tiny fraction of children across the country.

At the same time, remote submissions via consumer smartphones and unverified videos are inherently vulnerable to fraud:
- **Video Manipulation:** Footage can be spliced, cropped, or edited.
- **Speed Tampering:** Video recorded at 60 or 120 FPS can be played back at 30 FPS to artificially inflate hang-time or jump distance.
- **Impersonation & Stand-ins:** A different athlete can perform the trial off-camera.
- **Age Fraud:** Identity and age are routinely misreported to gain unfair category advantages.

As a result, bodies like the **Sports Authority of India (SAI)** cannot reliably act on remotely submitted results for national selection, academy admission, or stipends without physically re-testing every candidate. Consequently, extraordinary talent in rural and remote regions remains invisible.

> **“PRAMAAN is a tamper-proof talent-certification system for India’s sports selection. A coach-verified athlete test on any Android phone becomes a signed, physics-checked certificate that anyone can verify by scanning a QR.”**

---

## 2. Core Architecture & Zero-Trust Verification Pipeline

PRAMAAN transforms any commodity Android smartphone (3–4 GB RAM, Android 12+) into an unforgeable, certified testing station. It does not trust unverified video files or client self-reporting. Instead, it enforces a 4-tier zero-trust pipeline:

```mermaid
graph TD
    subgraph Mobile Device [Athlete / Station Phone :app]
        Cam[CameraX 30 FPS] --> MP[MediaPipe PoseLandmarker Lite]
        Sensors[Gyroscope 50 Hz] --> GyroRec[GyroRecorder]
        MP --> LiveScorer[Real-Time Scorer: Vertical / Broad / Sit-Up]
        
        Cam --> VideoFile[video.mp4]
        MP --> FramesFile[frames.jsonl]
        Sensors --> GyroFile[gyro.jsonl]
        
        VideoFile & FramesFile & GyroFile --> Sealer[Hardware Sealer]
        Sealer --> Merkle[SHA-256 Merkle Hashes]
        Merkle --> Keystore[Android Keystore ECDSA P-256]
        Keystore --> Manifest[manifest.json + manifest.sig]
        
        Manifest --> LocalAudit[Local IntegrityEngine C1-C5]
        LocalAudit --> BadgeLocal[Badge: VERIFIED / FLAGGED]
    end

    subgraph Coach Physical Presence [In-Person Coach Verification]
        BadgeLocal --> S2QR[Hardware S2 QR Code]
        S2QR --> CoachScan[Coach Phone Scan & In-Person Presence]
        CoachScan --> CoachReview[Video Scrub + AI Check Audit]
        CoachReview --> CoachKey[Coach Keystore ECDSA P-256 Sign]
    end

    subgraph Central Authority [:server on Laptop / Regional Hub]
        CoachKey --> SyncClient[Multipart Upload POST /api/sessions]
        SyncClient --> ReVerifier[Server SessionVerifier]
        ReVerifier --> ServerChecks[Independent Pure Kotlin Re-Score]
        ServerChecks --> Issuer[ServerCertificateIssuer ES256]
        Issuer --> Cert[Digital JWS Certificate]
        Issuer --> DB[(SQLite Database)]
        DB --> Dashboard[Web Dashboard & Media Verification]
        Cert --> PublicQR[Public Verification QR /v/{jws}]
    end
```

### The 4 Trust Pillars

1. **On-Device Computer Vision (No Cloud Roundtrip):**  
   Google MediaPipe 33-landmark pose estimation runs entirely on the device at 30 FPS. Trajectories are sampled purely using frame timestamps, decoupling evaluation from network latency or system clocks.

2. **Hardware-Backed Cryptographic Ledger:**  
   The instant recording concludes, the app calculates SHA-256 digests of the raw video container (`video.mp4`), normalized keypoint coordinates (`frames.jsonl`), and sensor streams (`gyro.jsonl`). An immutable session manifest is formatted in RFC 8785 Canonical JSON and cryptographically signed inside the phone's **Android Keystore (Secure Enclave / StrongBox / TEE)** using a non-exportable ECDSA P-256 private key. Any subsequent modification of a single byte breaks the signature.

3. **5-Layer Automated Physics & Sensor Audit ($C_1$ to $C_5$):**  
   Measurement values double as fraud detection filters:
   - **$C_1$ (Seal Integrity):** Verifies the ECDSA P-256 signature against the device public key and matches file digests against the manifest.
   - **$C_2$ (Ballistic Freefall Gravity):** Fits a quadratic parabola ($y(t) = at^2 + bt + c$) to the airborne trajectory to estimate gravitational acceleration ($g = -2a$). Real human jumps yield $g \in [7.31, 12.31]\text{ m/s}^2$. Slow-motion videos (e.g., $0.5\times$) yield $g \approx 2.45\text{ m/s}^2$ and are instantly flagged.
   - **$C_3$ (Multi-Method / Horizontal Consistency):** For vertical jumps, cross-validates flight-time ballistics ($h = \frac{1}{8}gt^2$) against body-scaled hip rise. For broad jumps, validates linear forward trajectory and speed limit ($\le 5\text{ m/s}$).
   - **$C_4$ (Camera Steadiness):** Evaluates hardware gyroscope angular drift during execution ($< 0.12\text{ rad/s}$ RMS), automatically trimming touch transients from start/stop button presses. Flags handheld movement or artificial camera panning.
   - **$C_5$ (Calibration / Witness Attestation):** Enforces ground-calibration scale consistency ($\le 10\%$ discrepancy) or validates an offline cryptographic co-signature from a distinct witness phone.

4. **Zero-Trust Central Authority & Public Verifiability:**  
   The laptop/central server does not take the phone’s word for it. It runs the identical pure-Kotlin `:core` engine on the raw uploaded frames, verifies Merkle hashes, checks coach signature binding, and issues an RFC 7515 compact **ES256 JWS digital certificate**. Anyone can scan the resulting QR code with a standard smartphone camera to view the tamper-proof result on the web.

---

## 3. Supported Athletic Tests

PRAMAAN implements standardized SAI fitness battery protocols with automated foul detection and biomechanical feedback:

| Athletic Test | Primary Metric | AI Measurement Method | Fraud / Foul Detection |
| :--- | :--- | :--- | :--- |
| **Standing Vertical Jump** | Height ($\text{cm}$) | Sub-frame takeoff/landing detection via foot velocity; dual-calculation ($h_{flight} = \frac{1}{8}gt^2$ vs $h_{hip}$ anatomical rise). | $C_2$ Parabolic gravity fit ($9.81\text{ m/s}^2$), $C_3$ dual-method agreement, countermovement dip detection. |
| **Standing Broad Jump** | Distance ($\text{cm}$) | Ground-plane perspective homography calibrated against standard 200 cm floor tape; sub-frame takeoff and rear-most heel contact identification. | Take-off line foul detection (toes over line), horizontal velocity consistency, $C_5$ scale ratio consistency ($\le 10\%$). Target accuracy $\pm 2\text{ cm}$. |
| **30-Second Sit-Ups** | Repetitions | Real-time state machine tracking torso-femur angular flexion ($> 140^\circ$ flat, $< 55^\circ$ knees touched). | Cadence tracking, rejected rep categorization (`NOT_UP`, `NOT_DOWN`, `TOO_FAST` $< 0.5\text{ s}$). |

---

## 4. Role-Based Identity & In-Person Presence Workflow

To prevent impersonation while avoiding bloated third-party authentication systems, PRAMAAN implements two clean, hardware-bound identity flows:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ATHLETE REGISTRATION                            │
│  Name + DOB + Height + Weight + Consent  ──>  Bind Device Keystore Key │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Record & Seal
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        HARDWARE S2 QR CODE                             │
│  Athlete phone renders offline QR containing:                          │
│  Session ID + Manifest SHA-256 + Athlete Initials + Score + Device PK  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ In-Person Scan
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        COACH IN-PERSON REVIEW                          │
│  Coach signs in (Coach ID + PIN)  ──>  Scans S2 QR on Athlete Phone    │
│  Inspects video playback, pose skeleton & 5 AI integrity checks        │
│  Signs APPROVE / REJECT using Coach Keystore Key                       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Multipart Upload
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      CENTRAL SERVER VERIFICATION                       │
│  Server re-scores frames.jsonl  ──>  Matches Coach Key  ──>  Issues JWS │
└────────────────────────────────────────────────────────────────────────┘
```

1. **Athlete Self-Registration:** Athletes register with full name, date of birth, standing height, and guardian consent. The profile is permanently bound to the device's hardware Keystore public key. No passwords or OTPs needed.
2. **In-Person Coach Verification (S2 QR Code):** When the athlete finishes an assessment, the app generates a high-density S2 QR code containing the session digest and athlete ID. The coach scans this code on-site, proving physical presence and eliminating remote submission spoofing.
3. **Coach Review & Hardware Sign-Off:** The coach reviews the trial video, annotated landing stills, and AI integrity checks. Upon approval, the coach's hardware-backed Keystore signs an approval payload.
4. **Official Digital Certificate:** The server validates that:
   - Server integrity re-score is strictly `VERIFIED`.
   - The reviewing coach key matches an accredited coach.
   - The certificate is rendered as an immutable JWS and encoded into a QR code for talent scout verification.

---

## 5. Trust Badges

Every assessment produces an unequivocal official badge based on deterministic evaluation rules:

| Badge | Visual | Criteria & Meaning |
| :--- | :---: | :--- |
| **WITNESSED** | 🟢 Dark Green | Recorded on device, hardware sealed, all AI checks ($C_1$–$C_5$) pass, and co-signed by an independent witness device. |
| **VERIFIED** | 🟢 Green | Recorded on device, hardware sealed, all AI physics checks ($C_1$–$C_4$) pass. Fully valid for coach review. |
| **UNSEALED** | ⚪ Grey | Video was imported from storage rather than captured live. Physics checks pass, but cannot receive official certification. |
| **FLAGGED** | 🟡 Amber | Physics checks failed (e.g., gravity $< 7.31\text{ m/s}^2$ indicating slow-motion video cheat, or excessive camera movement). |
| **TAMPERED** | 🔴 Red | Cryptographic seal violated. Manifest signature failed or video/keypoint bytes were altered post-capture. |

---

## 6. Accuracy Validation Module

In strict accordance with SAI/NeGD testing requirements, PRAMAAN includes an integrated statistical validation suite (`AccuracyStats.kt`) that benchmarks AI measurements directly against manual steel tape measurements:

### Statistical Metrics Evaluated
- **Mean Signed Bias ($\bar{d}$):** $\frac{1}{n} \sum (y_{ai} - y_{tape})$ (assesses systematic over/under-estimation).
- **Mean Absolute Error (MAE):** $\frac{1}{n} \sum |y_{ai} - y_{tape}|$.
- **Standard Deviation of Error ($s_d$):** Quantifies measurement consistency.
- **95% Limits of Agreement (LoA):** $\bar{d} \pm 1.96 \times s_d$ (Bland-Altman agreement range).
- **Proportion within $\pm 2\text{ cm}$:** Scored with 95% Wilson Score Confidence Intervals.
- **Proportion within $\pm 3\text{ cm}$:** Secondary tolerance threshold.
- **Automated Bland-Altman Plots:** Generates interactive SVG difference plots directly on the server web dashboard.

---

## 7. Repository Structure

PRAMAAN is structured as a decoupled, multi-tier Gradle monorepo adhering to strict software engineering standards:

```text
Pramaan/
├── :core                               # Pure Kotlin/JVM Domain Engine
│   ├── src/main/kotlin/app/pramaan/core/
│   │   ├── crypto/                     # ECDSA P-256 verifier, SHA-256, DER/Raw codecs, Base64Url
│   │   ├── integrity/                  # C1-C5 checks, IntegrityEngine, ParabolaFit, BroadJumpChecks
│   │   ├── manifest/                   # CanonicalJson (RFC 8785), SessionManifest, QR codecs
│   │   ├── model/                      # PoseFrame, Landmark, Badge, CheckResult, TestType
│   │   ├── scoring/                    # VerticalJumpAnalyzer, SitUpAnalyzer, Feedback
│   │   │   └── broadjump/              # BroadJumpAnalyzer, GroundCalibration, Homography
│   │   └── validation/                 # AccuracyStats (Bland-Altman, Bias, MAE, LoA, Wilson CI)
│   └── src/test/                       # 100% JVM unit tests (T1-T6, T2b, SBJ, Synthetic tracks)
│
├── :app                                # Native Android Application (API 31-36)
│   └── src/main/kotlin/app/pramaan/
│       ├── camera/                     # CameraX controller, MediaPipe PoseEngine, GyroRecorder
│       ├── data/                       # Room database (PramaanDb), DAOs, SessionStore
│       ├── seal/                       # DeviceKey (Android Keystore P-256), Hardware Sealer
│       └── ui/                         # Jetpack Compose UI (Stitch "Pramaan Talent System" design)
│           ├── athlete/                # Athlete home, test catalog, profile, submissions
│           ├── coach/                  # Coach review queue, S2 QR scanner, video scrub, review screen
│           ├── broadjump/              # Frame scrubber, floor tape calibration screen
│           └── screens/                # Welcome, TestSetup, Record, Result, Sync, Certificate
│
└── :server                             # Standalone Verification Authority
    └── src/main/kotlin/app/pramaan/server/
        ├── Application.kt              # Ktor Netty entrypoint, multi-LAN IP auto-discovery
        ├── Db.kt                       # SQLite persistence with Exposed ORM
        ├── ApiRoutes.kt                # /api/health, /api/auth, /api/sessions, /api/coach
        ├── Certificates.kt             # Nimbus JWS compact ES256 certificate issuer & verifier
        ├── SessionVerifier.kt          # Re-executes pure :core integrity audit on uploads
        ├── BroadJumpRescorer.kt        # Server-side re-scoring of broad jump frames & calibration
        ├── ValidationService.kt        # Ingestion of ground-truth tape data & accuracy reports
        └── DashboardPages.kt           # HTML5/CSS3 admin dashboard, SVG Bland-Altman plots, QR generator
```

### Architectural Guarantees
- **`:core` Stays Pure Kotlin:** Absolutely zero `android.*` imports, zero Java system clocks, and zero third-party framework ties. All timestamps derive strictly from camera frame timestamps.
- **Zero DI Overhead:** Manual dependency wiring via `AppContainer.kt`. Fast startup, zero reflection, zero code-generation conflicts.
- **Pinned Dependencies:** All libraries pinned via `gradle/libs.versions.toml`.

---

## 8. Quick Start Guide

### Prerequisites
- **Java Development Kit (JDK):** Version 17 or higher.
- **Android SDK:** API Level 31 to 36 (Android 12 to 16).
- **Gradle:** Wrapper included (Gradle 9.3.1).

---

### Step 1: Run Automated Verification Tests
Run the comprehensive unit and server integration test suite:
```bash
./gradlew test --no-daemon
```
*Executes all tests across `:core` (scoring algorithms, synthetic physics models, cryptographic codecs) and `:server` (session verification, certificate generation, database migrations).*

---

### Step 2: Build the Android APK
Assemble the debug APK:
```bash
./gradlew :app:assembleDebug --no-daemon
```
The compiled APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```
Install it onto your Android phone via USB debugging:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### Step 3: Run the Verification Server on Laptop

#### On Windows (Single-Click Automation)
1. **Firewall Rule (One-Time Setup):** Right-click `allow-firewall.bat` and select **"Run as administrator"**. This ensures incoming TCP connections on port 8080 from phones on your Wi-Fi or mobile hotspot are allowed through Windows Defender Firewall.
2. **Start Server:** Double-click `run-server.bat` (or execute `./gradlew :server:run`).

Upon startup, the server prints a local LAN pairing banner:
```text
========================================================================
   PRAMAAN SPORTS TALENT VERIFICATION SERVER (PORT 8080)
========================================================================
 [✓] Laptop Web Dashboard:  http://localhost:8080
 [✓] Mobile Endpoint (LAN): http://192.168.1.45:8080
========================================================================
```

---

### Step 4: Pair Phone with Laptop Server
1. Open `http://localhost:8080/` in your laptop browser to view the **Pramaan Dashboard**.
2. The top of the page displays a **"Pair Field Station"** QR code encoding your laptop's local network address.
3. On the phone app, navigate to **Connect Server** $\rightarrow$ Tap **"Scan Server QR"** and point the camera at your laptop screen.
4. Tap **"Test Connection"** to verify communication (`GET /api/health`).

---

## 9. Live Evaluation & Demo Walkthrough (PRD §4 Flow)

To replicate the master evaluation flow on physical devices:

1. **Athlete Onboarding:**  
   Open the Pramaan app $\rightarrow$ Select **Athlete** $\rightarrow$ Register athlete details (e.g., *Manpreet Kaur, 14F, 152 cm*). The device Keystore key is generated and bound.
2. **Setup Test Station:**  
   Mount the phone on a tripod or table stand approximately 3 metres from the jumping mark. Select **Vertical Jump** $\rightarrow$ Tap **Begin Test**.
3. **Perform Live Jump:**  
   Step back to the mark. MediaPipe tracks full-body keypoints with a real-time skeleton overlay. Execute countermovement jump. The app identifies the takeoff/landing points and displays the measured height. Tap **Stop & Save Result**.
4. **Instant Hardware Seal:**  
   In under 1 second, the phone hashes all files, signs the manifest using Android Keystore, runs checks $C_1$–$C_4$, and awards the **VERIFIED** badge.
5. **Submit for Coach Verification:**  
   Tap **Submit for Coach Verification**. The session uploads to the laptop server. The app displays the **In-Person S2 QR Code**.
6. **Coach In-Person Scan & Approval:**  
   On the coach device (logged in as Coach), tap **Scan Athlete QR** $\rightarrow$ Scan the athlete's screen. The coach screen immediately displays athlete bio-data, session video playback, and the 5-layer AI audit check breakdown. Tap **Approve & Sign Record**.
7. **Official Digital Certificate:**  
   The server issues an immutable JWS certificate. Tap **View Digital Certificate** to render the official QR code.
8. **Public Cross-Verification:**  
   Scan the certificate QR with **any standard smartphone camera (no app required)**. The browser opens `http://<server-ip>:8080/v/<jws>` displaying the official green verification seal, athlete initials, test score, coach signature fingerprint, and tamper audit status.
9. **Fraud & Cheat Demonstrations:**
   - **Simulate Tamper:** On the athlete result screen, tap **Simulate Tamper (Demo Fraud Detection)**. The app injects a single altered byte into a copy of `video.mp4` and re-runs the audit. The badge instantly collapses to **TAMPERED** ($C_1$ fails: hash mismatch).
   - **Slow-Motion Playback Cheat:** In the app, navigate to **Import Video** and import `slowmo_jump.mp4` (a real jump slowed down by $2\times$). The AI analyzer scores the visual movement, but the freefall gravity check flags $g \approx 2.45\text{ m/s}^2$, permanently marking the trial **FLAGGED** ("Motion too slow for real gravity").

---

## 10. Technology Stack

- **Mobile Client (`:app`):** Kotlin 2.2, Android Jetpack Compose (Material 3), CameraX 1.4, Google MediaPipe Pose Landmarker Lite, Android Keystore API, Room Database 2.7, ZXing Android Embedded.
- **Domain Engine (`:core`):** Pure Kotlin 2.2, KotlinX Serialization (JSON), KotlinX Coroutines, RFC 8785 Canonical JSON Encoder, BouncyCastle/JVM Cryptography.
- **Backend Authority (`:server`):** Ktor 3.1 (Netty Engine), Exposed ORM 0.61, SQLite 3 (JDBC), Nimbus JOSE + JWT (ES256), KotlinX HTML DSL.

---

## 11. License & Attribution

Developed for India's National Sports Talent Identification Assessment.  
Built in compliance with the **Sports Authority of India (SAI)** and **National e-Governance Division (NeGD)** AI assessment framework specifications.
