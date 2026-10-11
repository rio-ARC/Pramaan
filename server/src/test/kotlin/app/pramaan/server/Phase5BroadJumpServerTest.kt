package app.pramaan.server

import app.pramaan.core.SyntheticAttemptSpec
import app.pramaan.core.SyntheticBroadJump
import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.crypto.Sha256
import app.pramaan.core.manifest.AthleteManifestInfo
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.FileHashes
import app.pramaan.core.manifest.GyroJsonl
import app.pramaan.core.manifest.PoseFrameJsonl
import app.pramaan.core.manifest.ReviewPayload
import app.pramaan.core.manifest.SessionManifest
import app.pramaan.core.manifest.SessionQr
import app.pramaan.core.manifest.SessionResult
import app.pramaan.core.model.Badge
import app.pramaan.core.model.CheckResult
import app.pramaan.core.model.GyroSample
import app.pramaan.core.model.TestType
import app.pramaan.core.scoring.broadjump.AthleteInfo
import app.pramaan.core.scoring.broadjump.BroadJumpAnalyzer
import app.pramaan.core.scoring.broadjump.BroadJumpParams
import app.pramaan.core.scoring.broadjump.CalibMode
import app.pramaan.core.scoring.broadjump.GroundCalibration
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import app.pramaan.server.DashboardPages.renderValidationPage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.math.abs

class Phase5BroadJumpServerTest {

    @BeforeEach
    fun setupDb(@TempDir tempDir: File) {
        val testDbFile = File(tempDir, "test_pramaan_sbj.db")
        ServerDb.init(testDbFile)
    }

    private fun generateEcKeyPair(): Pair<String, KeyPair> {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val pair = kpg.generateKeyPair()
        val pubKeyB64 = Base64Url.encode(pair.public.encoded)
        return pubKeyB64 to pair
    }

    private fun signMessage(pair: KeyPair, message: ByteArray): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(pair.private)
        sig.update(message)
        return Base64Url.encode(sig.sign())
    }

    private fun createSyntheticSbjSession(
        sessionDir: File,
        athleteId: String,
        devicePubKey: String,
        deviceKeyPair: KeyPair,
        heightCm: Double = 160.0,
        distanceCm: Double = 200.0,
        calibMode: CalibMode = CalibMode.MARKER,
        analyzerVersion: String = "bj-1.0.0",
        manifestAthleteHeight: Double = heightCm,
        tamperCalibTap: Boolean = false,
        declaredBestCm: Double? = null
    ): Pair<SessionManifest, File> {
        val (track, calib) = SyntheticBroadJump.generate(
            heightCm = heightCm,
            fps = 30,
            attempts = listOf(SyntheticAttemptSpec(distanceCm = distanceCm, flightTimeS = 0.58))
        )

        val videoFile = File(sessionDir, "video.mp4").apply { writeBytes(ByteArray(1024) { 0x01 }) }
        val framesFile = File(sessionDir, "frames.jsonl").apply {
            writer().use { PoseFrameJsonl.writeAll(it, track) }
        }
        val gyroFile = File(sessionDir, "gyro.jsonl").apply {
            val samples = listOf(
                GyroSample(t = 100L, x = 0.001, y = 0.001, z = 0.001),
                GyroSample(t = 200L, x = 0.001, y = 0.001, z = 0.001)
            )
            writer().use { GyroJsonl.writeAll(it, samples) }
        }

        val calibFrameFile = File(sessionDir, "calib_frame.jpg").apply { writeBytes(ByteArray(512) { 0x02 }) }
        val stillsDir = File(sessionDir, "stills").apply { mkdirs() }
        val stillFile = File(stillsDir, "att1_landing.jpg").apply { writeBytes(ByteArray(512) { 0x03 }) }

        val usedCalib = if (calibMode == CalibMode.HEIGHT_ONLY) {
            calib.copy(mode = CalibMode.HEIGHT_ONLY, marker = null, markerCm = null)
        } else calib

        val athleteInfo = AthleteInfo(heightCm = heightCm, ageYears = 16, sex = "F")
        val phoneResult = BroadJumpAnalyzer.analyze(track, usedCalib, athleteInfo, BroadJumpParams(analyzerVersion = analyzerVersion))

        val videoHash = Sha256.hash(videoFile.readBytes())
        val framesHash = Sha256.hash(framesFile.readBytes())
        val gyroHash = Sha256.hash(gyroFile.readBytes())
        val calibHash = Sha256.hash(calibFrameFile.readBytes())
        val stillsMap = mapOf("att1_landing" to Sha256.hash(stillFile.readBytes()))

        val bestScore = declaredBestCm ?: (phoneResult.best ?: 0.0)

        val manifest = SessionManifest(
            v = 1,
            sessionId = sessionDir.name,
            test = TestType.BROAD_JUMP,
            analyzerVersion = analyzerVersion,
            source = "CAMERA",
            calibration = usedCalib,
            athlete = AthleteManifestInfo(
                id = athleteId,
                initials = "MK",
                age = 16,
                sex = "F",
                heightCm = manifestAthleteHeight
            ),
            devicePublicKey = devicePubKey,
            capturedAt = "2026-10-11T12:00:00Z",
            fps = 30,
            hashes = FileHashes(
                video = videoHash,
                frames = framesHash,
                gyro = gyroHash,
                calibFrame = calibHash,
                stills = stillsMap
            ),
            result = SessionResult(
                unit = "cm",
                best = bestScore,
                broadJumpAttempts = phoneResult.attempts
            ),
            checks = listOf(
                CheckResult(id = "C1", pass = true, value = "Intact", limit = "Matches"),
                CheckResult(id = "C2", pass = true, value = "9.81 m/s²", limit = "7.5..12.0"),
                CheckResult(id = "C3", pass = true, value = "Consistent", limit = "Limits"),
                CheckResult(id = "C4", pass = true, value = "Steady", limit = "Limits"),
                CheckResult(id = "C5", pass = true, value = "1.2%", limit = "≤ 10%")
            ),
            badge = Badge.VERIFIED
        )

        val manifestBytes = CanonicalJson.encodeToByteArray(manifest)
        val manifestFile = File(sessionDir, "manifest.json").apply { writeBytes(manifestBytes) }

        val sigB64 = signMessage(deviceKeyPair, manifestBytes)
        File(sessionDir, "manifest.sig").writeText(sigB64)

        if (tamperCalibTap) {
            // Tamper calibration tap inside manifest after signature
            val tamperedManifest = manifest.copy(
                calibration = manifest.calibration?.copy(markerCm = 300.0)
            )
            manifestFile.writeBytes(CanonicalJson.encodeToByteArray(tamperedManifest))
        }

        return manifest to sessionDir
    }

    @Test
    fun `T-BJ-S1 Upload genuine signed SBJ session rescores to VERIFIED and passes coach review`(@TempDir tempDir: File) {
        val (athPub, athKeyPair) = generateEcKeyPair()
        val (coachPub, coachKeyPair) = generateEcKeyPair()

        val athlete = AccountRepo.createAthlete(
            AthleteProfileInput(
                name = "Manpreet Kaur",
                dob = "2008-01-01",
                sex = "F",
                heightCm = 160.0,
                guardianConsent = true,
                devicePublicKey = athPub
            ),
            photoBytes = null,
            photosDir = File(tempDir, "photos")
        )

        val seedFile = File(tempDir, "coaches.json").apply {
            writeText("""[{"id": "COA-01", "name": "Rakesh Kumar", "district": "Ranchi", "pin": "1234"}]""")
        }
        AccountRepo.seedCoachesIfEmpty(seedFile)
        val coach = AccountRepo.authenticateCoach("COA-01", "1234", coachPub)!!

        val sessionDir = File(tempDir, "ses_bj_genuine").apply { mkdirs() }
        val (manifest, _) = createSyntheticSbjSession(
            sessionDir = sessionDir,
            athleteId = athlete.id,
            devicePubKey = athPub,
            deviceKeyPair = athKeyPair,
            heightCm = 160.0,
            distanceCm = 200.0
        )

        // 1. Session verification & server rescore
        val verifyResult = SessionVerifier.verifyUploadedSession(sessionDir, account = athlete)
        verifyResult.outcome.badge shouldBe Badge.VERIFIED

        // Server rescore distance is within 0.5 cm of manifest best
        val serverBest = verifyResult.serverBroadJumpResult?.best
        serverBest shouldNotBe null
        abs(serverBest!! - manifest.result.best) shouldBeLessThan 0.5

        // Save session in db
        val now = System.currentTimeMillis()
        ServerDb.saveOrUpdate(
            ServerSessionRecord(
                id = manifest.sessionId,
                athleteId = athlete.id,
                athleteInitials = "MK",
                test = "BROAD_JUMP",
                best = manifest.result.best,
                unit = "cm",
                status = "AWAITING_REVIEW",
                integrity = "VERIFIED",
                manifestSha256 = verifyResult.manifestSha256,
                rawManifest = CanonicalJson.encodeToString(manifest),
                submittedAt = now
            )
        )

        // 2. Athlete S2 QR generation
        val nonce = "sbjNonce123"
        val s2MsgBytes = SessionQr.buildSignedMessageBytes(manifest.sessionId, verifyResult.manifestSha256, nonce, now)
        val athSig = signMessage(athKeyPair, s2MsgBytes)
        val s2Qr = SessionQr.encode(manifest.sessionId, verifyResult.manifestSha256, nonce, now, athSig)

        // 3. Coach review & certificate issuance
        val qrSha256 = Sha256.digest(s2Qr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val reviewPayload = ReviewPayload(
            coachId = coach.id,
            decision = "APPROVED",
            manifestSha256 = verifyResult.manifestSha256,
            method = "IN_PERSON_QR",
            qrSha256 = qrSha256,
            reviewedAt = now + 1000,
            sessionId = manifest.sessionId
        )
        val coachSig = signMessage(coachKeyPair, reviewPayload.canonicalBytes())

        val certIssuer = ServerCertificateIssuer(File(tempDir, "server_key.json"))
        val reviewService = ReviewService(AccountRepo, EventLog, certIssuer, { "http://localhost:8080" })

        val outcome = reviewService.reviewSession(
            sessionId = manifest.sessionId,
            coachId = coach.id,
            decision = "APPROVED",
            requestedMethod = "IN_PERSON_QR",
            reason = null,
            scannedQr = s2Qr,
            reviewedAt = now + 1000,
            coachSignature = coachSig
        )

        outcome.shouldBeInstanceOf<ReviewResult.Success>()
        outcome.status shouldBe "APPROVED"
        outcome.integrity shouldBe "VERIFIED"
        outcome.certificateJws shouldNotBe null

        // Verify claims contain cal = MARKER
        val claims = certIssuer.verifyCertificate(outcome.certificateJws!!)
        claims shouldNotBe null
        claims!!.getClaim("test") shouldBe "BROAD_JUMP"
        (claims.getClaim("cal") ?: claims.getClaim("calibration")) shouldBe "MARKER"
    }

    @Test
    fun `T-BJ-S2 Upload with HEIGHT_ONLY calibration flags CALIB_APPROX and blocks coach approval`(@TempDir tempDir: File) {
        val (athPub, athKeyPair) = generateEcKeyPair()
        val (coachPub, coachKeyPair) = generateEcKeyPair()

        val athlete = AccountRepo.createAthlete(
            AthleteProfileInput(
                name = "Harpreet Singh",
                dob = "2007-05-10",
                sex = "M",
                heightCm = 175.0,
                guardianConsent = true,
                devicePublicKey = athPub
            ),
            photoBytes = null,
            photosDir = File(tempDir, "photos")
        )

        val seedFile = File(tempDir, "coaches.json").apply {
            writeText("""[{"id": "COA-01", "name": "Rakesh Kumar", "district": "Ranchi", "pin": "1234"}]""")
        }
        AccountRepo.seedCoachesIfEmpty(seedFile)
        val coach = AccountRepo.authenticateCoach("COA-01", "1234", coachPub)!!

        val sessionDir = File(tempDir, "ses_bj_height_only").apply { mkdirs() }
        val (manifest, _) = createSyntheticSbjSession(
            sessionDir = sessionDir,
            athleteId = athlete.id,
            devicePubKey = athPub,
            deviceKeyPair = athKeyPair,
            heightCm = 175.0,
            calibMode = CalibMode.HEIGHT_ONLY
        )

        val verifyResult = SessionVerifier.verifyUploadedSession(sessionDir, account = athlete)
        verifyResult.outcome.badge shouldBe Badge.FLAGGED

        val calibCheck = verifyResult.outcome.checks.find { it.reason == "CALIB_APPROX" }
        calibCheck shouldNotBe null
        calibCheck!!.pass shouldBe false

        // In database, status AWAITING_REVIEW, integrity FLAGGED
        val now = System.currentTimeMillis()
        ServerDb.saveOrUpdate(
            ServerSessionRecord(
                id = manifest.sessionId,
                athleteId = athlete.id,
                athleteInitials = "HS",
                test = "BROAD_JUMP",
                best = manifest.result.best,
                unit = "cm",
                status = "AWAITING_REVIEW",
                integrity = "FLAGGED",
                manifestSha256 = verifyResult.manifestSha256,
                rawManifest = CanonicalJson.encodeToString(manifest),
                submittedAt = now
            )
        )

        // Coach attempts approval on FLAGGED session -> returns 422 NOT_APPROVABLE
        val certIssuer = ServerCertificateIssuer(File(tempDir, "server_key.json"))
        val reviewService = ReviewService(AccountRepo, EventLog, certIssuer, { "http://localhost:8080" })

        val reviewPayload = ReviewPayload(
            coachId = coach.id,
            decision = "APPROVED",
            manifestSha256 = verifyResult.manifestSha256,
            method = "REMOTE",
            qrSha256 = "",
            reviewedAt = now + 1000,
            sessionId = manifest.sessionId
        )
        val coachSig = signMessage(coachKeyPair, reviewPayload.canonicalBytes())

        val outcome = reviewService.reviewSession(
            sessionId = manifest.sessionId,
            coachId = coach.id,
            decision = "APPROVED",
            requestedMethod = "REMOTE",
            reason = null,
            scannedQr = null,
            reviewedAt = now + 1000,
            coachSignature = coachSig
        )

        outcome.shouldBeInstanceOf<ReviewResult.Error>()
        outcome.statusCode shouldBe 422
        outcome.error shouldBe "NOT_APPROVABLE"
    }

    @Test
    fun `T-BJ-S3 Upload with altered calibration taps after signing fails C1 to TAMPERED`(@TempDir tempDir: File) {
        val (athPub, athKeyPair) = generateEcKeyPair()

        val athlete = AccountRepo.createAthlete(
            AthleteProfileInput(
                name = "Amit Roy",
                dob = "2008-04-12",
                sex = "M",
                heightCm = 165.0,
                guardianConsent = true,
                devicePublicKey = athPub
            ),
            photoBytes = null,
            photosDir = File(tempDir, "photos")
        )

        val sessionDir = File(tempDir, "ses_bj_tampered").apply { mkdirs() }
        createSyntheticSbjSession(
            sessionDir = sessionDir,
            athleteId = athlete.id,
            devicePubKey = athPub,
            deviceKeyPair = athKeyPair,
            heightCm = 165.0,
            tamperCalibTap = true
        )

        val verifyResult = SessionVerifier.verifyUploadedSession(sessionDir, account = athlete)
        verifyResult.outcome.badge shouldBe Badge.TAMPERED

        val c1 = verifyResult.outcome.checks.find { it.id == "C1" }
        c1 shouldNotBe null
        c1!!.pass shouldBe false
    }

    @Test
    fun `T-BJ-S4 Manifest height differs from account height flags HEIGHT_MISMATCH`(@TempDir tempDir: File) {
        val (athPub, athKeyPair) = generateEcKeyPair()

        val athlete = AccountRepo.createAthlete(
            AthleteProfileInput(
                name = "Pooja Sharma",
                dob = "2009-08-20",
                sex = "F",
                heightCm = 155.0, // Account height: 155.0 cm
                guardianConsent = true,
                devicePublicKey = athPub
            ),
            photoBytes = null,
            photosDir = File(tempDir, "photos")
        )

        val sessionDir = File(tempDir, "ses_bj_height_mismatch").apply { mkdirs() }
        // Manifest declares 165.0 cm (differs by 10 cm > 0.5 cm)
        createSyntheticSbjSession(
            sessionDir = sessionDir,
            athleteId = athlete.id,
            devicePubKey = athPub,
            deviceKeyPair = athKeyPair,
            heightCm = 155.0,
            manifestAthleteHeight = 165.0
        )

        val verifyResult = SessionVerifier.verifyUploadedSession(sessionDir, account = athlete)
        verifyResult.outcome.badge shouldBe Badge.FLAGGED

        val heightCheck = verifyResult.outcome.checks.find { it.reason == "HEIGHT_MISMATCH" }
        heightCheck shouldNotBe null
        heightCheck!!.pass shouldBe false
    }

    @Test
    fun `T-BJ-S5 Unknown analyzerVersion flags ANALYZER_VERSION_UNKNOWN`(@TempDir tempDir: File) {
        val (athPub, athKeyPair) = generateEcKeyPair()

        val athlete = AccountRepo.createAthlete(
            AthleteProfileInput(
                name = "Ravi Verma",
                dob = "2008-11-15",
                sex = "M",
                heightCm = 170.0,
                guardianConsent = true,
                devicePublicKey = athPub
            ),
            photoBytes = null,
            photosDir = File(tempDir, "photos")
        )

        val sessionDir = File(tempDir, "ses_bj_version_unknown").apply { mkdirs() }
        createSyntheticSbjSession(
            sessionDir = sessionDir,
            athleteId = athlete.id,
            devicePubKey = athPub,
            deviceKeyPair = athKeyPair,
            heightCm = 170.0,
            analyzerVersion = "bj-99.9.9" // Unknown version
        )

        val verifyResult = SessionVerifier.verifyUploadedSession(sessionDir, account = athlete)
        verifyResult.outcome.badge shouldBe Badge.FLAGGED

        val versionCheck = verifyResult.outcome.checks.find { it.reason == "ANALYZER_VERSION_UNKNOWN" }
        versionCheck shouldNotBe null
        versionCheck!!.pass shouldBe false
    }

    @Test
    fun `Rescore mismatch when manifest claims wrong score flags SERVER_RESCORE_MISMATCH`(@TempDir tempDir: File) {
        val (athPub, athKeyPair) = generateEcKeyPair()

        val athlete = AccountRepo.createAthlete(
            AthleteProfileInput(
                name = "Vikas Yadav",
                dob = "2008-02-02",
                sex = "M",
                heightCm = 165.0,
                guardianConsent = true,
                devicePublicKey = athPub
            ),
            photoBytes = null,
            photosDir = File(tempDir, "photos")
        )

        val sessionDir = File(tempDir, "ses_bj_rescore_mismatch").apply { mkdirs() }
        // Athlete jumped 200.0 cm, but manifest claims 250.0 cm
        createSyntheticSbjSession(
            sessionDir = sessionDir,
            athleteId = athlete.id,
            devicePubKey = athPub,
            deviceKeyPair = athKeyPair,
            heightCm = 165.0,
            distanceCm = 200.0,
            declaredBestCm = 250.0
        )

        val verifyResult = SessionVerifier.verifyUploadedSession(sessionDir, account = athlete)
        verifyResult.outcome.badge shouldBe Badge.FLAGGED

        val mismatchCheck = verifyResult.outcome.checks.find { it.reason == "SERVER_RESCORE_MISMATCH" }
        mismatchCheck shouldNotBe null
        mismatchCheck!!.pass shouldBe false
    }

    @Test
    fun `T-BJ-S6 CSV import then validation Stats match values computed by hand on a 5-row fixture`() {
        val sessionFixtures = listOf(
            Pair("ses_val_1", 200.5),
            Pair("ses_val_2", 179.0),
            Pair("ses_val_3", 211.5),
            Pair("ses_val_4", 194.5),
            Pair("ses_val_5", 204.0)
        )
        for ((sid, aiDist) in sessionFixtures) {
            ServerDb.saveOrUpdate(
                ServerSessionRecord(
                    id = sid,
                    athleteId = "ATH-VAL",
                    athleteInitials = "AV",
                    test = "BROAD_JUMP",
                    best = aiDist,
                    unit = "cm",
                    status = "APPROVED",
                    integrity = "VERIFIED",
                    submittedAt = System.currentTimeMillis()
                )
            )
        }

        val csvFixture = """
            sessionId,attempt,tapeCm,truthFoul,dataSet
            ses_val_1,1,200.0,NONE,REPORT
            ses_val_2,1,180.0,NONE,REPORT
            ses_val_3,1,210.0,NONE,REPORT
            ses_val_4,1,195.0,NONE,REPORT
            ses_val_5,1,205.0,NONE,REPORT
        """.trimIndent()

        val importedCount = ValidationRepo.bulkImportCsv(csvFixture)
        importedCount shouldBe 5

        val comparisons = ValidationService.getComparisons()
        comparisons.size shouldBe 5

        val stats = ValidationService.getAccuracyStats()
        val rep = stats.reportStats

        rep.n shouldBe 5
        rep.meanBiasCm shouldBe -0.1
        rep.maeCm shouldBe 0.9
        rep.sdCm shouldBe (1.08 plusOrMinus 0.02)
        rep.loaLowerCm shouldBe (-2.22 plusOrMinus 0.05)
        rep.loaUpperCm shouldBe (2.02 plusOrMinus 0.05)
        rep.pctWithin2Cm shouldBe 100.0
        rep.pctWithin3Cm shouldBe 100.0
        rep.maxErrorCm shouldBe 1.5
        rep.worstAttempt?.sessionId shouldBe "ses_val_3"

        val html = createHTML().html {
            renderValidationPage(stats, comparisons, importedCount = 5)
        }
        html shouldContain "SPORTS TALENT AI ACCURACY VALIDATION"
        html shouldContain "SAI BENCHMARK CONCORDANCE"
        html shouldContain "100.0%"
        html shouldContain "of 5 attempts within ±2 cm"
        html shouldContain "MAE"
        html shouldContain "0.9 cm"
        html shouldContain "-0.1 cm"
        html shouldContain "<svg"
        html shouldContain "SAI ±2 cm Target"
        html shouldContain "y = x (Perfect Concordance)"
    }
}

