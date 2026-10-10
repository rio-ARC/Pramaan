package app.pramaan.core.manifest

import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.crypto.Sha256
import kotlinx.serialization.Serializable

/**
 * QR type S: Coach phone -> Witness phone.
 * Encodes summary of the session signed by the coach's device key.
 * Includes coach public key (cpk) so witness can verify offline without trust infrastructure.
 */
@Serializable
data class SessionQrPayload(
    val sid: String,
    val d: String,
    val test: String,
    val best: Double,
    val unit: String,
    val ini: String,
    val cpk: String = ""
)

/**
 * QR type W: Witness phone -> Coach phone.
 * Encodes witness attestation signed by the witness's device key.
 */
@Serializable
data class WitnessQrPayload(
    val sid: String,
    val d: String,
    val wpk: String,
    val t: Long
)

/**
 * Persisted witness attestation saved as witness.json in the session folder.
 */
@Serializable
data class WitnessAttestation(
    val sid: String,
    val d: String,
    val wpk: String,
    val t: Long,
    val sig: String
) {
    /**
     * First 8 hex characters of SHA-256 hash of witness public key (wit claim in cert).
     */
    val witnessShortId: String
        get() {
            val digest = Sha256.digest(Base64Url.decode(wpk))
            return digest.take(4).joinToString("") { "%02x".format(it) }
        }
}

/**
 * Helper for encoding/decoding QR payload strings formatted as:
 * PRM1<type>.<base64url(canonical json)>.<base64url(signature)>
 */
object WitnessQrCodec {
    const val PREFIX = "PRM1"

    data class DecodedQr(
        val type: Char,
        val payloadJsonBase64Url: String,
        val signatureBase64Url: String
    )

    fun encode(type: Char, payloadBase64Url: String, signatureBase64Url: String): String {
        return "$PREFIX$type.$payloadBase64Url.$signatureBase64Url"
    }

    fun decode(qrString: String): DecodedQr? {
        if (!qrString.startsWith(PREFIX) || qrString.length < 6) return null
        val type = qrString[PREFIX.length]
        val remainder = qrString.substring(PREFIX.length + 1)
        val parts = remainder.split('.')
        if (parts.size != 2) return null
        return DecodedQr(
            type = type,
            payloadJsonBase64Url = parts[0],
            signatureBase64Url = parts[1]
        )
    }
}
