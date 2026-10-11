# Implementation Plan: PRAMAAN Standing Broad Jump (SBJ)

> **Context:** Adding the Standing Broad Jump as a third AI-scored test (following Vertical Jump and Sit-ups), adhering to SAI guidelines (measuring within ±2 cm with floor-tape calibration), physics-based fraud checks (C2 gravity + C3 horizontal motion consistency + C5 calibration agreement), and built-in accuracy validation.
>
> **Master Rules & Constraints Checklist:**
> 1. Section 4 (live demo flow) is master acceptance test? **Yes.**
> 2. `:core` stays pure Kotlin with no `android.*` or system clock (timestamps from frames only)? **Yes.**
> 3. Existing Vertical Jump and Sit-ups behaviour 100% preserved and tests green? **Yes.**
> 4. Do not write code until `PLAN_SBJ.md` is approved? **Yes (Phase 0).**
> 5. Wait for approval after each phase before starting the next phase? **Yes.**

---

## 1. File & Class Mapping to Repository

| Spec Reference / Mention | Actual Path in Repository | Status | Description & Role |
|---|---|---|---|
| **Core Module (`:core`)** | | | |
| `model/TestType.kt` | [core/.../model/TestType.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/model/TestType.kt) | Existing (Mod) | Add `BROAD_JUMP("Standing Broad Jump", "cm")` |
| `scoring/broadjump/BroadJumpParams.kt` | [core/.../scoring/broadjump/BroadJumpParams.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/scoring/broadjump/BroadJumpParams.kt) | **NEW** | Holds all configurable constants, thresholds, version `bj-1.0.0` (§5.9) |
| `scoring/broadjump/GroundCalibration.kt` | [core/.../scoring/broadjump/GroundCalibration.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/scoring/broadjump/GroundCalibration.kt) | **NEW** | Data models: `CalibMode`, `NormPoint`, `GroundCalibration`, `GroundFrame` projection |
| `scoring/broadjump/BroadJumpResult.kt` | [core/.../scoring/broadjump/BroadJumpResult.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/scoring/broadjump/BroadJumpResult.kt) | **NEW** | Data models: `BjAttempt`, `BroadJumpResult`, `CalibSummary`, `Foul`, `BjNote`, `ContactPoint` |
| `scoring/broadjump/BroadJumpAnalyzer.kt` | [core/.../scoring/broadjump/BroadJumpAnalyzer.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/scoring/broadjump/BroadJumpAnalyzer.kt) | **NEW** | Core logic: bootstrap, `findFlights`, `findStance`, fouls, landing contact, distance, biomechanics |
| `scoring/broadjump/BroadJumpLiveTracker.kt` | [core/.../scoring/broadjump/BroadJumpLiveTracker.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/scoring/broadjump/BroadJumpLiveTracker.kt) | **NEW** | Real-time lightweight tracker for live HUD (`standingStill`, attempt counter, `≈ distance`) |
| `scoring/Feedback.kt` | [core/.../scoring/Feedback.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/scoring/Feedback.kt) | Existing (Mod) | Add broad jump feedback keys (`fb_bj_*`) |
| `integrity/BroadJumpChecks.kt` | [core/.../integrity/BroadJumpChecks.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/integrity/BroadJumpChecks.kt) | **NEW** | C2 Gravity Parabola, C3 Horizontal Consistency, C5 Calibration Agreement |
| `integrity/IntegrityEngine.kt` | [core/.../integrity/IntegrityEngine.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/integrity/IntegrityEngine.kt) | Existing (Mod) | Dispatch by `TestType.BROAD_JUMP` without altering `VERTICAL_JUMP` or `SIT_UPS` |
| `manifest/SessionManifest.kt` | [core/.../manifest/SessionManifest.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/manifest/SessionManifest.kt) | Existing (Mod) | Add optional `analyzerVersion`, `analysisFrame`, `calibration`, `bjResult`, `stills` hashes |
| `validation/AccuracyStats.kt` | [core/.../validation/AccuracyStats.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/validation/AccuracyStats.kt) | **NEW** | Statistical module: bias, MAE, SD, 95% LoA, % within ±2/3 cm (Wilson CI), Bland-Altman data |
| `test/.../SyntheticBroadJump.kt` | [core/.../test/SyntheticBroadJump.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/test/kotlin/app/pramaan/core/SyntheticBroadJump.kt) | **NEW** | Synthetic pose track generator with trajectory modeling, noise injection, and foul synthesis |
| `test/.../BroadJumpAnalyzerTest.kt` | [core/.../test/BroadJumpAnalyzerTest.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/test/kotlin/app/pramaan/core/BroadJumpAnalyzerTest.kt) | **NEW** | Unit & acceptance test suite executing T-BJ-01 through T-BJ-20 |
| **Android App Module (`:app`)** | | | |
| `ui/screens/TestSetupScreen.kt` | [app/.../ui/screens/TestSetupScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/TestSetupScreen.kt) | Existing (Mod) | SBJ setup variant: side-on camera placement diagram, tape instructions, checklist chips |
| `ui/screens/RecordScreen.kt` | [app/.../ui/screens/RecordScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/RecordScreen.kt) | Existing (Mod) | SBJ HUD overlay: hold-still ring, live attempt counter ("Jump 1 of 3"), "≈ 184 cm" chip |
| `ui/broadjump/BroadJumpCalibrationScreen.kt` | [app/.../ui/broadjump/BroadJumpCalibrationScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/broadjump/BroadJumpCalibrationScreen.kt) | **NEW** | Calibration screen: frame scrubber, pinch-zoom, crosshair, line tap, mark tap chips, nudge buttons |
| `media/FrameExtractor.kt` | [app/.../media/FrameExtractor.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/media/FrameExtractor.kt) | **NEW** | Video frame extraction via `MediaMetadataRetriever` with rotation normalization |
| `media/StillRenderer.kt` | [app/.../media/StillRenderer.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/media/StillRenderer.kt) | **NEW** | Renders annotated landing stills (take-off line, tape mark, rear heel drop line, distance text) |
| `seal/Sealer.kt` | [app/.../seal/Sealer.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/seal/Sealer.kt) | Existing (Mod) | Support extra files (`calib_frame.jpg`, `stills/att{n}_landing.jpg`) and SBJ manifest encoding |
| `ui/screens/ResultScreen.kt` | [app/.../ui/screens/ResultScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/ResultScreen.kt) | Existing (Mod) | SBJ result block: best distance, attempts table with fouls/notes, landing still, biomechanics |
| `ui/athlete/AthleteHomeScreen.kt` | [app/.../ui/athlete/AthleteHomeScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/athlete/AthleteHomeScreen.kt) | Existing (Mod) | Enable Standing Broad Jump card in the test catalog |
| `res/values/strings.xml` & `values-hi` | `app/src/main/res/values*/strings.xml` | Existing (Mod) | Add EN and Hindi localized strings for SBJ (§11.5) |
| `ui/Nav.kt` | [app/.../ui/Nav.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/Nav.kt) | Existing (Mod) | Add route `Routes.BROAD_JUMP_CALIBRATION` between recording and result |
| **Server Module (`:server`)** | | | |
| `server/BroadJumpRescorer.kt` | [server/.../server/BroadJumpRescorer.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/BroadJumpRescorer.kt) | **NEW** | Re-executes `:core` analyzer on uploaded `frames.jsonl` + calibration + account height |
| `server/SessionVerifier.kt` | [server/.../server/SessionVerifier.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/SessionVerifier.kt) | Existing (Mod) | Dispatches `manifest.test == BROAD_JUMP` to `BroadJumpRescorer` |
| `server/AthleteRoutes.kt` | [server/.../server/AthleteRoutes.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/AthleteRoutes.kt) | Existing (Mod) | Accept optional multipart parts `calib_frame`, `still_1..3`, verify hashes, enforce §9 rules |
| `server/CoachRoutes.kt` | [server/.../server/CoachRoutes.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/CoachRoutes.kt) | Existing (Mod) | Serve stills and calibration frames; provide attempts & calibration in review detail |
| `server/DashboardPages.kt` | [server/.../server/DashboardPages.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/DashboardPages.kt) | Existing (Mod) | Render SBJ attempt table, stills, validation tab with Bland-Altman SVG plot |
| `server/Db.kt` | [server/.../server/Db.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/Db.kt) | Existing (Mod) | Add `ValidationEntries` table schema (`session_id`, `attempt`, `tape_cm`, `truth_foul`, `data_set`) |
| `server/Certificates.kt` | [server/.../server/Certificates.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/Certificates.kt) | Existing (Mod) | Handle `BROAD_JUMP` claims: `score` = best distance, `unit: "cm"`, `calibration: "MARKER"` |
| `test/.../Phase5BroadJumpServerTest.kt` | [server/.../test/Phase5BroadJumpServerTest.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/test/kotlin/app/pramaan/server/Phase5BroadJumpServerTest.kt) | **NEW** | Server integration tests T-BJ-S1 through T-BJ-S6 |

---

## 2. Conflicts Between Spec & Existing Code, with Proposed Resolutions

### Conflict 1: `SessionManifest` & `FileHashes` Schema Extensions
- **Conflict:** Existing `FileHashes` only contains `video`, `frames`, `gyro`. Existing `SessionResult` only contains `unit`, `best`, `attempts: List<JumpAttempt>?`, `rejected: List<RejectedRep>?`. SBJ requires `calib_frame` and `stills: Map<String, String>` hashes, plus `calibration` object and `bjResult` details.
- **Proposed Resolution:**
  - Add optional nullable fields with defaults to `SessionManifest`:
    ```kotlin
    val analyzerVersion: String? = null,
    val analysisFrame: AnalysisFrameDimensions? = null,
    val calibration: GroundCalibration? = null,
    val broadJumpResult: BroadJumpResult? = null,
    ```
  - In `FileHashes`, add optional fields: `val calibFrame: String? = null`, `val stills: Map<String, String>? = null`.
  - For vertical jump and sit-ups, these remain null and omit from canonical JSON, preserving 100% backward compatibility and test stability.

### Conflict 2: Integrity Checks Identity & Dispatch (C1–C5)
- **Conflict:** Existing code defines C2 as Vertical Jump Gravity, C3 as Two-Method Agreement ($h_1$ vs $h_2$), C4 as Steadiness, and C5 as Witness Attestation.
  In SBJ:
  - C2 is Gravity Parabola along the calibration axis.
  - C3 is Horizontal Consistency (linearity RMS $\le$ tolerance, forward speed $\le$ 5 m/s, reach gap).
  - C4 is Camera Steadiness (unchanged).
  - C5 is Calibration Agreement (`scaleDiffPct` $\le$ 10%, tilt $\le$ 20°, stance behind line).
- **Proposed Resolution:**
  - In `IntegrityEngine.kt`, branch check evaluation by `manifest.test`:
    - If `TestType.VERTICAL_JUMP`: evaluate existing C2 (Vertical Gravity) and C3 (Two-Method Agreement).
    - If `TestType.BROAD_JUMP`: evaluate SBJ C2 (Parabola on hip height), SBJ C3 (Horizontal Consistency), and SBJ C5 (Calibration Agreement).
    - If `TestType.SIT_UPS`: evaluate C1 and C4.
  - In `decideBadge()`: generalise physics check evaluation to check all non-C1 checks (`C2`, `C3`, `C4`, `C5` where applicable). A failure in any check results in `FLAGGED` (or `CALIB_APPROX`).

### Conflict 3: Session Sealing Pipeline & Extra Files
- **Conflict:** Existing `Sealer.kt` hashes exactly 3 files: `video.mp4`, `frames.jsonl`, `gyro.jsonl`. SBJ produces `calib_frame.jpg` and `stills/att{n}_landing.jpg` before sealing.
- **Proposed Resolution:**
  - Update `Sealer.sealSession()` with an optional parameter: `calibration: GroundCalibration? = null`, `extraFiles: Map<String, File> = emptyMap()`.
  - For SBJ, execute `BroadJumpAnalyzer.analyze()` to calculate results and generate annotated stills before signing the manifest. Hash all extra files and place their hashes into `manifest.hashes`.
  - Sign the completed manifest with `DeviceKey`.

### Conflict 4: Athlete Submission & Server Re-scoring
- **Conflict:** Server's `POST /api/sessions` expects multipart files and calls `SessionVerifier.verifyUploadedSession(sessionFolder)`. It currently assumes `VERTICAL_JUMP` or `SIT_UPS`.
- **Proposed Resolution:**
  - In `SessionVerifier.kt`, when `manifest.test == TestType.BROAD_JUMP`, invoke `BroadJumpRescorer.rescore(sessionFolder, account)`.
  - Re-score `frames.jsonl` using `manifest.calibration` and `account.height_cm`.
  - Enforce §9 rules:
    - Rule 1 (Height integrity): If `manifest.athlete.heightCm` differs from `account.height_cm` by $> 0.5\text{ cm}$ $\rightarrow$ `FLAGGED` (`HEIGHT_MISMATCH`).
    - Rule 2 (Mode integrity): If `calibration.mode == HEIGHT_ONLY` $\rightarrow$ `FLAGGED` (`CALIB_APPROX`).
    - Rule 3 (Rescore match): If server-computed best distance or attempts differ by $> 0.5\text{ cm}$ $\rightarrow$ `FLAGGED` (`SERVER_RESCORE_MISMATCH`).

### Conflict 5: Certificate Claims & Public Verification Page
- **Conflict:** Existing certificates display vertical jump height or sit-up reps. The public verification page (`/v/{jws}`) must display the calibration note for broad jumps.
- **Proposed Resolution:**
  - When `test == "BROAD_JUMP"`, emit certificate claims with `test: "BROAD_JUMP"`, `val: bestDistanceCm`, `unt: "cm"`, and custom claim `cal: "MARKER"`.
  - In `DashboardPages.kt` on `/v/{jws}`, render: *"Standing Broad Jump — {val} cm"* and *"Measured against a 200 cm floor marker"*.

---

## 3. Open Questions & Biomechanical / Hardware Considerations

1. **SAI Official Attempt Rules:**
   - *Question:* Does SAI prescribe best of 2 or best of 3? What happens if an athlete falls backward?
   - *Working Decision:* Default to 3 attempts in one continuous video take (§1.D5). If an athlete falls backward, per athletics conventions, the mark is measured to the rearmost contact point (hand or hip) and flagged with a note `FALL_BACK` rather than invalidating the jump.
2. **MediaPipe Heel Landmark Behaviour & Offset:**
   - *Observation:* MediaPipe landmark 29/30 corresponds to the calcaneus bone center, not the outer sole edge of athletic shoes.
   - *Working Decision:* Introduce `heelOffsetCm = 0.0` in `BroadJumpParams` as a tunable parameter. During Phase 4 validation, compute mean bias $d = \text{ai} - \text{tape}$ on the TUNE dataset and freeze `heelOffsetCm = mean(d)` before running the REPORT dataset.
3. **Android Bitmap Orientation from `MediaMetadataRetriever`:**
   - *Observation:* Video frames extracted from `video.mp4` on Android may be rotated by EXIF / display rotation metadata (e.g., 90° or 270°).
   - *Working Decision:* In `FrameExtractor.kt`, read `METADATA_KEY_VIDEO_ROTATION` and apply Matrix rotation so extracted bitmaps are guaranteed upright and match the `(x, y)` coordinate system of MediaPipe.
4. **Resolution Profile & Camera FPS:**
   - *Decision:* Maintain default 640×480 analysis stream for real-time 30/60 fps MediaPipe pose extraction on budget phones. The calibration frame and annotated stills are extracted from the recorded MP4 at high resolution (720p/1080p).

---

## 4. Phase-by-Phase Implementation Plan

### Phase 0: Architecture & Plan (Current)
- [x] Read `BroadJump_test.md`, `Build Description.md`, and verify existing codebase state.
- [x] Create [PLAN_SBJ.md](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/PLAN_SBJ.md) with complete mapping, conflict analysis, and open questions.
- [x] Approved by user.

### Phase 1: Pure Kotlin `:core` Analyzer & Checks (Complete)
- [x] Create `scoring/broadjump/` package with `BroadJumpParams`, `GroundCalibration`, `BroadJumpResult`, `BroadJumpAnalyzer`, `BroadJumpLiveTracker`.
- [x] Create `integrity/BroadJumpChecks.kt` (C2 Parabola on hip height, C3 Horizontal consistency, C5 Calibration agreement).
- [x] Update `IntegrityEngine.kt` to dispatch SBJ checks while keeping `VERTICAL_JUMP` and `SIT_UPS` unchanged.
- [x] Update `model/TestType.kt` and `manifest/SessionManifest.kt`.
- [x] Create `SyntheticBroadJump.kt` generator.
- [x] Implement and pass unit & acceptance tests **T-BJ-01 through T-BJ-18, T-BJ-20** (51/51 tests green).
- **Master Acceptance Condition:** All existing tests and all new SBJ core tests pass green with zero failures.

### Phase 2: Android `:app` Capture, Calibration & Results (Complete)
- [x] Enable Broad Jump tile on `AthleteHomeScreen.kt`.
- [x] Create `TestSetupScreen` SBJ side-on guide and checklist.
- [x] Implement SBJ HUD overlay in `RecordScreen.kt` using `BroadJumpLiveTracker`.
- [x] Create `media/FrameExtractor.kt` and `media/StillRenderer.kt`.
- [x] Build `BroadJumpCalibrationScreen.kt` with pinch-to-zoom, crosshair, line/mark tap, nudge arrows, and live validity checks.
- [x] Extend `Sealer.kt` to package extra files and sign SBJ manifests.
- [x] Update `ResultScreen.kt` with SBJ result presentation and attempt details.
- [x] Add English and Hindi strings to `res/values/strings.xml` and `res/values-hi/strings.xml`.
- **Master Acceptance Condition:** End-to-end recording, calibration, sealing, and result display work smoothly on device.

### Phase 3: Server Re-scoring & Role Workflow Integration (Complete)
- [x] Implement `BroadJumpRescorer.kt` in `:server`.
- [x] Update `SessionVerifier.kt` to handle SBJ sessions and dispatch server rescoring.
- [x] Update `AthleteRoutes.kt` to support multipart `calib_frame` and `stills/` subfolder upload, serve files, and enforce §9 rules.
- [x] Update `CoachRoutes.kt` to serve calibration frames/stills and display attempt details.
- [x] Update `DashboardPages.kt` to display SBJ sessions and update `/v/{jws}` certificate rendering.
- [x] Implement server test suite `Phase5BroadJumpServerTest.kt` covering **T-BJ-S1 through T-BJ-S5** + rescore mismatch (all 6 tests passing).
- **Master Acceptance Condition:** Two-role workflow (Athlete submits SBJ $\rightarrow$ Server re-scores $\rightarrow$ Coach reviews stills & signs $\rightarrow$ Certificate verifies) passes end-to-end.

### Phase 4: Accuracy Validation Module (Complete)
- [x] Create `validation_entries` database table in `Db.kt` and `ValidationRepo`.
- [x] Create pure Kotlin `AccuracyStats.kt` in `:core` to calculate MAE, SD, 95% LoA, % within ±2 cm (Wilson interval), and foul recall/precision.
- [x] Unit test `AccuracyStatsTest.kt` in `:core/src/test` covering hand-calculated fixtures, Wilson score, and foul metrics.
- [x] Create `ValidationService.kt` with SVG generators for Bland-Altman agreement plot and AI vs Tape scatter plot.
- [x] Implement `/validation` dashboard route in `DashboardPages.kt` with headline concordance row, stats summary, inline SVG plots, foul confusion table, mode comparison, worst 5 attempts, collapsed TUNE block, and CSV import/export.
- [x] Add tape measurement entry form per attempt in `renderSubmissionDetailPage`.
- [x] Implement `ValidationRoutes.kt` with `/validation`, `POST /validation/import`, `GET /validation/export.csv`, and `POST /validation/entry`.
- [x] Add server test **T-BJ-S6** in `Phase5BroadJumpServerTest.kt` verifying hand-calculated stats on a 5-row fixture.
- **Master Acceptance Condition:** Validation data ingestion, statistical calculations, SVG plots, and CSV import/export function accurately and tests pass green.

### Phase 5 & 6: Polish & Rehearsal
- **Scope:** Error handling, edge cases (no jump detected, extreme tilt warning), localized UI review, and final live demo rehearsals.

