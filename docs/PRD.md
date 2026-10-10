# PRAMAAN — Hackathon PRD (Android/Kotlin)

Oct 7, 2026 · @Aritra

## 1. Summary

PRAMAAN turns a cheap Android phone into a fitness-test station that scores the test with on-device AI and proves the result is real: unedited, at real speed, and vouched for by a witness.

**Pitch line:** "Counting reps is solved. Proving the reps were real is not."

**What we build (hackathon prototype, not a production app):**

| Pillar | What it does | Why it matters on stage |
| --- | --- | --- |
| Score | MediaPipe pose scores a standing vertical jump and sit-ups live on the phone, with skeleton overlay | Instant, visual "wow" |
| Seal | Video and keypoints are hashed and signed with a hardware-backed Android Keystore key the moment recording stops; physics checks catch slowed or faked footage | The differentiator: cheats get caught with a plain-language reason |
| Share | A second phone co-signs the result by QR (offline), the result syncs to a small server, and a certificate QR can be verified by anyone | A judge verifies a result on their own phone |

**Three ideas that make it credible:**

1. A plain hash proves nothing (anyone can re-hash an edited file), so we sign with a non-exportable key that lives in the phone's secure hardware.
2. The phone is on a stand, not on the athlete, so its sensors only prove the camera stayed still; the motion itself is checked with physics.
3. Jump height is computed two ways: flight time (h = g·t²/8) and body-scaled hip rise. Speeding up or slowing down a video breaks gravity and breaks the agreement, so the measurement doubles as a cheat detector.

**Target:** one Android app (Kotlin, Jetpack Compose, CameraX, MediaPipe), one tiny Kotlin server (Ktor + SQLite) on a laptop, and one shared pure-Kotlin module for scoring and verification. About 6 working days for 2–4 people or a set of coding agents.

## 2. Why it matters

India wants phone-based talent assessment and keeps hitting the same two walls: scale and trust.

- **SAI is asking for exactly this.** A 2026 SAI/NeGD concept note specifies an AI app for a 10-test fitness battery, with targets like jump height within ±2 cm and sit-up counting ≥ 95%, and demands unedited single-take videos with a signed declaration from athlete and coach ([concept note, Apr 2026](https://sportsauthorityofindia.nic.in/sai_new/public/assets/uploads/documents/e6cbda80-9511-4e6c-9e36-afdc920d8351.pdf)).
- **Physical centres don't scale.** The KIRTI talent programme logged about 1.88 lakh assessments against a 20 lakh annual target ([Circle of Sport, Aug 2026](https://circleofsport.com/sports-ministrys-ambitious-talent-identification-plan-comes-to-a-standstill/)).
- **Fraud is the headline problem.** At least 500 wrestlers were disqualified from India's U-17 championships in June 2026 for age fraud, and athletes have submitted other people's bone tests ([Reuters via Daily Sabah](https://www.dailysabah.com/sports/500-wrestlers-expose-indias-endemic-age-fraud-problem/news)).
- **Cheap phones are the target.** Phones under $200 were about 58% of India's shipments in 1H 2025 ([IDC via EE Times India](https://www.eetindia.co.in/?p=25868)), so the app must run on a 3–4 GB RAM phone at 30 fps.
- **Competitors stop at measurement.** Existing student and startup builds (including the SIH 2025 winner for SAI's statement) use pose estimation to count and measure; the ones we found do not prove the footage is genuine.

## 3. Scope

Build the smallest thing that makes the full story true on stage: two tests, sealing, two physics checks, a witness co-sign, sync, and a verifiable certificate. Everything else is stretch or cut.

### 3.1 Must build

| # | Item | Notes |
| --- | --- | --- |
| M1 | Athlete list (local) | Name, age, sex, height (cm), weight (kg). No login. |
| M2 | Standing vertical jump, scored live | Hero test. Auto-detect jumps, best of 3, both height methods shown. |
| M3 | Sit-ups (30 s), counted live | Second test, simple rep state machine. |
| M4 | Skeleton overlay on camera preview | The visual hook. |
| M5 | Seal | SHA-256 of video + keypoint log, manifest signed with an Android Keystore EC key. |
| M6 | Five integrity checks | Tamper, gravity, two-method agreement, camera steady, witness. |
| M7 | Import a video file | Scores any MP4 so the slowed "cheat" clip can be shown; imported results are never "Verified". |
| M8 | "Simulate edit" button | Alters a copy of a sealed session's video and re-verifies, to show tamper detection. |
| M9 | Witness co-sign by QR | Same app in Witness mode on a second phone; two QR scans; works offline. |
| M10 | Sync to laptop server | One "Sync" button; uploads manifest, signature, keypoints and video. |
| M11 | Server re-verify + certificate | Re-checks signature, hashes and physics with the shared code; issues a signed certificate. |
| M12 | Verify page + mini dashboard | Public `/v/{token}` page; one list page and one detail page for results. |

### 3.2 Stretch (only after the demo flow works end to end)

- Hindi UI and spoken prompts.
- Face match at the start of a take to flag a stand-in performer.
- Witness "start code" QR shown to the camera at the start of recording (proves the witness was present live).
- Server-side verification of Android Key Attestation (show TEE/StrongBox level).
- Percentile band against a small provisional norms table.
- One more test (standing broad jump).

### 3.3 Cut

Shuttle run and the other SAI tests, logins and roles, DigiLocker/APAAR/Aadhaar, parental-consent flow (a single "guardian consent obtained" checkbox stays), database encryption, background sync, Postgres/Docker, Play Integrity, report-card PDF, validation mode, audit-log chaining, multi-module Gradle setup.

## 4. Live demo flow (what the build must make work)

This is the one end-to-end path the prototype must run reliably; if a feature is not on this path, it is stretch. Agents should treat it as the master acceptance test.

**Setup:** Phone A ("coach") on a tripod about 3 m from the athlete, side-on. Phone B ("witness") in a teammate's hand. Laptop running the server on the same hotspot. A pre-made slowed clip (`demo/slowmo_jump.mp4`) is on Phone A.

1. **Add athlete.** On Phone A, add the volunteer: name, age, sex, height. Takes under 20 s.
2. **Start vertical jump.** The setup screen shows where to stand; a pre-flight check turns green when the full body is in frame and the phone is steady.
3. **Record.** Athlete does 3 jumps. The preview shows the skeleton and marks each take-off and landing; a provisional height appears after each jump.
4. **Result in under 5 s.** Best jump in big numbers, both methods (flight time and hip rise) and how closely they agree, one line of feedback, and the badge **Verified** with all checks green.
5. **Witness co-sign, offline.** Phone A shows a session QR; Phone B (Witness mode) scans it, shows the athlete initials and score, the teammate taps "I witnessed this", Phone B shows a return QR, Phone A scans it. Badge becomes **Witnessed**. Both phones can be in airplane mode.
6. **Sync.** Tap Sync on Phone A. The laptop dashboard shows the new result with the same badge and checks, re-computed by the server.
7. **Certificate.** Phone A shows a certificate QR. A judge scans it with their own phone camera and the browser opens the verify page: **Valid**, test, height, initials, date, witness ID.
8. **Cheat 1: slowed video.** On Phone A, import `slowmo_jump.mp4`. It scores the jump but shows **Flagged**: "Motion too slow for real gravity: measured 2.4 m/s², expected about 9.8".
9. **Cheat 2: edited file.** Open the genuine session from step 4 and tap "Simulate edit". The badge turns **Tampered**: "Video changed after recording; signature no longer matches". Syncing it shows the server rejects it too.
10. **Optional: sit-ups.** 30 s of sit-ups with a live rep counter and any rejected reps shown with a reason.

If the venue has no space to jump, steps 2–4 can run on a pre-recorded genuine clip through the import path, but the default is live.

## 5. Features and acceptance criteria

Each result gets exactly one badge, decided by five checks; every check shows its measured value and a one-line reason.

### 5.1 Badges

| Badge | Rule | Colour |
| --- | --- | --- |
| Witnessed | Sealed on this device, all checks pass, valid witness co-sign | Dark green |
| Verified | Sealed on this device, all checks pass, no witness | Green |
| Unsealed | Imported video, physics checks pass (score shown, no certificate) | Grey |
| Flagged | Gravity, agreement or steadiness check fails | Amber |
| Tampered | Signature or any hash does not match | Red |

### 5.2 Checks

| ID | Check | Pass rule | Fails → |
| --- | --- | --- | --- |
| C1 | Tamper | Manifest signature verifies; SHA-256 of video and keypoint log match the manifest | Tampered |
| C2 | Gravity (jump only) | Parabola fitted to hip during flight gives 9.81 ± 2.5 m/s² | Flagged |
| C3 | Two-method agreement (jump only) | abs(flight-time height − hip-rise height) ≤ max(4 cm, 20%) | Flagged |
| C4 | Camera steady | Gyroscope RMS < 0.05 rad/s during recording (skipped for imports) | Flagged |
| C5 | Witness | Witness signature valid over this session's digest, different key from the coach | Upgrades Verified → Witnessed |

### 5.3 Acceptance criteria

**M1 Athletes**

- [ ] Add, edit and list athletes; height is required before a jump test.
- [ ] A "guardian consent obtained" checkbox is required for athletes under 18.

**M2 Vertical jump**

- [ ] Detects take-off and landing automatically for up to 3 jumps; best jump selected.
- [ ] Shows flight-time height, hip-rise height and their difference.
- [ ] On a genuine 30 fps take of an adult, flight-time height is within about 3 cm of a slow-motion reference (checked once by hand, not a formal study).

**M3 Sit-ups**

- [ ] 30 s timer starts on the first upward movement; counts reps that reach the up and down angles; shows rejected reps with a reason.
- [ ] Count matches a human count on 3 test sets.

**M4 Live overlay**

- [ ] Skeleton draws at ≥ 15 fps on a 4 GB mid-range phone; take-off/landing markers flash on detection.

**M5 Seal**

- [ ] Video is recorded to app-private storage only.
- [ ] Within 1 s of stopping, the app writes `manifest.json` and `manifest.sig` signed by the device's Keystore key.

**M6 Checks**

- [ ] All five checks run on the phone in under 1 s and appear on a "Why this badge?" sheet with values and limits.

**M7 Import**

- [ ] Picks an MP4 from storage, runs the same pose + scoring pipeline, badge is never above Unsealed.
- [ ] The bundled 0.5× slowed clip is Flagged by C2.

**M8 Simulate edit**

- [ ] Copies the session, changes bytes in the copy's video, re-verifies, shows Tampered with the reason.

**M9 Witness**

- [ ] Two-scan QR exchange works with both phones in airplane mode; badge updates to Witnessed.

**M10–M12 Sync, server, verify**

- [ ] Sync uploads pending sessions; the server returns its own badge, which matches the phone for genuine sessions.
- [ ] A sealed session whose video was altered is rejected by the server as Tampered.
- [ ] Certificate QR opens `/v/{token}` on any phone and shows Valid; changing one character of the token shows Invalid.

## 6. Architecture

The coach phone does all measuring and sealing offline; the laptop server re-runs the same `:core` code before it issues a certificate.

&#91;embedded content: PRAMAAN prototype architecture · two phones, shared core, laptop server\]

Read it top to bottom: capture, pose and scoring feed the checks and the seal; the witness phone co-signs by QR; Sync sends the session to the server, which re-verifies, stores and certifies. Section 8 maps each box to a folder.

## 7. Tech stack

Kotlin everywhere, three Gradle modules, no infrastructure beyond a laptop. Versions are floors; bump each to its latest stable when creating the project and pin them in `gradle/libs.versions.toml`.

| Area | Choice | Floor version | Used for |
| --- | --- | --- | --- |
| Platform | minSdk 26, target/compileSdk 36, JDK 17, Kotlin 2.2, AGP 8.11 | — | Keystore EC keys are available from Android 8 |
| UI | Jetpack Compose (BOM) + Material 3 + Navigation Compose | 2025.08.00 / 2.9 | All screens |
| Camera | CameraX: camera2, lifecycle, video, view | 1.5.x | Preview, MP4 recording, frame analysis |
| Pose | MediaPipe Tasks Vision `PoseLandmarker` (`pose_landmarker_lite.task`) | 0.10.2x | 33 landmarks per frame, CPU (GPU optional) |
| QR | ML Kit Barcode Scanning (bundled) to read, ZXing core to draw | 17.3 / 3.5.3 | Witness exchange, certificate QR |
| Storage | Room (KSP) | 2.7.x | Athletes and sessions |
| Async | Kotlin coroutines + Flow | 1.10.x | Pipeline and UI state |
| Serialization | kotlinx-serialization-json | 1.9.x | Manifest, payloads |
| Network | Ktor client (OkHttp engine) | 3.2.x | Sync |
| Crypto | Platform JCA: `AndroidKeyStore`, `SHA256withECDSA`, `MessageDigest` | — | Signing and hashing |
| DI | None; a simple `AppContainer` object | — | Keeps setup fast |
| Server | Ktor server (Netty) + SQLite (`sqlite-jdbc`) + Exposed | 3.2.x / 3.4 / 0.6x | API, dashboard, verify page |
| Certificates | Nimbus JOSE + JWT on the server | 10.x | ES256 compact JWS |
| Dashboard UI | `kotlinx.html` + one CSS file | 0.11.x | Server-rendered pages |
| Tests | JUnit 5 + Kotest assertions (core, server); JUnit 4 (app) | — | Synthetic-track tests |

## 8. Repo structure

Three modules: `:app` (Android), `:core` (pure Kotlin/JVM, shared by app and server) and `:server` (Ktor). Base package `app.pramaan` (`in` is a Kotlin keyword, so not `in.pramaan`). `:core` must never import `android.*`.

```text
pramaan/
├── README.md                  # how to run app + server, demo checklist
├── AGENTS.md                  # rules from section 13.3
├── settings.gradle.kts        # include(":app", ":core", ":server")
├── build.gradle.kts
├── gradle/libs.versions.toml
├── tools/
│   └── make_slowmo.sh         # ffmpeg -i in.mp4 -filter:v "setpts=2.0*PTS" -an out.mp4
│
├── core/                      # pure Kotlin: scoring, integrity, manifest, crypto verify
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/app/pramaan/core/
│       │   ├── model/      Landmark.kt  PoseFrame.kt  TestType.kt  Badge.kt  CheckResult.kt
│       │   ├── manifest/   SessionManifest.kt  WitnessPayloads.kt  CanonicalJson.kt
│       │   ├── scoring/    Smoothing.kt  BodyScale.kt  VerticalJumpAnalyzer.kt  JumpResult.kt
│       │   │               SitUpAnalyzer.kt  SitUpResult.kt  Feedback.kt
│       │   ├── integrity/  IntegrityEngine.kt  Checks.kt  ParabolaFit.kt
│       │   └── crypto/     Sha256.kt  EcdsaVerifier.kt  DerRaw.kt  Base64Url.kt
│       └── test/kotlin/app/pramaan/core/
│           SyntheticTracks.kt  VerticalJumpAnalyzerTest.kt  SitUpAnalyzerTest.kt
│           IntegrityEngineTest.kt  CanonicalJsonTest.kt
│
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── models/pose_landmarker_lite.task
│       │   └── demo/   genuine_jump.mp4  slowmo_jump.mp4
│       ├── res/values/strings.xml
│       ├── res/xml/network_security_config.xml     # allow http to the laptop's LAN IP
│       └── kotlin/app/pramaan/
│           ├── PramaanApp.kt  MainActivity.kt  AppContainer.kt  Nav.kt
│           ├── data/      PramaanDb.kt  AthleteEntity.kt  SessionEntity.kt  Daos.kt
│           │              SessionStore.kt  SyncClient.kt
│           ├── camera/    CameraController.kt  PoseEngine.kt  VideoFilePoseRunner.kt  GyroRecorder.kt
│           ├── seal/      DeviceKey.kt  Sealer.kt  LocalVerifier.kt  TamperSimulator.kt
│           ├── witness/   QrCodec.kt  WitnessSigner.kt
│           └── ui/
│               ├── theme/       Theme.kt  Colors.kt  Type.kt
│               ├── components/  BadgeChip.kt  SkeletonOverlay.kt  QrImage.kt  QrScanner.kt
│               │                ChecksSheet.kt  BigMetric.kt
│               └── screens/     HomeScreen.kt  AthletesScreen.kt  AthleteEditScreen.kt
│                                TestSetupScreen.kt  RecordScreen.kt  ResultScreen.kt
│                                CoSignScreen.kt  WitnessScreen.kt  CertificateScreen.kt
│                                SyncScreen.kt  ImportScreen.kt  + one ViewModel per screen
│
└── server/
    ├── build.gradle.kts
    ├── keys/                  # gitignored: cert_signing_key.pem (ES256), created on first run
    └── src/main/
        ├── kotlin/app/pramaan/server/
        │   Application.kt  Db.kt  ApiRoutes.kt  SessionVerifier.kt
        │   Certificates.kt  DashboardPages.kt  VerifyPage.kt
        └── resources/  application.conf  static/style.css
```

## 9. Data formats

A session is a folder of files plus one signed manifest; the database only indexes sessions. Signed JSON is serialised canonically (keys sorted, no whitespace) by `CanonicalJson.kt` before hashing or signing; signatures are ECDSA P-256 with SHA-256, DER-encoded, base64url.

### 9.1 Session folder: `filesDir/sessions/{sessionId}/`

| File | Content |
| --- | --- |
| `video.mp4` | The full take (720p, 30 fps), app-private |
| `frames.jsonl` | One `PoseFrame` per analysed frame |
| `gyro.jsonl` | Gyroscope samples (`t`, `x`, `y`, `z`) |
| `manifest.json` | `SessionManifest`, canonical JSON |
| `manifest.sig` | Coach-device signature over `manifest.json` |
| `witness.json` | Witness attestation, if co-signed |

### 9.2 PoseFrame (one line of `frames.jsonl`)

```json
{"t":918273645123,"lm":[[0.5123,0.3121,0.98],[0.5101,0.2950,0.97]]}
```

`t` is the frame timestamp in nanoseconds (camera timestamp for live takes, video presentation time for imports); `lm` holds 33 `[x, y, visibility]` entries in normalised image coordinates, rounded to 4 decimals.

### 9.3 SessionManifest (signed)

```json
{
  "v": 1,
  "sessionId": "01JA7Q8R4C2D3E4F5G6H7J8K9M",
  "test": "VERTICAL_JUMP",
  "source": "CAMERA",
  "athlete": { "id": "01JA…", "initials": "M.K.", "age": 14, "sex": "F", "heightCm": 152.0 },
  "devicePublicKey": "base64url-X509-SubjectPublicKeyInfo",
  "capturedAt": "2026-10-12T09:14:03Z",
  "fps": 30,
  "hashes": { "video": "b64url-sha256", "frames": "b64url-sha256", "gyro": "b64url-sha256" },
  "result": { "unit": "cm", "best": 38.2,
              "attempts": [ { "flightCm": 38.9, "hipRiseCm": 37.4, "gravity": 9.4, "valid": true } ] },
  "checks": [ { "id": "C2", "pass": true, "value": "9.4", "limit": "7.3–12.3" } ],
  "badge": "VERIFIED"
}
```

For sit-ups, `result` is `{ "unit": "reps", "best": 22, "rejected": [ { "t": 12.4, "reason": "NOT_UP" } ] }`.

### 9.4 Witness QR payloads

Both QRs are plain text: `PRM1<type>.<base64url(canonical json)>.<base64url(signature)>`.

| Type | Shown by → scanned by | JSON | Signed by |
| --- | --- | --- | --- |
| `S` session | Coach phone → witness phone | `sid`, `d` (SHA-256 of `manifest.json`), `test`, `best`, `unit`, `ini` | Coach key |
| `W` witness | Witness phone → coach phone | `sid`, `d`, `wpk` (witness public key), `t` (epoch ms) | Witness key |

### 9.5 Certificate (ES256 compact JWS, issued by the server)

```json
{ "iss": "pramaan", "jti": "01JA…", "iat": 1791364461, "sid": "01JA7Q8R…", "ini": "M.K.",
  "test": "VERTICAL_JUMP", "val": 38.2, "unit": "cm", "badge": "WITNESSED", "wit": "4be81d02" }
```

The QR encodes `http://{laptop-ip}:8080/v/{jws}`. Only initials appear, never the full name.

### 9.6 Tables

| Where | Table | Columns |
| --- | --- | --- |
| App (Room) | `athletes` | id, name, initials, age, sex, heightCm, weightKg, guardianConsent, createdAt |
| App (Room) | `sessions` | id, athleteId, test, source, folder, best, unit, badge, synced, serverBadge, certificateJws, createdAt |
| Server (SQLite) | `sessions` | id, test, initials, best, unit, deviceKey, witnessKey, phoneBadge, serverBadge, checksJson, receivedAt, folder |
| Server (SQLite) | `certificates` | jti, sessionId, jws, issuedAt |

## 10. How it works: pipeline and algorithms

One pipeline for live and imported video: frames with timestamps → pose → analyzer → checks → seal. All maths lives in `:core` so the server can repeat it exactly.

### 10.1 Capture pipeline (`app/camera`)

1. Bind CameraX **Preview + VideoCapture + ImageAnalysis** on the back camera. Video: `Recorder` at `Quality.HD`, written with `FileOutputOptions` to the session folder (never MediaStore). Analysis: 640×480, `STRATEGY_KEEP_ONLY_LATEST`. Pick a demo phone where this triple binding works; no fallback needed.
2. For each analysis frame: run `PoseLandmarker` in `LIVE_STREAM` mode, convert the result to a `PoseFrame` with `imageInfo.timestamp`, append it to `frames.jsonl`, push it to the overlay and the live analyzer.
3. `GyroRecorder` logs the gyroscope at `SENSOR_DELAY_GAME` while recording.
4. On stop (`VideoRecordEvent.Finalize`): finalise the analyzer, run the checks, hash the three files, build and sign the manifest (10.5).
5. **Imports** (`VideoFilePoseRunner`): read frames with `MediaMetadataRetriever.getFrameAtIndex`, run `PoseLandmarker` in `VIDEO` mode, and set each frame's time from the file itself: t = index × duration ÷ frameCount (`METADATA_KEY_DURATION`, `METADATA_KEY_VIDEO_FRAME_COUNT`). A slowed file therefore has stretched timestamps, which is what the gravity check catches.

### 10.2 Vertical jump (`VerticalJumpAnalyzer`)

The phone is side-on, so vertical image position tracks height. Image y grows downward; convert to "up" before use.

1. **Body scale:** during the first second of quiet standing, measure the pixel distance from mid-eye to mid-heel; scale s (cm per px) = heightCm × 0.936 ÷ that distance (0.936 ≈ eye height ÷ stature).
2. **Smooth** hip-centre and toe y with a moving average of 5 frames (or Savitzky–Golay if time allows).
3. **Baseline:** median toe height over the 0.5 s of low motion before each jump.
4. **Take-off / landing:** take-off = first frame where both toes rise more than 2% of body height above baseline; landing = first frame after the peak where they return within that band. Interpolate linearly between the two frames around each crossing for sub-frame timing.
5. **Validity:** flight time between 0.12 and 1.0 s, and a downward dip of the hip before take-off.
6. **Flight-time height** (reported value): h = g × t² ÷ 8.
7. **Hip-rise height** (cross-check): (hip height at peak − hip height at take-off) × s.
8. **Gravity estimate:** fit hip height in metres against time over the flight frames with least squares, y = a·t² + b·t + c; then g\_est = −2a.
9. Best of up to 3 valid jumps. **Feedback:** if knee angle at the bottom of the dip is above 120°, "Dip a little deeper before jumping"; if wrists stay below the shoulders, "Swing your arms up"; else "Good technique".

```latex
h_{flight} = \frac{g\,t^{2}}{8}, \qquad g_{est} = -2a \ \text{ where } \ y_{hip}(t) \approx a t^{2} + b t + c
```

### 10.3 Sit-ups (`SitUpAnalyzer`)

Side view, athlete lying with knees bent. Trunk angle = angle of the hip→shoulder line above horizontal. State machine: DOWN when angle < 25°, UP when angle > 70°; a rep counts on DOWN → UP → DOWN, at least 0.5 s long. A movement that leaves DOWN but never reaches UP is rejected as `NOT_UP`. The 30 s timer starts at the first upward movement; reps finishing after 30 s don't count.

### 10.4 Checks (`IntegrityEngine`)

| Check | Computation |
| --- | --- |
| C1 Tamper | Verify `manifest.sig` with `devicePublicKey`; recompute SHA-256 of `video.mp4`, `frames.jsonl`, `gyro.jsonl` and compare with `hashes` |
| C2 Gravity | Every valid jump: 7.31 ≤ g\_est ≤ 12.31 m/s² |
| C3 Agreement | Best jump: abs(flight − hipRise) ≤ max(4 cm, 0.2 × flight) |
| C4 Steady | Root-mean-square gyro magnitude over the take < 0.05 rad/s |
| C5 Witness | Witness signature verifies over `{sid, d, wpk, t}`, `d` equals SHA-256 of `manifest.json`, `wpk` ≠ `devicePublicKey` |

Badge = Tampered if C1 fails; else Flagged if C2, C3 or C4 fails; else Unsealed for imports; else Witnessed if C5 passes; else Verified. A slowed 0.5× clip gives g\_est ≈ 2.45; a 2× clip gives ≈ 39, so both fail C2.

### 10.5 Device key and sealing (`app/seal`)

```kotlin
// DeviceKey.kt: created once, on first launch
val spec = KeyGenParameterSpec.Builder("pramaan_device", KeyProperties.PURPOSE_SIGN)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setDigests(KeyProperties.DIGEST_SHA256)
    .build()
KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
    .apply { initialize(spec) }.generateKeyPair()

// Sealer.kt
val bytes = CanonicalJson.encode(manifest)
val sig = Signature.getInstance("SHA256withECDSA").run {
    initSign(keyStore.getKey("pramaan_device", null) as PrivateKey); update(bytes); sign()
}
```

The private key never leaves secure hardware; the public key goes into every manifest. `TamperSimulator` copies a session folder, overwrites 4 KB in the middle of the copy's `video.mp4`, and runs `LocalVerifier` on the copy.

### 10.6 Witness co-sign (`app/witness`)

1. Coach phone (Result screen → "Get witness") shows QR `S` signed by its key.
2. Witness phone (Home → "Witness mode") scans it, checks the signature, shows test, best value and initials.
3. Witness taps "I witnessed this"; the phone signs `{sid, d, wpk, t}` with its own device key and shows QR `W`.
4. Coach phone scans `W`, runs C5, saves `witness.json`, updates the badge.

No network is used. Every install has one device key, so any phone with the app can be a witness.

## 11. Screens

Twelve Compose screens in one activity. Big type, one primary button per screen, and the badge always visible on results.

| Screen | Purpose | Key elements | Goes to |
| --- | --- | --- | --- |
| Home | Entry | Buttons: Athletes, Witness mode, Import video, Sync (with pending count); recent results list | Any |
| Athletes | List | Search, add button, last result + badge per athlete | Athlete edit, Test setup |
| Athlete edit | Create/update | Name, age, sex, height, weight, guardian-consent checkbox if under 18 | Athletes |
| Test setup | Prepare | Pick Vertical jump or Sit-ups; diagram of camera position; live pre-flight checklist (full body in frame, phone steady) | Record |
| Record | Capture | Camera preview, skeleton overlay, take-off/landing markers or rep counter, provisional value, Stop | Result |
| Result | Outcome | Big value, both jump methods, feedback line, badge; buttons: Why this badge?, Get witness, Certificate, Simulate edit | Checks sheet, Co-sign, Certificate |
| Checks sheet | Explain | C1–C5 with pass/fail, measured value, limit, one-line reason | Result |
| Co-sign | Coach side of the witness exchange | Shows QR `S`, then camera to scan QR `W`; badge animates to Witnessed | Result |
| Witness | Witness side | Scan `S` → summary + "I witnessed this" → show QR `W` | Home |
| Import | Load a file | Pick MP4 (or a bundled demo clip), pick athlete and test, progress bar | Result |
| Sync | Upload | Pending sessions, Sync button, server URL field, server badge per session | — |
| Certificate | Share | Certificate QR (from server), value, badge, initials, date | — |

**Badge colours:** Witnessed dark green, Verified green, Unsealed grey, Flagged amber, Tampered red; always with an icon and the word, not colour alone.

**State:** one ViewModel per screen exposing a `StateFlow<UiState>`; camera and scoring work runs on `Dispatchers.Default`.

## 12. Server: API, verify page, dashboard

A single Ktor app on the laptop (`./gradlew :server:run`, port 8080, SQLite file `pramaan.db`). It trusts nothing from the phone: it re-runs `:core` checks before it agrees with the badge.

### 12.1 Endpoints

| Method + path | Purpose | Request | Response |
| --- | --- | --- | --- |
| `POST /api/sessions` | Upload a session (multipart) | parts: `manifest.json`, `manifest.sig`, `video.mp4`, `frames.jsonl`, `gyro.jsonl`, `witness.json` (optional) | `{ sessionId, serverBadge, checks[], certificate? }` |
| `GET /api/sessions/{id}` | Status | — | Same as above |
| `GET /v/{jws}` | Public verify page | — | HTML: big Valid/Invalid, test, value, badge, initials, date, witness ID |
| `GET /` | Dashboard list | — | HTML table of sessions, newest first |
| `GET /sessions/{id}` | Dashboard detail | — | HTML: badge, checks with values, video player, jump numbers |
| `GET /media/{id}/video.mp4` | Video for the detail page | — | MP4 |

No authentication for the prototype; the server is only reachable on the demo hotspot.

### 12.2 Upload handling (`SessionVerifier`)

1. Save the parts to `data/{sessionId}/`.
2. Run `IntegrityEngine` from `:core` on the saved files: signature, hashes, then re-run `VerticalJumpAnalyzer` / `SitUpAnalyzer` on `frames.jsonl` and re-check C2–C5.
3. Server badge = the engine's result; store phone badge and server badge side by side.
4. If the server badge is Verified or Witnessed, issue a certificate: ES256 JWS with the claims in 9.5, signed by `keys/cert_signing_key.pem` (generated on first run if missing). Return it to the phone.
5. Tampered sessions get HTTP 200 with `serverBadge: "TAMPERED"` so the phone can show the rejection.

### 12.3 Pages

- **Dashboard list:** time, initials, test, value, phone badge, server badge, top failing check. Auto-refresh every 5 s so new syncs appear during the demo.
- **Detail:** the five checks with values and limits, both jump estimates per attempt, embedded video.
- **Verify page:** verifies the JWS signature with the server's public key; Valid in green or Invalid in red, readable on a phone screen.

## 13. Build plan, agent rules and tests

Build `:core` first with synthetic data (no phone needed), then the camera path, then trust features, then the server. The live demo flow in section 4 is the finish line.

### 13.1 Six-day plan

| Day | Build | Done when |
| --- | --- | --- |
| 1 | Scaffold 3 modules; `:core` models, `CanonicalJson`, hashing, ECDSA verify; `SyntheticTracks`; `VerticalJumpAnalyzer` | T1–T3 pass |
| 2 | `SitUpAnalyzer`, `IntegrityEngine`; app camera + MediaPipe + skeleton overlay on the Record screen | T4–T6 pass; skeleton visible on a phone |
| 3 | Live jump scoring, sealing, Result screen, checks sheet, athletes | Steps 1–4 of the demo flow work |
| 4 | Import path, Simulate edit, witness QR exchange | Steps 5, 8, 9 work offline |
| 5 | Server (upload, verifier, certificate, verify page, dashboard); app Sync + Certificate screens | Steps 6–7 work; T7–T8 pass |
| 6 | Sit-ups screen, polish, tune thresholds on real takes, run the full flow 3 times | Whole section 4 runs 3 times in a row without a code change |

With 3–4 people, split: one on `:core`, one on camera/pose/UI, one on seal + witness, one on server.

### 13.2 Tests (in `core/src/test` and `server/src/test`)

| ID | Input | Expected |
| --- | --- | --- |
| T1 | Synthetic jump, flight 0.500 s, 30 fps, small noise | Flight height within ±2 cm of 30.7 cm; C2 and C3 pass |
| T2 | Same track with timestamps × 2 (slow motion) | g\_est ≈ 2.45; C2 fails; badge Flagged |
| T3 | Same track with timestamps × 0.5 (sped up) | g\_est ≈ 39; C2 fails |
| T4 | 22 synthetic sit-ups + 3 that stop at 50° | Count 22; 3 rejected `NOT_UP` |
| T5 | Signed manifest, one byte of the frames file changed | C1 fails; badge Tampered |
| T6 | Witness signature by the same key as the device | C5 fails; badge stays Verified |
| T7 | Server upload of a genuine signed fixture session | Server badge equals phone badge; certificate returned |
| T8 | Verify page with a JWS whose payload was altered | Shows Invalid |

`SyntheticTracks.kt` generates 33-landmark pose frames for a standing person, a countermovement jump following real projectile motion for a given flight time and frame rate, and sit-up cycles at given angles, with optional Gaussian noise.

### 13.3 `AGENTS.md`

1. Section 4 (live demo flow) is the master acceptance test; don't build anything not needed for it until it passes end to end.
2. `:core` stays pure Kotlin: no `android.*` imports, no system clock; time always comes from frame timestamps.
3. Every analyzer or check change comes with a test in `core/src/test`.
4. Add dependencies only via `gradle/libs.versions.toml`, pinned to exact versions.
5. Keep it simple: no DI framework, no extra modules, no background workers, no auth.
6. Session files stay in app-private storage; never write video to the gallery.
7. Don't invent official norms or accuracy numbers; anything measured goes in `README.md` with how it was measured.

### 13.4 Open questions

- Which phones you will demo on (the triple CameraX binding and 30 fps pose speed must be checked on them on day 2).
- Whether you want Hindi UI in the demo (stretch, about half a day).
