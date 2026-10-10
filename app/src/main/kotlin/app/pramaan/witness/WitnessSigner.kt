package app.pramaan.witness

import app.pramaan.core.crypto.Base64Url
import app.pramaan.core.crypto.EcdsaVerifier
import app.pramaan.core.manifest.CanonicalJson
import app.pramaan.core.manifest.SessionQrPayload
import app.pramaan.core.manifest.WitnessAttestation
import app.pramaan.core.manifest.WitnessQrCodec
import app.pramaan.core.manifest.WitnessQrPayload
import app.pramaan.seal.DeviceKey
import kotlinx.serialization.json.Json

class WitnessSigner(private val deviceKey: DeviceKey) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decodes and verifies QR S on the witness phone.
     */
    fun decodeAndVerifySessionQr(qrString: String): SessionQrPayload? {
        val decoded = WitnessQrCodec.decode(qrString) ?: return null
        if (decoded.type != 'S') return null

        val payloadBytes = Base64Url.decode(decoded.payloadJsonBase64Url)
        val payload = try {
            json.decodeFromString<SessionQrPayload>(payloadBytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            return null
        }

        // Verify coach signature if cpk is present
        if (payload.cpk.isNotEmpty()) {
            val valid = EcdsaVerifier.verify(
                publicKeyBase64Url = payload.cpk,
                message = payloadBytes,
                signatureDerBase64Url = decoded.signatureBase64Url
            )
            if (!valid) return null
        }

        return payload
    }

    /**
     * Creates QR W on witness phone after witness confirms.
     */
    fun createWitnessAttestationQr(sessionPayload: SessionQrPayload): Pair<String, WitnessAttestation> {
        val now = System.currentTimeMillis()
        val myPublicKey = deviceKey.getPublicKeyBase64Url()

        val payload = WitnessQrPayload(
            sid = sessionPayload.sid,
            d = sessionPayload.d,
            wpk = myPublicKey,
            t = now
        )

        val canonicalBytes = CanonicalJson.encodeToByteArray(payload)
        val sigDerB64Url = deviceKey.sign(canonicalBytes)
        val payloadB64Url = Base64Url.encode(canonicalBytes)

        val qrString = WitnessQrCodec.encode('W', payloadB64Url, sigDerB64Url)
        val attestation = WitnessAttestation(
            sid = payload.sid,
            d = payload.d,
            wpk = payload.wpk,
            t = payload.t,
            sig = sigDerB64Url
        )

        return qrString to attestation
    }

    /**
     * Creates QR S on coach phone to present to witness.
     */
    fun createSessionQr(
        sessionId: String,
        manifestHash: String,
        testName: String,
        bestScore: Double,
        unit: String,
        initials: String
    ): String {
        val payload = SessionQrPayload(
            sid = sessionId,
            d = manifestHash,
            test = testName,
            best = bestScore,
            unit = unit,
            ini = initials,
            cpk = deviceKey.getPublicKeyBase64Url()
        )

        val canonicalBytes = CanonicalJson.encodeToByteArray(payload)
        val sigDerB64Url = deviceKey.sign(canonicalBytes)
        val payloadB64Url = Base64Url.encode(canonicalBytes)

        return WitnessQrCodec.encode('S', payloadB64Url, sigDerB64Url)
    }

    /**
     * Decodes QR W on coach phone when scanning witness return code.
     */
    fun decodeWitnessReturnQr(qrString: String): WitnessAttestation? {
        val decoded = WitnessQrCodec.decode(qrString) ?: return null
        if (decoded.type != 'W') return null

        val payloadBytes = Base64Url.decode(decoded.payloadJsonBase64Url)
        val payload = try {
            json.decodeFromString<WitnessQrPayload>(payloadBytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            return null
        }

        return WitnessAttestation(
            sid = payload.sid,
            d = payload.d,
            wpk = payload.wpk,
            t = payload.t,
            sig = decoded.signatureBase64Url
        )
    }
}
