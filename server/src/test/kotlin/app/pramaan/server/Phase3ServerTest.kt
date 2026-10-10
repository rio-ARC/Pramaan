package app.pramaan.server

import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.crypto.Sha256
import app.pramaan.core.manifest.ReviewPayload
import app.pramaan.core.manifest.SessionQr
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class Phase3ServerTest {

    @BeforeEach
    fun setupDb(@TempDir tempDir: File) {
        val testDbFile = File(tempDir, "test_pramaan.db")
        ServerDb.init(testDbFile)
    }

    private fun generateEcKeyPair(): Pair<String, java.security.KeyPair> {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val pair = kpg.generateKeyPair()
        val pubKeyB64 = Base64Url.encode(pair.public.encoded)
        return pubKeyB64 to pair
    }

    private fun signMessage(pair: java.security.KeyPair, message: ByteArray): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(pair.private)
        sig.update(message)
        return Base64Url.encode(sig.sign())
    }

    @Test
    fun `test coach in-person review approval with S2 QR and certificate issuance`(@TempDir tempDir: File) {
        val (athletePubB64, athleteKeyPair) = generateEcKeyPair()
        val (coachPubB64, coachKeyPair) = generateEcKeyPair()

        // 1. Create Athlete & Coach
        val athlete = AccountRepo.createAthlete(
            AthleteProfileInput(
                name = "Manpreet Kaur",
                dob = "2010-03-14",
                sex = "F",
                heightCm = 152.0,
                guardianConsent = true,
                devicePublicKey = athletePubB64
            ),
            photoBytes = null,
            photosDir = File(tempDir, "photos")
        )

        val seedFile = File(tempDir, "coaches.json")
        seedFile.writeText("""[{"id": "COA-01", "name": "Rakesh Kumar", "district": "Ranchi", "pin": "1234"}]""")
        AccountRepo.seedCoachesIfEmpty(seedFile)
        val coach = AccountRepo.authenticateCoach("COA-01", "1234", coachPubB64)!!

        // 2. Insert session awaiting review with VERIFIED integrity
        val sessionId = "ses_1730000000_jump"
        val manifestSha = "3a8fb7e100000000000000000000000000000000000000000000000000000000"
        val now = System.currentTimeMillis()

        ServerDb.saveOrUpdate(
            ServerSessionRecord(
                id = sessionId,
                athleteId = athlete.id,
                athleteInitials = "MK",
                test = "VERTICAL_JUMP",
                best = 48.2,
                unit = "cm",
                status = "AWAITING_REVIEW",
                integrity = "VERIFIED",
                manifestSha256 = manifestSha,
                submittedAt = now
            )
        )

        // 3. Athlete generates S2 QR signed with athlete key
        val nonce = "randomNonce12"
        val s2MsgBytes = SessionQr.buildSignedMessageBytes(sessionId, manifestSha, nonce, now)
        val athleteSig = signMessage(athleteKeyPair, s2MsgBytes)
        val s2Qr = SessionQr.encode(sessionId, manifestSha, nonce, now, athleteSig)

        // 4. Coach signs canonical ReviewPayload
        val qrSha256 = Sha256.digest(s2Qr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val reviewPayload = ReviewPayload(
            coachId = coach.id,
            decision = "APPROVED",
            manifestSha256 = manifestSha,
            method = "IN_PERSON_QR",
            qrSha256 = qrSha256,
            reviewedAt = now + 1000,
            sessionId = sessionId
        )
        val coachSig = signMessage(coachKeyPair, reviewPayload.canonicalBytes())

        // 5. Submit review
        val certIssuer = ServerCertificateIssuer(File(tempDir, "server_key.json"))
        val reviewService = ReviewService(AccountRepo, EventLog, certIssuer)

        val result = reviewService.reviewSession(
            sessionId = sessionId,
            coachId = coach.id,
            decision = "APPROVED",
            requestedMethod = "IN_PERSON_QR",
            reason = null,
            scannedQr = s2Qr,
            reviewedAt = now + 1000,
            coachSignature = coachSig
        )

        result.shouldBeInstanceOf<ReviewResult.Success>()
        val success = result as ReviewResult.Success
        success.status shouldBe "APPROVED"
        success.integrity shouldBe "VERIFIED"
        success.certificateJws shouldNotBe null

        // Verify certificate claims
        val claims = certIssuer.verifyCertificate(success.certificateJws!!)
        claims shouldNotBe null
        claims!!.issuer shouldBe "PRAMAAN_CENTRAL_AUTHORITY"
        claims.getClaim("sid") shouldBe sessionId
        claims.getClaim("score") shouldBe 48.2

        // Verify DB update
        val updatedSession = ServerDb.getById(sessionId)
        updatedSession shouldNotBe null
        updatedSession!!.status shouldBe "APPROVED"
        updatedSession.reviewedBy shouldBe coach.id
        updatedSession.reviewMethod shouldBe "IN_PERSON_QR"
        updatedSession.certificateJws shouldBe success.certificateJws
    }

    @Test
    fun `test non-verified integrity cannot be approved by coach`(@TempDir tempDir: File) {
        val (coachPubB64, coachKeyPair) = generateEcKeyPair()
        val seedFile = File(tempDir, "coaches.json")
        seedFile.writeText("""[{"id": "COA-01", "name": "Rakesh Kumar", "district": "Ranchi", "pin": "1234"}]""")
        AccountRepo.seedCoachesIfEmpty(seedFile)
        val coach = AccountRepo.authenticateCoach("COA-01", "1234", coachPubB64)!!

        val sessionId = "ses_tampered_jump"
        ServerDb.saveOrUpdate(
            ServerSessionRecord(
                id = sessionId,
                athleteId = "ATH-01",
                athleteInitials = "MK",
                test = "VERTICAL_JUMP",
                best = 48.2,
                unit = "cm",
                status = "AWAITING_REVIEW",
                integrity = "FLAGGED", // AI gravity check failed
                manifestSha256 = "sha256fake",
                submittedAt = System.currentTimeMillis()
            )
        )

        val certIssuer = ServerCertificateIssuer(File(tempDir, "server_key.json"))
        val reviewService = ReviewService(AccountRepo, EventLog, certIssuer)

        val result = reviewService.reviewSession(
            sessionId = sessionId,
            coachId = coach.id,
            decision = "APPROVED",
            requestedMethod = "REMOTE",
            reason = null,
            scannedQr = null,
            reviewedAt = System.currentTimeMillis(),
            coachSignature = "dummySig"
        )

        result.shouldBeInstanceOf<ReviewResult.Error>()
        val error = result as ReviewResult.Error
        error.statusCode shouldBe 422
        error.error shouldBe "NOT_APPROVABLE"
    }

    @Test
    fun `test coach rejection with reason`(@TempDir tempDir: File) {
        val (coachPubB64, coachKeyPair) = generateEcKeyPair()
        val seedFile = File(tempDir, "coaches.json")
        seedFile.writeText("""[{"id": "COA-01", "name": "Rakesh Kumar", "district": "Ranchi", "pin": "1234"}]""")
        AccountRepo.seedCoachesIfEmpty(seedFile)
        val coach = AccountRepo.authenticateCoach("COA-01", "1234", coachPubB64)!!

        val sessionId = "ses_reject_jump"
        val now = System.currentTimeMillis()
        ServerDb.saveOrUpdate(
            ServerSessionRecord(
                id = sessionId,
                athleteId = "ATH-01",
                athleteInitials = "MK",
                test = "VERTICAL_JUMP",
                best = 48.2,
                unit = "cm",
                status = "AWAITING_REVIEW",
                integrity = "VERIFIED",
                manifestSha256 = "sha256fake",
                submittedAt = now
            )
        )

        val reviewPayload = ReviewPayload(
            coachId = coach.id,
            decision = "REJECTED",
            manifestSha256 = "sha256fake",
            method = "REMOTE",
            qrSha256 = "",
            reviewedAt = now + 500,
            sessionId = sessionId
        )
        val coachSig = signMessage(coachKeyPair, reviewPayload.canonicalBytes())

        val certIssuer = ServerCertificateIssuer(File(tempDir, "server_key.json"))
        val reviewService = ReviewService(AccountRepo, EventLog, certIssuer)

        val result = reviewService.reviewSession(
            sessionId = sessionId,
            coachId = coach.id,
            decision = "REJECTED",
            requestedMethod = "REMOTE",
            reason = "Faulty form on landing, retest required",
            scannedQr = null,
            reviewedAt = now + 500,
            coachSignature = coachSig
        )

        result.shouldBeInstanceOf<ReviewResult.Success>()
        val success = result as ReviewResult.Success
        success.status shouldBe "REJECTED"
        success.certificateJws shouldBe null

        val updated = ServerDb.getById(sessionId)
        updated!!.status shouldBe "REJECTED"
        updated.reviewReason shouldBe "Faulty form on landing, retest required"
    }
}
