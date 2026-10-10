package app.pramaan.review

import app.pramaan.auth.AuthStore
import app.pramaan.core.crypto.Sha256
import app.pramaan.core.manifest.ReviewPayload
import app.pramaan.seal.DeviceKey
import kotlinx.coroutines.flow.first

class ReviewSigner(
    private val deviceKey: DeviceKey,
    private val authStore: AuthStore
) {
    suspend fun createSignedReview(
        sessionId: String,
        manifestSha256: String,
        decision: String,
        method: String,
        scannedQr: String? = null,
        reviewedAt: Long = System.currentTimeMillis()
    ): Pair<ReviewPayload, String> {
        val coachId = authStore.accountIdFlow.first() ?: "COA-01"
        val qrSha256 = if (!scannedQr.isNullOrBlank()) {
            Sha256.digest(scannedQr.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        } else ""

        val payload = ReviewPayload(
            coachId = coachId,
            decision = decision,
            manifestSha256 = manifestSha256,
            method = method,
            qrSha256 = qrSha256,
            reviewedAt = reviewedAt,
            sessionId = sessionId
        )

        val signature = deviceKey.sign(payload.canonicalBytes())
        return payload to signature
    }
}
