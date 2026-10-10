package app.pramaan.core

import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.ReviewPayload
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReviewPayloadTest {

    @Test
    fun `test ReviewPayload canonical JSON bytes`() {
        val payload = ReviewPayload(
            coachId = "COA-01",
            decision = "APPROVED",
            manifestSha256 = "3a8fb7e100000000000000000000000000000000000000000000000000000000",
            method = "IN_PERSON_QR",
            qrSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            reviewedAt = 1730000130000L,
            sessionId = "ses_1730000000_abcd"
        )

        val bytes = payload.canonicalBytes()
        val jsonString = String(bytes, Charsets.UTF_8)

        // RFC 8785 requires deterministic key ordering (alphabetical)
        jsonString.startsWith("{\"coachId\":\"COA-01\"") shouldBe true
        jsonString.contains("\"decision\":\"APPROVED\"") shouldBe true
        jsonString.contains("\"type\":\"PRAMAAN_COACH_REVIEW\"") shouldBe true
        jsonString.contains("\"v\":1") shouldBe true

        // Roundtrip decoding
        val decoded = CanonicalJson.decodeFromByteArray(ReviewPayload.serializer(), bytes)
        decoded.coachId shouldBe payload.coachId
        decoded.decision shouldBe payload.decision
        decoded.sessionId shouldBe payload.sessionId
        decoded.method shouldBe payload.method
        decoded.manifestSha256 shouldBe payload.manifestSha256
        decoded.qrSha256 shouldBe payload.qrSha256
    }
}
