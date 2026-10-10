package app.pramaan.server

import app.pramaan.core.model.CheckResult
import app.pramaan.server.DashboardPages.renderSubmissionDetailPage
import app.pramaan.server.DashboardPages.renderSubmissionsPage
import app.pramaan.server.DashboardPages.renderVerificationPage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class Phase4ServerTest {

    @BeforeEach
    fun setupDb(@TempDir tempDir: File) {
        val testDbFile = File(tempDir, "test_pramaan.db")
        ServerDb.init(testDbFile)
    }

    @Test
    fun `test public verification page renders full 4-step chain of custody for valid credential`(@TempDir tempDir: File) {
        val keyFile = File(tempDir, "server_key.json")
        val issuer = ServerCertificateIssuer(keyFile)

        val jws = issuer.issueCoachVerifiedCertificate(
            sessionId = "ses_2026_jump_001",
            manifestSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            athleteId = "ATH-849201",
            athleteName = "Manpreet Kaur",
            athleteAge = 16,
            athleteSex = "F",
            test = "VERTICAL_JUMP",
            score = 48.2,
            unit = "cm",
            integrity = "VERIFIED",
            coachId = "COA-01",
            coachName = "Rakesh Kumar",
            reviewMethod = "IN_PERSON_QR",
            reviewedAt = 1779000000000L,
            recordedAt = 1778999900000L
        )

        val claims = issuer.verifyCertificate(jws)
        claims shouldNotBe null

        val html = createHTML().html {
            renderVerificationPage(
                claims = claims,
                isValid = true,
                rawJws = jws,
                lanIp = "127.0.0.1"
            )
        }

        // 1. Valid credential badge
        html shouldContain "PRAMAAN VERIFIED CREDENTIAL"
        html shouldContain "Official Sports Talent Certificate"

        // 2. Candidate masked name and performance
        html shouldContain "Manpreet K."
        html shouldContain "ATH-849201"
        html shouldContain "48.2 cm"
        html shouldContain "VERTICAL JUMP"

        // 3. Four-step chain of custody per Spec 8.4
        html shouldContain "Recorded on Registered Phone"
        html shouldContain "AI &amp; Physics Hardware Checks Passed"
        html shouldContain "Approved by Coach Rakesh Kumar"
        html shouldContain "Certificate Issued by PRAMAAN Authority"

        // 4. Verification QR code SVG and technical audit raw JWS
        html shouldContain "<svg"
        html shouldContain "Technical Audit: Raw JWS Compact Serialization & Claims"
        html shouldContain jws
    }

    @Test
    fun `test public verification page renders fraud warning for tampered credential`(@TempDir tempDir: File) {
        val keyFile = File(tempDir, "server_key.json")
        val issuer = ServerCertificateIssuer(keyFile)

        val fakeJws = "eyJhbGciOiJFUzI1NiIsImtpZCI6InByYW1hYW4tYXV0aC0xIn0.eyJpc3MiOiJQUkFNQUFOIn0.invalid_signature_bits"
        val claims = issuer.verifyCertificate(fakeJws)
        claims shouldBe null

        val html = createHTML().html {
            renderVerificationPage(
                claims = null,
                isValid = false,
                rawJws = fakeJws,
                lanIp = "127.0.0.1"
            )
        }

        html shouldContain "FRAUD DETECTED • INVALID OR REVOKED CREDENTIAL"
        html shouldContain "Cryptographic Verification Failed"
        html shouldContain "Invalid Token Payload Inspected"
        html shouldContain fakeJws
    }

    @Test
    fun `test submissions list page supports filtering tabs and auto-refresh`() {
        val sessions = listOf(
            ServerSessionRecord(
                id = "ses_awaiting",
                athleteId = "ATH-01",
                athleteInitials = "MK",
                test = "VERTICAL_JUMP",
                best = 40.0,
                unit = "cm",
                status = "AWAITING_REVIEW",
                integrity = "VERIFIED",
                submittedAt = System.currentTimeMillis()
            ),
            ServerSessionRecord(
                id = "ses_approved",
                athleteId = "ATH-02",
                athleteInitials = "RS",
                test = "VERTICAL_JUMP",
                best = 45.0,
                unit = "cm",
                status = "APPROVED",
                integrity = "VERIFIED",
                reviewedBy = "COA-01",
                submittedAt = System.currentTimeMillis()
            ),
            ServerSessionRecord(
                id = "ses_flagged",
                athleteId = "ATH-03",
                athleteInitials = "AK",
                test = "VERTICAL_JUMP",
                best = 30.0,
                unit = "cm",
                status = "AWAITING_REVIEW",
                integrity = "FLAGGED",
                submittedAt = System.currentTimeMillis()
            ),
            ServerSessionRecord(
                id = "ses_tampered",
                athleteId = "ATH-04",
                athleteInitials = "PK",
                test = "VERTICAL_JUMP",
                best = 50.0,
                unit = "cm",
                status = "AUTO_REJECTED",
                integrity = "TAMPERED",
                submittedAt = System.currentTimeMillis()
            )
        )

        val counts = mapOf(
            "ALL" to sessions.size,
            "AWAITING_REVIEW" to 2,
            "APPROVED" to 1,
            "FLAGGED" to 1,
            "AUTO_REJECTED" to 1
        )

        val htmlAll = createHTML().html {
            renderSubmissionsPage(sessions, activeFilter = "ALL", counts = counts)
        }
        htmlAll shouldContain "FIELD SUBMISSIONS AUDIT LOG"
        htmlAll shouldContain "content=\"5\""
        htmlAll shouldContain "ALL (4)"
        htmlAll shouldContain "AWAITING REVIEW (2)"
        htmlAll shouldContain "APPROVED (1)"
        htmlAll shouldContain "FLAGGED (1)"
        htmlAll shouldContain "AUTO-REJECTED (1)"
        htmlAll shouldContain "ses_awaiting"
        htmlAll shouldContain "ses_approved"

        val htmlApproved = createHTML().html {
            renderSubmissionsPage(sessions.filter { it.status == "APPROVED" }, activeFilter = "APPROVED", counts = counts)
        }
        htmlApproved shouldContain "ses_approved"
    }

    @Test
    fun `test submission detail page displays athlete demographics, checks, and event timeline`() {
        val json = Json { ignoreUnknownKeys = true }
        val checks = listOf(
            CheckResult("C1", true, "Match", "Exact SHA-256 match"),
            CheckResult("C2", true, "g = 9.78 m/s²", "9.81 m/s² ±8%"),
            CheckResult("C3", true, "0.3 cm diff", "≤ 3.0 cm"),
            CheckResult("C4", true, "RMS 0.08 m/s²", "≤ 0.50 m/s²")
        )

        val session = ServerSessionRecord(
            id = "ses_detailed_01",
            athleteId = "ATH-7788",
            athleteInitials = "MK",
            test = "VERTICAL_JUMP",
            best = 48.2,
            unit = "cm",
            status = "APPROVED",
            integrity = "VERIFIED",
            integrityJson = json.encodeToString(checks),
            reviewedBy = "COA-01",
            certificateJws = "sample.jws.cert",
            submittedAt = 1779000000000L
        )

        val athlete = AccountRecord(
            id = "ATH-7788",
            role = "ATHLETE",
            name = "Manpreet Kaur",
            dob = "2010-03-14",
            sex = "F",
            heightCm = 152.0,
            weightKg = 41.5,
            district = "Ranchi",
            guardianConsent = true,
            devicePubkey = "test_key_bound_hex_or_b64"
        )

        val events = listOf(
            SessionEventRecord(
                id = 1,
                sessionId = "ses_detailed_01",
                type = "SUBMITTED",
                actorId = "ATH-7788",
                detail = "Submitted by athlete",
                at = 1779000000000L
            ),
            SessionEventRecord(
                id = 2,
                sessionId = "ses_detailed_01",
                type = "SERVER_VERIFIED",
                actorId = "SERVER",
                detail = "Integrity badge: VERIFIED",
                at = 1779000001000L
            ),
            SessionEventRecord(
                id = 3,
                sessionId = "ses_detailed_01",
                type = "COACH_REVIEWED",
                actorId = "COA-01",
                detail = "In-person approved by coach",
                at = 1779000005000L
            )
        )

        val html = createHTML().html {
            renderSubmissionDetailPage(
                session = session,
                athlete = athlete,
                reviewer = null,
                events = events
            )
        }

        html shouldContain "SUBMISSION AUDIT: #SES_DETA"
        html shouldContain "Manpreet Kaur (ATH-7788)"
        html shouldContain "DOB: 2010-03-14"
        html shouldContain "District: Ranchi"
        html shouldContain "C1"
        html shouldContain "Seal"
        html shouldContain "C2"
        html shouldContain "Gravity"
        html shouldContain "Chain of Custody Timeline"
        html shouldContain "SUBMITTED"
        html shouldContain "SERVER_VERIFIED"
        html shouldContain "COACH_REVIEWED"
        html shouldContain "View Verifiable Certificate"
    }
}
