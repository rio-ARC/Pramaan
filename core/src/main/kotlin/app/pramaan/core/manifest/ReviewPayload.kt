package app.pramaan.core.manifest

import kotlinx.serialization.Serializable

/**
 * Canonical JSON payload for coach review signature (Spec §6.2).
 * Both mobile and server construct this object and call canonicalBytes() to ensure
 * byte-for-byte identical UTF-8 encoding per RFC 8785.
 */
@Serializable
data class ReviewPayload(
    val coachId: String,
    val decision: String, // "APPROVED" or "REJECTED"
    val manifestSha256: String,
    val method: String, // "IN_PERSON_QR" or "REMOTE"
    val qrSha256: String = "", // SHA-256 hex of scanned S2 QR string, or empty string
    val reviewedAt: Long,
    val sessionId: String,
    val type: String = "PRAMAAN_COACH_REVIEW",
    val v: Int = 1
) {
    fun canonicalBytes(): ByteArray = CanonicalJson.encodeToByteArray(serializer(), this)
}
