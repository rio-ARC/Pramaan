package app.pramaan.core.manifest

import app.pramaan.core.crypto.EcdsaVerifier

/**
 * Protocol for Athlete Session QR (Type S2) per Spec §6.1.
 * Format: PRAMAAN:S2:<sessionId>:<manifestSha256Hex>:<nonce>:<issuedAtMs>:<athleteSigB64url>
 */
object SessionQr {
    const val PREFIX = "PRAMAAN:S2"

    data class DecodedS2Qr(
        val sessionId: String,
        val manifestSha256Hex: String,
        val nonce: String,
        val issuedAtMs: Long,
        val athleteSigB64url: String
    ) {
        val signedMessage: String
            get() = buildSignedMessageString(sessionId, manifestSha256Hex, nonce, issuedAtMs)

        val signedMessageBytes: ByteArray
            get() = signedMessage.toByteArray(Charsets.UTF_8)

        fun verifySignature(athletePublicKeyBase64Url: String): Boolean {
            return EcdsaVerifier.verify(
                publicKeyBase64Url = athletePublicKeyBase64Url,
                message = signedMessageBytes,
                signatureDerBase64Url = athleteSigB64url
            )
        }
    }

    fun buildSignedMessageString(
        sessionId: String,
        manifestSha256Hex: String,
        nonce: String,
        issuedAtMs: Long
    ): String {
        return "S2|$sessionId|$manifestSha256Hex|$nonce|$issuedAtMs"
    }

    fun buildSignedMessageBytes(
        sessionId: String,
        manifestSha256Hex: String,
        nonce: String,
        issuedAtMs: Long
    ): ByteArray {
        return buildSignedMessageString(sessionId, manifestSha256Hex, nonce, issuedAtMs).toByteArray(Charsets.UTF_8)
    }

    fun encode(
        sessionId: String,
        manifestSha256Hex: String,
        nonce: String,
        issuedAtMs: Long,
        athleteSigB64url: String
    ): String {
        return "$PREFIX:$sessionId:$manifestSha256Hex:$nonce:$issuedAtMs:$athleteSigB64url"
    }

    fun decode(qrString: String): DecodedS2Qr? {
        val trimmed = qrString.trim()
        if (!trimmed.startsWith("$PREFIX:")) return null
        val parts = trimmed.split(":")
        // Expect ["PRAMAAN", "S2", sessionId, manifestSha256Hex, nonce, issuedAtMs, athleteSigB64url] -> 7 parts
        if (parts.size != 7) return null

        val sessionId = parts[2]
        val manifestSha256Hex = parts[3]
        val nonce = parts[4]
        val issuedAtMs = parts[5].toLongOrNull() ?: return null
        val athleteSigB64url = parts[6]

        if (sessionId.isBlank() || manifestSha256Hex.isBlank() || nonce.isBlank() || athleteSigB64url.isBlank()) {
            return null
        }

        return DecodedS2Qr(
            sessionId = sessionId,
            manifestSha256Hex = manifestSha256Hex,
            nonce = nonce,
            issuedAtMs = issuedAtMs,
            athleteSigB64url = athleteSigB64url
        )
    }
}
