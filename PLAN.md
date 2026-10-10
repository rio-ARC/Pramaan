# Implementation Plan: PRAMAAN Role-Based Workflow (Athlete ↔ Coach ↔ Verifier)

> **Context:** Upgrading the current single-flow prototype into a two-role mobile product (Athlete & Coach) with an Organiser dashboard and Verifier web portal.
> **Constraint Checklist & Confidence Score:**
> 1. Section 4 live demo flow is master acceptance test? Yes.
> 2. `:core` stays pure Kotlin with no android.* or system clock? Yes.
> 3. No DI framework, no extra modules, no background workers? Yes.
> 4. Add dependencies only via `gradle/libs.versions.toml` pinned to exact versions? Yes.
> 5. Do NOT write code yet, produce PLAN.md and wait for approval? Yes.
> Confidence Score: 5/5.

---

## 1. Mapping of Files & Classes Mentioned in Spec to Repo

| Spec Reference / Mention | Actual Path in Repository | Status | Description & Role |
|---|---|---|---|
| **Core Module (`:core`)** | | | |
| `VerticalJumpAnalyzer.kt` | [core/.../scoring/VerticalJumpAnalyzer.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/scoring/VerticalJumpAnalyzer.kt) | Existing | Flight time & displacement height analyzer |
| `SitUpAnalyzer.kt` | [core/.../scoring/SitUpAnalyzer.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/scoring/SitUpAnalyzer.kt) | Existing | Flexion angle rep analyzer |
| `ParabolaFit.kt` | [core/.../integrity/ParabolaFit.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/integrity/ParabolaFit.kt) | Existing | C2 gravity quadratic regression check |
| `IntegrityEngine.kt` | [core/.../integrity/IntegrityEngine.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/integrity/IntegrityEngine.kt) | Existing | Evaluates checks C1–C4 |
| `CanonicalJson.kt` | [core/.../manifest/CanonicalJson.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/manifest/CanonicalJson.kt) | Existing | RFC 8785 canonical JSON serializer |
| `DerRaw.kt` | [core/.../crypto/DerRaw.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/crypto/DerRaw.kt) | Existing | IEEE P1363 ↔ DER signature conversion |
| `EcdsaVerifier.kt` | [core/.../crypto/EcdsaVerifier.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/crypto/EcdsaVerifier.kt) | Existing | SHA256withECDSA verification on pure JVM |
| `Sha256.kt` | [core/.../crypto/Sha256.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/crypto/Sha256.kt) | Existing | SHA-256 digests |
| `SessionManifest.kt` | [core/.../manifest/SessionManifest.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/manifest/SessionManifest.kt) | Existing (Mod) | Manifest schema; athleteId bound to accountId |
| `WitnessPayloads.kt` | [core/.../manifest/WitnessPayloads.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/manifest/WitnessPayloads.kt) | Existing | Offline co-sign payload structures (fallback) |
| `Badge.kt` | [core/.../model/Badge.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/model/Badge.kt) | Existing (Mod) | Represents integrity badges (`VERIFIED`, `FLAGGED`, `TAMPERED`, `UNSEALED`) |
| `ReviewPayload.kt` | [core/.../manifest/ReviewPayload.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/manifest/ReviewPayload.kt) | **NEW** | Canonical JSON model for `PRAMAAN_COACH_REVIEW` |
| `SessionQr.kt` (codec) | [core/.../manifest/SessionQr.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/manifest/SessionQr.kt) | **NEW** | S2 QR string encoder/decoder & verification |
| **Server Module (`:server`)** | | | |
| `Db.kt` | [server/.../Db.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/Db.kt) | Existing (Mod) | Schema definition for Exposed SQLite tables |
| `Application.kt` | [server/.../Application.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/Application.kt) | Existing (Mod) | Ktor setup, Auth plugins (Bearer + Basic), DI container |
| `SessionVerifier.kt` | [server/.../SessionVerifier.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/SessionVerifier.kt) | Existing (Mod) | Verifies session hashes, checks, and device key |
| `ServerCertificateIssuer` | [server/.../Certificates.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/Certificates.kt) | Existing (Mod) | Issues ES256 JWS with coach + athlete claims |
| `DashboardPages.kt` | [server/.../DashboardPages.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/DashboardPages.kt) | Existing (Mod) | Organiser dashboard (`/`, `/submissions`, `/coaches`, `/athletes`) & `/v/{jws}` |
| `ApiRoutes.kt` | [server/.../ApiRoutes.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/ApiRoutes.kt) | Existing (Mod) | Main router delegating to specialized route modules |
| `AccountRepo.kt` | [server/.../AccountRepo.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/AccountRepo.kt) | **NEW** | Account data access & PIN hashing/validation |
| `TokenRepo.kt` | [server/.../TokenRepo.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/TokenRepo.kt) | **NEW** | Bearer token hashing, storage, lookup |
| `EventLog.kt` | [server/.../EventLog.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/EventLog.kt) | **NEW** | Append-only event store for chain of custody |
| `AuthRoutes.kt` | [server/.../AuthRoutes.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/AuthRoutes.kt) | **NEW** | Athlete register, coach login, `/api/me`, logout, photo |
| `AthleteRoutes.kt` | [server/.../AthleteRoutes.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/AthleteRoutes.kt) | **NEW** | Session submission, `/api/me/sessions`, polling |
| `CoachRoutes.kt` | [server/.../CoachRoutes.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/CoachRoutes.kt) | **NEW** | Pending queue, review detail, review submit |
| `AdminRoutes.kt` | [server/.../AdminRoutes.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/AdminRoutes.kt) | **NEW** | Organiser dashboard endpoints with Basic Auth |
| `ReviewService.kt` | [server/.../ReviewService.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/ReviewService.kt) | **NEW** | Business logic for coach approval, QR validation, certificate issuance |
| `seed/coaches.json` | `server/seed/coaches.json` | **NEW** | Default initial coaches seeded on server launch |
| **Android App Module (`:app`)** | | | |
| `DeviceKey.kt` | [app/.../seal/DeviceKey.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/seal/DeviceKey.kt) | Existing | Non-exportable Keystore EC P-256 key |
| `Sealer.kt` | [app/.../seal/Sealer.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/seal/Sealer.kt) | Existing (Mod) | Seals session with logged-in athlete's ID & profile |
| `TamperSimulator.kt` | [app/.../seal/TamperSimulator.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/seal/TamperSimulator.kt) | Existing (Mod) | Flips byte in MP4; moved to Demo Tools |
| `SessionEntity.kt` | [app/.../data/SessionEntity.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/data/SessionEntity.kt) | Existing (Mod) | Room table updated with two-axis status |
| `AthleteEntity.kt` | [app/.../data/AthleteEntity.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/data/AthleteEntity.kt) | Existing (Dep) | Replaced by single logged-in profile in DataStore |
| `PramaanDb.kt` | [app/.../data/PramaanDb.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/data/PramaanDb.kt) | Existing (Mod) | Room database (v2 with destructive migration) |
| `AppContainer.kt` | [app/.../AppContainer.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/AppContainer.kt) | Existing (Mod) | Manual DI container providing new repositories |
| `MainActivity.kt` | [app/.../MainActivity.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/MainActivity.kt) | Existing (Mod) | Resolves starting route from `AuthStore` before render |
| `Nav.kt` | [app/.../ui/Nav.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/Nav.kt) | Existing (Mod) | Replaced/refactored into role-based NavHosts |
| `HomeScreen.kt` | [app/.../ui/screens/HomeScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/HomeScreen.kt) | Existing (Dep) | Replaced by `AthleteHomeScreen` & `CoachHomeScreen` |
| `TestSetupScreen.kt` | [app/.../ui/screens/TestSetupScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/TestSetupScreen.kt) | Existing (Mod) | Athlete picker removed; uses active profile |
| `RecordScreen.kt` | [app/.../ui/screens/RecordScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/RecordScreen.kt) | Existing | Camera recording & live HUD (kept as-is) |
| `ResultScreen.kt` | [app/.../ui/screens/ResultScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/ResultScreen.kt) | Existing (Mod) | Tamper/Witness buttons replaced by "Submit" |
| `WitnessScreen.kt` | [app/.../ui/screens/WitnessScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/WitnessScreen.kt) | Existing | Reused by CoachScanScreen and offline fallback |
| `CoSignScreen.kt` | [app/.../ui/screens/CoSignScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/CoSignScreen.kt) | Existing | Retained for offline fallback |
| `SyncScreen.kt` | [app/.../ui/screens/SyncScreen.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/ui/screens/SyncScreen.kt) | Existing (Dep) | Replaced by ConnectScreen & background submit |
| `AuthStore.kt` | `app/.../auth/AuthStore.kt` | **NEW** | DataStore holding `serverUrl`, `role`, `accountId`, `token`, `profileJson` |
| `AuthRepository.kt` | `app/.../auth/AuthRepository.kt` | **NEW** | Registration & login API interactions |
| `Role.kt` | `app/.../auth/Role.kt` | **NEW** | Enum `ATHLETE`, `COACH` |
| `ApiClient.kt` | `app/.../api/ApiClient.kt` | **NEW** | Ktor HTTP client configured with Bearer header & dynamic base URL |
| `AuthDtos.kt`, `SessionDtos.kt`, `ReviewDtos.kt` | `app/.../api/dto/*.kt` | **NEW** | Serialization models for API communication |
| `SubmissionRepository.kt` | `app/.../submissions/SubmissionRepository.kt` | **NEW** | Session upload, polling status, submissions list |
| `ReviewRepository.kt` | `app/.../review/ReviewRepository.kt` | **NEW** | Coach queue fetch, review detail, submit review |
| `ReviewSigner.kt` | `app/.../review/ReviewSigner.kt` | **NEW** | Canonical payload generator & hardware Keystore signer |
| `RootNavHost.kt`, `*Graph.kt` | `app/.../ui/nav/*.kt` | **NEW** | Onboarding, Athlete, and Coach nested navigation graphs |
| `ConnectScreen.kt`, `WelcomeScreen.kt`, `AthleteRegisterScreen.kt`, `CoachLoginScreen.kt` | `app/.../ui/onboarding/*.kt` | **NEW** | First-run and authentication screens |
| `AthleteHomeScreen.kt`, `SubmissionsScreen.kt`, `SubmissionDetailScreen.kt`, `ShowQrScreen.kt`, `CertificateScreen.kt`, `ProfileScreen.kt`, `DemoToolsScreen.kt` | `app/.../ui/athlete/*.kt` | **NEW** | Athlete screens |
| `CoachHomeScreen.kt`, `CoachScanScreen.kt`, `ReviewScreen.kt`, `DecisionDoneScreen.kt`, `CoachHistoryScreen.kt` | `app/.../ui/coach/*.kt` | **NEW** | Coach screens |
| `StatusChip.kt`, `IntegrityChip.kt`, `AthleteCard.kt` | `app/.../ui/components/*.kt` | **NEW** | UI tokens & role-aware badges |

---

## 2. Conflicts Between Spec & Existing Code, with Proposed Resolutions

### Conflict 1: `AGENTS.md` Rule #5 ("no auth") vs Spec §12.1
- **Conflict:** `AGENTS.md` currently forbids user auth: *"Keep it simple: no DI framework, no extra modules, no background workers, no auth."* The spec explicitly introduces two auth paths.
- **Proposed Resolution:** In accordance with Spec §12.1, update `AGENTS.md` Rule 5:
  > *"Keep it simple: no DI framework, no extra modules, no background workers. Exactly two auth paths exist: athlete self-registration and coach Coach-ID+PIN sign-in, both binding the device Keystore key. No passwords for athletes, no OAuth, no email/OTP, no third-party auth. Certificate is issued only when server integrity is VERIFIED and a bound coach key has signed APPROVED. All signed payloads (manifest, S2 QR, review payload) are built by shared `:core` code."*

### Conflict 2: Badge Model vs The Two-Axis Status (`integrity` + `status`)
- **Conflict:** Existing code uses a single `Badge` enum (`WITNESSED`, `VERIFIED`, `UNSEALED`, `FLAGGED`, `TAMPERED`) for both local integrity and server verification. In the new spec (§2.2):
  1. `integrity` is `VERIFIED`, `FLAGGED`, `TAMPERED`, `UNSEALED`. (`WITNESSED` is removed from this axis).
  2. `status` is `DRAFT`, `AWAITING_REVIEW`, `APPROVED`, `REJECTED`, `AUTO_REJECTED`.
- **Proposed Resolution:**
  - Keep `Badge` in `:core` containing `VERIFIED`, `FLAGGED`, `TAMPERED`, `UNSEALED` (and retain `WITNESSED` only for legacy/offline fallback).
  - Add `enum class SessionStatus { DRAFT, AWAITING_REVIEW, APPROVED, REJECTED, AUTO_REJECTED }` in `:core`.
  - In server `sessions` table and Room `sessions` table, store both fields independently.
  - The certificate is only issued when `integrity == VERIFIED && status == APPROVED`.

### Conflict 3: Certificate Issuance Timing & Public Verification Page
- **Conflict:** Currently, `POST /api/sessions` immediately generates and signs a certificate (`certJws`) upon upload if hashes pass. The spec dictates that certificates are **never** issued at upload; they are only issued when a coach executes an approved review.
- **Proposed Resolution:**
  - In `POST /api/sessions`, remove automatic certificate creation. Set `status = AWAITING_REVIEW` (or `AUTO_REJECTED` if `TAMPERED`).
  - Move certificate signing to `POST /api/coach/sessions/{id}/review` when `decision == APPROVED`.
  - Upgrade `/v/{jws}` to render the full chain of custody (Athlete phone seal, server checks pass, Coach approval in-person/remote, certificate issuance timestamp).

### Conflict 4: Certificate Claims Schema
- **Conflict:** Existing `ServerCertificateIssuer.kt` signs claims: `iss: "pramaan.authority"`, `sub: athleteId`, `sid`, `ini`, `tst`, `val`, `unt`, `bdg`, `cpk`, `wit`, `iat`.
  Spec §6.3 specifies:
  - `iss: "PRAMAAN_CENTRAL_AUTHORITY"`
  - `jti: "cert_..."`
  - `iat`, `sid`, `sha256`
  - `athlete: { id, name, age, sex }` (name formatted as First Name + Last Initial)
  - `test`, `score`, `unit`, `integrity`
  - `review: { by, name, method, at }`
  - `recordedAt`
- **Proposed Resolution:** Update `ServerCertificateIssuer.issueCertificate()` to accept athlete details, coach details, review method, and review timestamp, encoding the exact JSON claims structure from §6.3.

### Conflict 5: Missing Dependencies in `gradle/libs.versions.toml`
- **Conflict:** The server requires Ktor Server Auth for `bearer` and `basic` authentication (`io.ktor:ktor-server-auth`), but it is not declared in `libs.versions.toml`. The app will also require `androidx.datastore:datastore-preferences` and `androidx.media3:media3-exoplayer`.
- **Proposed Resolution:** Under Rule 4 ("Add dependencies only via gradle/libs.versions.toml, pinned to exact versions"):
  - For Phase 1 (server): Add `ktor-server-auth = { group = "io.ktor", name = "ktor-server-auth", version.ref = "ktor" }` to `libs.versions.toml` (version pinned to `3.2.0`).
  - For Phase 2 (app): Add DataStore and Media3 entries pinned to exact versions.

### Conflict 6: Athlete Identification & Multiple Athletes per Device
- **Conflict:** Existing app allows adding/selecting multiple athletes in Room (`AthleteEntity`, `AthletesScreen`, `AthleteEditScreen`). The spec states: *"Your phone is your login... One account per phone... The local athletes table and the athlete picker are no longer used."*
- **Proposed Resolution:**
  - Deprecate local `athletes` table in Room and athlete picker dropdowns.
  - Store single authenticated athlete profile in `AuthStore` (DataStore).
  - Manifest sealing (`Sealer.kt`) reads athlete ID and height directly from `AuthStore`.

---

## 3. Exact Changes to Manifest, Room Entities, and Server Tables

### A. Manifest Changes (`SessionManifest.kt` & `Sealer.kt`)

In [SessionManifest.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/core/src/main/kotlin/app/pramaan/core/manifest/SessionManifest.kt):
```kotlin
@Serializable
data class AthleteManifestInfo(
    val id: String,          // Matches accountId (e.g., "ATH-7K3Q")
    val initials: String,    // e.g., "MK"
    val age: Int,            // Computed from DOB
    val sex: String,         // "F" | "M" | "X"
    val heightCm: Double     // From registered athlete profile
)

@Serializable
data class SessionManifest(
    val v: Int = 1,
    val sessionId: String,
    val test: TestType,
    val source: String,
    val athlete: AthleteManifestInfo,
    val devicePublicKey: String, // Public key from Android Keystore DeviceKey
    val capturedAt: String,
    val fps: Int,
    val hashes: FileHashes,
    val result: SessionResult,
    val checks: List<CheckResult>,
    val badge: Badge             // Local integrity: VERIFIED, FLAGGED, TAMPERED, UNSEALED
)
```
*Note on Sealer behavior:* When `Sealer.sealSession()` executes:
- `athlete.id` is populated with `AuthStore.accountId`.
- `devicePublicKey` is bound to the account during registration. Server verifies `manifest.athlete.id == token.accountId` AND `manifest.devicePublicKey == account.device_pubkey`.

---

### B. Room Entity Changes (`SessionEntity.kt` & `PramaanDb.kt`)

In [SessionEntity.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/data/SessionEntity.kt):
```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val ownerAccountId: String,            // Replaces athleteId picker; bound to AuthStore.accountId
    val test: String,
    val source: String,
    val folder: String,
    val best: Double,
    val unit: String,
    val localIntegrity: String,            // 'VERIFIED' | 'FLAGGED' | 'TAMPERED' | 'UNSEALED'
    val status: String,                    // 'DRAFT' | 'AWAITING_REVIEW' | 'APPROVED' | 'REJECTED' | 'AUTO_REJECTED'
    val serverIntegrity: String? = null,   // Populated after upload
    val reviewedByName: String? = null,    // Populated after coach review
    val reviewMethod: String? = null,      // 'IN_PERSON_QR' | 'REMOTE' | 'OFFLINE_QR'
    val reviewReason: String? = null,      // Reason if rejected
    val certificateJws: String? = null,    // Final ES256 JWS token
    val createdAt: Long = System.currentTimeMillis()
)
```

In [PramaanDb.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/app/src/main/kotlin/app/pramaan/data/PramaanDb.kt):
- Remove `AthleteEntity::class` from `@Database`.
- Version bump: `version = 2`.
- Apply `fallbackToDestructiveMigration()`.

---

### C. Server SQLite Tables (Exposed in `server/src/main/kotlin/app/pramaan/server/Db.kt`)

```kotlin
// 1. Accounts Table (Athletes & Coaches)
object AccountsTable : Table("accounts") {
    val id = varchar("id", 64)                         // PK: 'ATH-7K3Q' or 'COA-01'
    val role = varchar("role", 16)                      // 'ATHLETE' | 'COACH'
    val name = varchar("name", 128)
    val dob = varchar("dob", 32).nullable()             // 'YYYY-MM-DD'
    val sex = varchar("sex", 8).nullable()              // 'F' | 'M' | 'X'
    val heightCm = double("height_cm").nullable()
    val weightKg = double("weight_kg").nullable()
    val district = varchar("district", 64).nullable()
    val phone = varchar("phone", 32).nullable()
    val guardianConsent = bool("guardian_consent").default(false)
    val photoPath = varchar("photo_path", 256).nullable()
    val pinHash = varchar("pin_hash", 128).nullable()   // SHA-256(salt + pin) for coaches
    val pinSalt = varchar("pin_salt", 64).nullable()
    val devicePubkey = text("device_pubkey").nullable() // Base64url SubjectPublicKeyInfo
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// 2. Tokens Table (API Bearer Tokens)
object TokensTable : Table("tokens") {
    val tokenHash = varchar("token_hash", 128)          // PK: SHA-256(token)
    val accountId = varchar("account_id", 64).references(AccountsTable.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(tokenHash)
}

// 3. Sessions Table (Existing updated with audit & review columns)
object SessionsTable : Table("sessions") {
    val id = varchar("id", 64)                          // PK: 'ses_...'
    val athleteId = varchar("athlete_id", 64).references(AccountsTable.id).nullable()
    val test = varchar("test", 64)
    val best = double("best")
    val unit = varchar("unit", 16)
    val status = varchar("status", 32).default("AWAITING_REVIEW") // 'DRAFT', 'AWAITING_REVIEW', 'APPROVED', 'REJECTED', 'AUTO_REJECTED'
    val integrity = varchar("integrity", 32).nullable() // 'VERIFIED', 'FLAGGED', 'TAMPERED', 'UNSEALED'
    val integrityJson = text("integrity_json").nullable()
    val manifestSha256 = varchar("manifest_sha256", 128).nullable()
    val submittedAt = long("submitted_at").nullable()
    val reviewedBy = varchar("reviewed_by", 64).references(AccountsTable.id).nullable()
    val reviewedAt = long("reviewed_at").nullable()
    val reviewMethod = varchar("review_method", 32).nullable() // 'IN_PERSON_QR', 'REMOTE', 'OFFLINE_QR'
    val reviewReason = text("review_reason").nullable()
    val coachSig = text("coach_sig").nullable()
    val certificateJws = text("certificate_jws").nullable()
    val capturedAt = varchar("captured_at", 64).nullable()
    val rawManifest = text("raw_manifest").nullable()

    override val primaryKey = PrimaryKey(id)
}

// 4. Session Events Table (Append-only timeline / Chain of Custody)
object SessionEventsTable : Table("session_events") {
    val id = integer("id").autoIncrement()
    val sessionId = varchar("session_id", 64)
    val type = varchar("type", 32)                      // RECORDED, SUBMITTED, SERVER_VERIFIED, QR_SCANNED, APPROVED, REJECTED, CERT_ISSUED
    val actorId = varchar("actor_id", 64).nullable()    // 'ATH-...', 'COA-...', 'SERVER'
    val at = long("at")
    val detail = text("detail").nullable()              // JSON payload summary

    override val primaryKey = PrimaryKey(id)
}
```

---

## 4. Task List for Phase 1 (Spec §10, Day 1 — Server Only)

### Task 1.1: Dependency and Project Configuration
- [ ] Add `ktor-server-auth` to [gradle/libs.versions.toml](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/gradle/libs.versions.toml) pinned to exact version `3.2.0`.
- [ ] Add `implementation(libs.ktor.server.auth)` to [server/build.gradle.kts](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/build.gradle.kts).
- [ ] Update [AGENTS.md](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/AGENTS.md) Rule #5 as specified in Spec §12.1.

### Task 1.2: Database Models & Repositories
- [ ] Create `server/seed/coaches.json` with initial coaches (`COA-01: Rakesh Kumar`, `COA-02: Sunita Devi`).
- [ ] Update [server/src/main/kotlin/app/pramaan/server/Db.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/Db.kt) with `AccountsTable`, `TokensTable`, `SessionsTable`, `SessionEventsTable`.
- [ ] Implement `AccountRepo.kt`:
  - `createAthlete(profile, photoFile)` with Crockford base32 ID generation (`ATH-XXXX`).
  - `authenticateCoach(coachId, pin, devicePublicKey)` (with SHA-256 + salt verification and key binding).
  - `seedCoachesIfEmpty(seedFile)`.
  - `getAccount(id)` and `getAllAthletes()`.
- [ ] Implement `TokenRepo.kt`:
  - Generate 32-byte secure random base64url token, store `SHA-256(token)`.
  - `findAccountByTokenHash(tokenHash): Account?`.
  - `revokeToken(token)`.
- [ ] Implement `EventLog.kt`:
  - `logEvent(sessionId, type, actorId, detailJson)`.
  - `getEventsForSession(sessionId)`.

### Task 1.3: Server Authentication & Security Middleware
- [ ] In [Application.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/Application.kt):
  - Configure `Authentication` with `bearer("api")` validating tokens via `TokenRepo`.
  - Configure `Authentication` with `basic("admin")` validating `ADMIN_PASSWORD` (default: `pramaan`).
  - Add helper function `ApplicationCall.requireRole(role: String): AccountPrincipal`.

### Task 1.4: Auth & Account Endpoints
- [ ] Implement `AuthRoutes.kt`:
  - `POST /api/auth/athlete/register` (multipart: profile JSON + photo file).
  - `POST /api/auth/coach/login` (`{ coachId, pin, devicePublicKey }`).
  - `GET /api/me` (returns current caller profile + role).
  - `POST /api/auth/logout` (revokes bearer token).
  - `GET /api/accounts/{id}/photo` (serves JPEG profile picture).

### Task 1.5: Updated Session Upload & Verification
- [ ] Update [SessionVerifier.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/SessionVerifier.kt) to compute `manifestSha256` and extract individual check results.
- [ ] Modify `POST /api/sessions` in [ApiRoutes.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/ApiRoutes.kt):
  - Enforce Bearer authentication (`Role.ATHLETE`).
  - Validate: `manifest.athlete.id == token.accountId` AND `manifest.devicePublicKey == account.device_pubkey`. If mismatch, respond `403 WRONG_DEVICE`.
  - Re-verify files and C1–C4 physics checks.
  - Determine `integrity` (`VERIFIED`, `FLAGGED`, `TAMPERED`, `UNSEALED`).
  - Determine `status`: `AUTO_REJECTED` if `TAMPERED`, otherwise `AWAITING_REVIEW`.
  - Record events: `SUBMITTED` and `SERVER_VERIFIED` in `session_events`.
  - Return `{ sessionId, status, integrity, checks }` (Do **not** issue certificate yet).
- [ ] Implement `GET /api/me/sessions` and `GET /api/me/sessions/{id}` for athlete status polling.

### Task 1.6: Organiser Dashboard & Admin Routes
- [ ] Protect `/` with Basic Auth (`ADMIN_PASSWORD`).
- [ ] Implement `AdminRoutes.kt`:
  - `GET /athletes` (list registered athletes with thumbnail photo & stats).
  - `GET /coaches` and `POST /coaches` (list coaches & add new coach form with PIN).
  - `GET /submissions` and `GET /submissions/{id}` (view submissions with timeline).
- [ ] Update [DashboardPages.kt](file:///c:/Users/Pankaj%20Kumar%20Gupta/AndroidStudioProjects/Pramaan/server/src/main/kotlin/app/pramaan/server/DashboardPages.kt) counters and navigation.

### Task 1.7: Automated Server Acceptance Tests
- [ ] Create `server/src/test/kotlin/app/pramaan/server/Phase1ServerTest.kt`:
  - Test athlete registration & bearer token issuance.
  - Test coach login with valid & invalid PIN, and verify key binding.
  - Test session upload with matching device key -> sets `AWAITING_REVIEW` & `VERIFIED`.
  - Test session upload with mismatched device key / account -> returns `403 WRONG_DEVICE`.
  - Test tampered session upload -> sets `AUTO_REJECTED` & `TAMPERED`.
  - Verify `GET /api/me/sessions` returns the uploaded session.

---

## Master Acceptance Flow (Phase 1 Exit Criteria)
Phase 1 is complete when all server tests pass and curl commands verify:
1. Athlete registration returns `ATH-XXXX` Crockford ID and valid bearer token.
2. Coach login succeeds with seeded `COA-01` and binds the device public key.
3. Uploading a session sealed with an mismatched key is rejected with `403 WRONG_DEVICE`.
4. Uploading a valid session creates an `AWAITING_REVIEW` record without issuing a premature certificate.
